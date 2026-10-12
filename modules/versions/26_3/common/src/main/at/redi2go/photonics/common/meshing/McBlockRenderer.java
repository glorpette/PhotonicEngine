package at.redi2go.photonics.common.meshing;

import at.redi2go.photonics.common.iris.IrisUtil;
import at.redi2go.photonics.common.meshing.impl.BlockBuilderBufferSource;
import at.redi2go.photonics.common.meshing.impl.BlockSetBuilder;
import at.redi2go.photonics.engine.rendering.world.bakery.BlockBuilder;
import at.redi2go.photonics.game.minecraft.Id;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3i;
import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class McBlockRenderer {
    private static final Id BLOCK_ATLAS = (Id) (Object) TextureAtlas.LOCATION_BLOCKS;
    private static final Set<Fluid> WHITELISTED_FLUIDS = Set.of(Fluids.LAVA, Fluids.FLOWING_LAVA);
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final BlockColors BLOCK_COLORS = Minecraft.getInstance().getBlockColors();

    private final RandomSource randomSource = RandomSource.create();
    private final PoseStack poseStack = new PoseStack();
    private final QuadInstance quadInstance = new QuadInstance();

    private final BlockBuilderBufferSource bufferSource = new BlockBuilderBufferSource();
    private final CameraRenderState cameraRenderState = new CameraRenderState();

    private final SimpleMeshState.HashStorage hashStorage = new SimpleMeshState.HashStorage();

    public McBlockRenderer() {
        cameraRenderState.pos = Vec3.ZERO;
    }

    public McMeshState extractMeshState(
            Vector3i blockChunkOffset,
            BlockPos blockPos,
            BlockState blockState,
            BlockAndTintGetter blockAndTintGetter
    ) {
        if (blockState.getBlock() == Blocks.END_GATEWAY)
            return EmptyMeshState.INSTANCE;

        int blockId = IrisUtil.getBlockId(blockState);
        FluidState fluidState = blockState.getFluidState();

        List<BlockStateModelPart> parts;
        if (blockState.getRenderShape() == RenderShape.MODEL) {
            parts = new ArrayList<>();

            randomSource.setSeed(blockState.getSeed(blockPos));
            Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(blockState).collectParts(randomSource, parts);
        } else parts = List.of();

        if (blockState.hasBlockEntity()) return new DynamicMeshState(blockId, fluidState, parts);
        if (WHITELISTED_FLUIDS.contains(fluidState.getType())) return new DynamicMeshState(blockId, fluidState, parts);

        if (parts.isEmpty())
            return EmptyMeshState.INSTANCE;

        var meshState = new SimpleMeshState(blockState.getBlock(), blockId, parts);
        meshState.computeHash(
                hashStorage,
                blockState,
                blockPos,
                blockAndTintGetter
        );

        return meshState;
    }

    public void meshBlock(
            McMeshState meshState,
            Vector3i blockChunkOffset,
            BlockPos pos,
            BlockState blockState,
            BlockAndTintGetter blockAndTintGetter,
            BlockBuilder builder
    ) {
        if (meshState == EmptyMeshState.INSTANCE) return;

        builder.useBlockId(meshState.blockId());

        FluidState fluidState = meshState.fluidState();
        if (!fluidState.isEmpty()) {
            submitFluid(
                    pos,
                    blockAndTintGetter,
                    builder,
                    blockState,
                    fluidState
            );
        }

        if (blockState.hasBlockEntity()) {
            submitBlockEntity(
                    pos,
                    blockState,
                    blockAndTintGetter,
                    builder
            );
        }

        List<BlockStateModelPart> parts = meshState.blockModel();
        if (!parts.isEmpty()) {
            submitBlock(
                    pos,
                    blockState,
                    blockAndTintGetter,
                    builder,
                    parts
            );
        }
    }

    private void submitFluid(
            BlockPos blockPos,
            BlockAndTintGetter blockAndTintGetter,
            BlockBuilder builder,
            BlockState blockState,
            FluidState fluidState
    ) {
        if (!WHITELISTED_FLUIDS.contains(fluidState.getType())) return;

        builder.useAtlas(BLOCK_ATLAS);
        builder.useOffset(
                -(blockPos.getX() & 15),
                -(blockPos.getY() & 15),
                -(blockPos.getZ() & 15)
        );

        FluidRenderer fluidRenderer = new FluidRenderer(Minecraft.getInstance().getModelManager().getFluidStateModelSet());
        fluidRenderer.tesselate(blockAndTintGetter, blockPos, layer -> (VertexConsumer) builder, blockState, fluidState);
    }

    private void submitBlock(
            BlockPos pos,
            BlockState blockState,
            BlockAndTintGetter blockAndTintGetter,
            BlockBuilder builder,
            List<BlockStateModelPart> parts
    ) {
        builder.useAtlas(BLOCK_ATLAS);
        builder.useOffset(0f, 0f, 0f);

        Vec3 offset = blockState.getOffset(pos);
        float x = (float) offset.x;
        float y = (float) offset.y;
        float z = (float) offset.z;

        VertexConsumer consumer = (VertexConsumer) builder;
        for (var part : parts) {
            for (var direction : DIRECTIONS) {
                for (var quad : part.getQuads(direction)) {
                    renderQuad(consumer, x, y, z, quad, blockState, blockAndTintGetter, pos);
                }
            }
            for (var quad : part.getQuads(null)) {
                renderQuad(consumer, x, y, z, quad, blockState, blockAndTintGetter, pos);
            }
        }
    }

    private void renderQuad(
            VertexConsumer consumer,
            float x,
            float y,
            float z,
            BakedQuad quad,
            BlockState blockState,
            BlockAndTintGetter level,
            BlockPos pos
    ) {
        quadInstance.setColor(-1);
        quadInstance.setLightCoords(0x00F000F0);
        quadInstance.setOverlayCoords(OverlayTexture.NO_OVERLAY);

        if (quad.materialInfo().isTinted()) {
            int tintIndex = quad.materialInfo().tintIndex();
            var tintSource = BLOCK_COLORS.getTintSource(blockState, tintIndex);
            int tintColor = tintSource != null ? tintSource.colorInWorld(blockState, level, pos) : -1;
            quadInstance.multiplyColor(tintColor);
        }

        consumer.putBlockBakedQuad(x, y, z, quad, quadInstance);
    }

    private static final Set<Block> FULL_BLOCK_ENTITY_REQUIRED_FOR = new BlockSetBuilder()
            .addBlock(Blocks.PLAYER_HEAD)
            .addBlock(Blocks.PLAYER_WALL_HEAD)
            .build();

    private static final Set<Block> LEVEL_REQUIRED_FOR = new BlockSetBuilder()
            .addBlock(Blocks.CHEST)
            .build();

    private BlockEntity copyBlockEntity(BlockState blockState) {
        EntityBlock entityBlock = (EntityBlock) blockState.getBlock();
        return entityBlock.newBlockEntity(new BlockPos(0, 0, 0), blockState);
    }

    private BlockEntity fetchBlockEntity(BlockPos blockPos, BlockState blockState) {
        try {
            var level = Minecraft.getInstance().level;
            if (level == null || !level.isInValidBounds(blockPos)) return copyBlockEntity(blockState);

            var chunk = level.getChunkAt(blockPos);
            return chunk.getBlockEntity(blockPos);
        } catch (Exception e) {
            return copyBlockEntity(blockState);
        }
    }

    private void submitBlockEntity(
            BlockPos blockPos,
            BlockState blockState,
            BlockAndTintGetter blockAndTintGetter,
            BlockBuilder builder
    ) {
        builder.useOffset(0f, 0f, 0f);
        bufferSource.setBlockBuilder(builder);

        try {
            if (FabricLoader.getInstance().isModLoaded("entity_model_features")) {
                BlockEntityRenderQueue.runOnRenderThread(() -> submitBlockEntityOnCurrentThread(
                        blockPos,
                        blockState,
                        blockAndTintGetter
                ));
            } else {
                submitBlockEntityOnCurrentThread(blockPos, blockState, blockAndTintGetter);
            }
        } finally {
            bufferSource.setBlockBuilder(null);
        }
    }

    private void submitBlockEntityOnCurrentThread(
            BlockPos blockPos,
            BlockState blockState,
            BlockAndTintGetter blockAndTintGetter
    ) {
        try {
            bufferSource.withActiveCapture(() -> {
            BlockEntity entity = FULL_BLOCK_ENTITY_REQUIRED_FOR.contains(blockState.getBlock()) ?
                    fetchBlockEntity(blockPos, blockState) :
                    copyBlockEntity(blockState);

            if (entity == null) return null;

            if (LEVEL_REQUIRED_FOR.contains(blockState.getBlock()))
                entity.setLevel((Level) blockAndTintGetter);

            @SuppressWarnings("unchecked")
            BlockEntityRenderer<BlockEntity, BlockEntityRenderState> renderer =
                    (BlockEntityRenderer<BlockEntity, BlockEntityRenderState>) (Object)
                            Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(entity);

            if (renderer == null) return null;

            var renderState = renderer.createRenderState();
            renderer.extractRenderState(entity, renderState, 0.8f, cameraRenderState.pos, null);

            poseStack.pushPose();
            try {
                renderer.submit(renderState, poseStack, bufferSource, cameraRenderState);
            } finally {
                poseStack.popPose();
            }
            return null;
            });
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("Could not capture block-entity geometry", failure);
        } finally {
            bufferSource.setBlockBuilder(null);
        }
    }
}
