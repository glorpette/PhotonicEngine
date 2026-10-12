package at.redi2go.photonics.engine.rendering.world.registry.block.model;

import at.redi2go.photonics.game.minecraft.core.IBlockPos;
import at.redi2go.photonics.game.minecraft.world.level.IBlockAndTintGetter;
import at.redi2go.photonics.game.minecraft.world.level.IBlockState;
import at.redi2go.photonics.engine.collect.ConcurrentLong2ObjectMap;
import at.redi2go.photonics.engine.rendering.world.bakery.BlockBakery;
import at.redi2go.photonics.engine.rendering.world.bakery.BlockMeshState;
import at.redi2go.photonics.engine.rendering.world.bakery.BlockMesher;
import at.redi2go.photonics.engine.rendering.world.bakery.impl.BlockBakeryImpl;
import at.redi2go.photonics.engine.rendering.world.block.BlockModel;
import at.redi2go.photonics.engine.rendering.world.registry.block.BlockRegistry;
import at.redi2go.photonics.engine.rendering.world.registry.block.builder.BlockModelBuilder;
import at.redi2go.photonics.engine.rendering.world.registry.object.ObjectRegistry;
import at.redi2go.photonics.engine.rendering.world.registry.object.WeakValue;
import org.jspecify.annotations.Nullable;
import org.joml.Vector3i;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;

public class BlockModelRegistry extends ObjectRegistry<BlockModelImpl> {
    private static final int MAX_CACHED_MESH_STATES = 8_192;
    private final BlockBakery bakery;
    private final BlockRegistry blockRegistry;

    private final ConcurrentHashMap<BlockMeshState, CompletableFuture<@Nullable @WeakValue BlockModel>> blockMeshCache = new ConcurrentHashMap<>();
    private final ConcurrentLong2ObjectMap<CompletableFuture<BlockModelImpl>> modelCache = new ConcurrentLong2ObjectMap<>(16);

    public BlockModelRegistry(
            ReadWriteLock lock,
            BlockBakery bakery,
            BlockRegistry blockRegistry
    ) {
        super(lock);

        this.bakery = bakery;
        this.blockRegistry = blockRegistry;
    }

    @Override
    protected void removeObject(BlockModelImpl value) {
        modelCache.remove(value.vertexHash());

        var meshes = value.meshes();
        while (!meshes.isEmpty()) {
            var mesh = meshes.remove();
            if (mesh == null) continue;

            blockMeshCache.remove(mesh);
        }
        trimMeshCache();
    }

    public void clearCache() {
        super.clearCache();
        blockMeshCache.clear();
        modelCache.clear();
    }

    private CompletionStage<@WeakValue BlockModelImpl> cacheModel(BlockBakery.MeshResult blockMesh) {
        CompletableFuture<BlockModelImpl> future = new CompletableFuture<>();
        var resultFuture = modelCache.putIfAbsent(blockMesh.vertexHash(), future);

        if (resultFuture != null) {
            blockMesh.close();
            return resultFuture;
        }

        try {
            var builder = new BlockModelBuilder();

            blockMesh.bake(builder);
            var result = builder.build(blockMesh.vertexHash(), blockRegistry, this);
            future.complete(result);
            if (result != null)
                result.enqueueIfUnused();
        } catch (Throwable t) {
            modelCache.remove(blockMesh.vertexHash(), future);
            future.completeExceptionally(t);
        } finally {
            blockMesh.close();
        }

        trimMeshCache();

        return future;
    }


    public <T extends BlockMeshState> CompletionStage<@Nullable BlockModel> getBlockModel(
            BlockMesher<T> blockMesher,
            Vector3i blockChunkOffset,
            IBlockPos pos,
            IBlockState blockState,
            IBlockAndTintGetter blockAndTintGetter
    ) {
        T meshState = blockMesher.extractMeshState(
                blockChunkOffset,
                pos,
                blockState,
                blockAndTintGetter
        );

        CompletableFuture<@Nullable BlockModel> future = new CompletableFuture<>();
        BlockBakery.MeshResult meshResult = null;

        try(var lock = acquireLock()) {
            if (meshState.shouldCache()) {
                var resultFuture = blockMeshCache.putIfAbsent(meshState, future);
                if (resultFuture != null)
                    return acquireModelReference(resultFuture);
            }

            meshResult = bakery.meshBlock(
                    blockMesher,
                    meshState,
                    blockChunkOffset,
                    pos,
                    blockState,
                    blockAndTintGetter
            );

            if (meshResult != null)
                meshResult.prepare();

            meshState.prepareCacheUse();
            if (meshResult == null) {
                future.complete(null);
                trimMeshCache();
                return future;
            }

            var cachedModel = cacheModel(meshResult);
            meshResult = null;
            cachedModel
                    .whenComplete((model, e) -> {
                        try {
                            if (e != null) {
                                if (meshState.shouldCache())
                                    blockMeshCache.remove(meshState, future);
                                future.completeExceptionally(e);
                                return;
                            }

                            if (model != null)
                                if (meshState.shouldCache())
                                    model.meshes().add(meshState);

                            future.complete(model);
                        } catch (Throwable t) {
                            if (meshState.shouldCache())
                                blockMeshCache.remove(meshState, future);
                            future.completeExceptionally(t);
                        }
                    });

            return acquireModelReference(future);
        } catch (Throwable t) {
            if (meshResult != null)
                meshResult.close();
            if (meshState.shouldCache())
                blockMeshCache.remove(meshState, future);
            future.completeExceptionally(t);
            return future;
        }
    }

    private void trimMeshCache() {
        int excess = blockMeshCache.size() - MAX_CACHED_MESH_STATES;
        if (excess <= 0) return;

        for (Map.Entry<BlockMeshState, CompletableFuture<@Nullable @WeakValue BlockModel>> entry : blockMeshCache.entrySet()) {
            if (excess == 0) return;

            var future = entry.getValue();
            if (!future.isDone() || !blockMeshCache.remove(entry.getKey(), future)) continue;

            future.thenAccept(model -> {
                if (model != null)
                    ((BlockModelImpl) model).meshes().remove(entry.getKey());
            });
            excess--;
        }
    }

    private CompletionStage<@Nullable BlockModel> acquireModelReference(CompletionStage<@Nullable @WeakValue BlockModel> originalFuture) {
        // Acquiring references is safe in thenApply as its either completed now, in which case we already hold the lock
        // or its being voxelized by a thread which also holds the lock, which will process all the thenApply dependants on that thread
        return originalFuture.thenApply((e) -> {
            if (e != null)
                ((BlockModelImpl) e).acquireReference();

            return e;
        });
    }
}
