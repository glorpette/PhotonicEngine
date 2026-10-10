package at.redi2go.photonics.common.meshing;

import at.redi2go.photonics.game.minecraft.core.IBlockPos;
import at.redi2go.photonics.game.minecraft.world.level.IBlockAndTintGetter;
import at.redi2go.photonics.game.minecraft.world.level.IBlockState;
import at.redi2go.photonics.engine.rendering.world.bakery.BlockBuilder;
import at.redi2go.photonics.engine.rendering.world.bakery.BlockMesher;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3i;

public class MinecraftBlockMesher implements BlockMesher<McMeshState> {
    private static final ThreadLocal<McBlockRenderer> RENDERERS = ThreadLocal.withInitial(McBlockRenderer::new);

    @Override
    public void setup() {
    }

    @Override
    public void teardown() {
    }

    @Override
    public McMeshState extractMeshState(
            Vector3i blockChunkOffset,
            IBlockPos pos,
            IBlockState blockState,
            IBlockAndTintGetter blockAndTintGetter
    ) {
        return RENDERERS.get().extractMeshState(
                blockChunkOffset,
                (BlockPos) pos,
                (BlockState) blockState,
                (BlockAndTintGetter) blockAndTintGetter
        );
    }

    @Override
    public void meshBlock(
            McMeshState meshState,
            Vector3i blockChunkOffset,
            IBlockPos pos,
            IBlockState blockState,
            IBlockAndTintGetter blockAndTintGetter,
            BlockBuilder blockBuilder
    ) {
        RENDERERS.get().meshBlock(
                meshState,
                blockChunkOffset,
                (BlockPos) pos,
                (BlockState) blockState,
                (BlockAndTintGetter) blockAndTintGetter,
                blockBuilder
        );
    }
}
