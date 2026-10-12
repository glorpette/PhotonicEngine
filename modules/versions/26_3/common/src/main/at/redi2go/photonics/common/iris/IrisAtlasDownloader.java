package at.redi2go.photonics.common.iris;

import at.redi2go.photonics.common.ResourceReloaderListener;
import at.redi2go.photonics.engine.Photonics;
import at.redi2go.photonics.engine.rendering.world.IgnoredInterruptedException;
import at.redi2go.photonics.engine.rendering.world.bakery.texture.AtlasDownloader;
import at.redi2go.photonics.engine.rendering.world.bakery.texture.AtlasTexture;
import at.redi2go.photonics.engine.rendering.world.bakery.texture.CpuTexture;
import at.redi2go.photonics.engine.rendering.world.block.TextureData;
import at.redi2go.photonics.game.minecraft.Id;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import net.irisshaders.iris.pbr.texture.PBRTextureHolder;
import net.irisshaders.iris.pbr.texture.PBRTextureManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.nio.IntBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Reads only the small texture regions needed while baking blocks. */
public class IrisAtlasDownloader implements AtlasDownloader {
    private static final IrisTextureTileCache TILE_CACHE = new IrisTextureTileCache();

    private final TextureManager textureManager = Minecraft.getInstance().getTextureManager();
    private final java.util.concurrent.ConcurrentHashMap<Id, CompletableFuture<AtlasTexture>> cache =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final Runnable clearCacheListener = this::clearCache;
    private volatile boolean closed;

    public IrisAtlasDownloader() {
        ResourceReloaderListener.add(clearCacheListener);
    }

    private void clearCache() {
        if (Photonics.LOGGER.isDebugEnabled())
            Photonics.LOGGER.debug("Clearing Photonics atlas tiles ({})", TILE_CACHE.stats());
        TILE_CACHE.clear();
        cache.values().forEach(future -> future.cancel(true));
        cache.clear();
    }

    private CompletableFuture<AtlasTexture> downloadTexture(Id atlasId) {
        if (closed)
            return CompletableFuture.failedFuture(new IllegalStateException("Atlas downloader is closed"));

        long generation = TILE_CACHE.generation();
        return cache.computeIfAbsent(atlasId, id -> resolveAtlas(id, generation));
    }

    private CompletableFuture<AtlasTexture> resolveAtlas(Id atlasId, long generation) {
        var result = new CompletableFuture<AtlasTexture>();
        Minecraft.getInstance().execute(() -> {
            try {
                if (closed || generation != TILE_CACHE.generation())
                    throw new IllegalStateException("Atlas belongs to a stale resource generation");

                AbstractTexture atlas = textureManager.getTexture((Identifier) (Object) atlasId);
                GpuTexture albedoGpu = atlas.getTexture();
                var glTexture = (com.mojang.renderpearl.backend.opengl.GlTexture) albedoGpu;
                PBRTextureManager.INSTANCE.getOrLoadHolder(glTexture.glId());
                PBRTextureHolder pbr = PBRTextureManager.INSTANCE.getHolder(glTexture.glId());

                CpuTexture albedo = createCpuTexture(albedoGpu, generation, 0);
                CpuTexture normal = createCpuTexture(
                        pbr.normalTexture() == null ? null : pbr.normalTexture().getTexture(),
                        generation,
                        TextureData.DEFAULT_NORMAL
                );
                CpuTexture specular = createCpuTexture(
                        pbr.specularTexture() == null ? null : pbr.specularTexture().getTexture(),
                        generation,
                        TextureData.DEFAULT_SPECULAR
                );

                result.complete(new AtlasTexture(albedo, normal, specular, stableHash(atlasId, generation)));
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    private CpuTexture createCpuTexture(@Nullable GpuTexture texture, long generation, int defaultValue) {
        if (texture == null) return (u, v) -> defaultValue;
        if (texture.getFormat() != com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM) {
            Photonics.LOGGER.warn("Unsupported texture format for Photonics voxelization: {}", texture.getFormat());
            return (u, v) -> defaultValue;
        }

        int width = texture.getWidth(0);
        int height = texture.getHeight(0);
        return new TiledCpuTexture(texture, generation, width, height, defaultValue);
    }

    private static long stableHash(Id atlasId, long generation) {
        long hash = 0xcbf29ce484222325L;
        for (byte value : atlasId.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            hash ^= value & 0xffL;
            hash *= 0x100000001b3L;
        }
        return hash * 31 + generation;
    }

    private CompletableFuture<int[]> readTextureTile(
            GpuTexture texture,
            long generation,
            int x,
            int y,
            int width,
            int height,
            int byteSize
    ) {
        IrisTextureTileCache.ReadbackBudget budget = TILE_CACHE.readbackBudget();
        try {
            budget.acquire(byteSize);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IgnoredInterruptedException();
        }

        var result = new CompletableFuture<int[]>();
        var budgetReleased = new AtomicBoolean();
        Runnable releaseBudget = () -> {
            if (budgetReleased.compareAndSet(false, true)) budget.release(byteSize);
        };

        try {
            Minecraft.getInstance().execute(() -> {
                if (closed || generation != TILE_CACHE.generation()) {
                    result.completeExceptionally(new IllegalStateException("Texture tile belongs to a stale generation"));
                    releaseBudget.run();
                    return;
                }

                GpuBuffer output = null;
                var outputClosed = new AtomicBoolean();
                try {
                    var device = RenderSystem.getDevice();
                    output = device.createBuffer(
                            () -> "Photonics voxelization texture tile",
                            GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ,
                            byteSize
                    );
                    GpuBuffer outputBuffer = output;
                    Runnable closeOutput = () -> {
                        if (outputClosed.compareAndSet(false, true)) {
                            try {
                                outputBuffer.close();
                            } catch (Throwable closeFailure) {
                                Photonics.LOGGER.debug("Could not close Photonics texture readback buffer", closeFailure);
                            }
                        }
                    };

                    FrontendCommandEncoder encoder = (FrontendCommandEncoder) device.createCommandEncoder();
                    encoder.copyTextureToBuffer(texture, outputBuffer, 0, () -> {
                        try {
                            int[] pixels;
                            try (var mapped = outputBuffer.map(true, false)) {
                                IntBuffer buffer = mapped.data().asIntBuffer();
                                pixels = new int[byteSize / Integer.BYTES];
                                buffer.get(pixels);
                            }
                            result.complete(pixels);
                        } catch (Throwable failure) {
                            result.completeExceptionally(failure);
                        } finally {
                            closeOutput.run();
                            releaseBudget.run();
                        }
                    }, 0, x, y, width, height);
                } catch (Throwable failure) {
                    if (output != null) {
                        try {
                            output.close();
                        } catch (Throwable closeFailure) {
                            failure.addSuppressed(closeFailure);
                        }
                    }
                    result.completeExceptionally(failure);
                    releaseBudget.run();
                }
            });
        } catch (Throwable failure) {
            result.completeExceptionally(failure);
            releaseBudget.run();
        }

        return result;
    }

    @Override
    public void preloadTexture(Id textureId) {
        downloadTexture(textureId);
    }

    @Override
    public AtlasTexture get(Id textureId) {
        var future = downloadTexture(textureId);
        if (RenderSystem.isOnRenderThread() && !future.isDone())
            throw new IllegalStateException(
                    "Refusing to wait for Photonics atlas " + textureId + " on the render thread"
            );

        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IgnoredInterruptedException();
        } catch (ExecutionException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public void close() {
        ResourceReloaderListener.remove(clearCacheListener);
        closed = true;
        clearCache();
    }

    private final class TiledCpuTexture implements CpuTexture {
        private final GpuTexture texture;
        private final long generation;
        private final int width;
        private final int height;
        private final int defaultValue;

        private TiledCpuTexture(GpuTexture texture, long generation, int width, int height, int defaultValue) {
            this.texture = texture;
            this.generation = generation;
            this.width = width;
            this.height = height;
            this.defaultValue = defaultValue;
        }

        @Override
        public int sample(float u, float v) {
            if (width <= 0 || height <= 0) return defaultValue;
            int pixelX = org.joml.Math.clamp(org.joml.Math.round(u * (width - 0.5f)), 0, width - 1);
            int pixelY = org.joml.Math.clamp(org.joml.Math.round(v * (height - 0.5f)), 0, height - 1);
            return TILE_CACHE.sample(
                    texture,
                    generation,
                    pixelX,
                    pixelY,
                    width,
                    height,
                    (x, y, tileWidth, tileHeight, byteSize) -> readTextureTile(
                            texture, generation, x, y, tileWidth, tileHeight, byteSize
                    )
            );
        }
    }
}
