package at.redi2go.photonics.engine.rendering;

import at.redi2go.photonics.engine.PrioritizedTask;
import at.redi2go.photonics.game.minecraft.core.IBlockPos;
import at.redi2go.photonics.game.minecraft.world.level.IBlockState;
import at.redi2go.photonics.game.minecraft.world.level.ILevel;
import at.redi2go.photonics.game.minecraft.world.level.ILightLayer;
import at.redi2go.photonics.game.minecraft.world.level.chunk.IChunkSection;
import at.redi2go.photonics.engine.rendering.world.block.VoxelNormal;
import org.apache.logging.log4j.util.TriConsumer;
import org.jspecify.annotations.Nullable;
import org.joml.RoundingMode;
import org.joml.Vector3i;

public class SectionCopy implements PrioritizedTask, IChunkSection {
    private final Vector3i pos;
    private final IChunkSection copy;
    private final long priority;

    public SectionCopy(
            Vector3i pos,
            IChunkSection section,
            long priority
    ) {
        this.pos = pos;
        this.copy = section.ph$createCopy();
        this.priority = priority;
    }

    @Override
    public long priority() {
        return this.priority;
    }

    public Vector3i pos() {
        return pos;
    }

    public Vector3i blockPos() {
        return pos.mul(16, new Vector3i());
    }

    @Override
    public IBlockState ph$getBlockState(int x, int y, int z) {
        return copy.ph$getBlockState(x, y, z);
    }

    @Override
    public boolean ph$hasOnlyAir() {
        return false;
    }

    @Override
    public IChunkSection ph$createCopy() {
        return this;
    }

    public void forEachBlock(TriConsumer<Vector3i, IBlockPos, IBlockState> blockConsumer) {
        Vector3i chunkOffset = new Vector3i();
        Vector3i sectionBlockPos = blockPos();

        for (int px = 0; px < 16; px++) {
            for (int py = 0; py < 16; py++) {
                for (int pz = 0; pz < 16; pz++) {
                    IBlockPos blockPos = IBlockPos.of(
                            sectionBlockPos.x + px,
                            sectionBlockPos.y + py,
                            sectionBlockPos.z + pz
                    );

                    chunkOffset.set(px, py, pz);
                    blockConsumer.accept(chunkOffset, blockPos, ph$getBlockState(px, py, pz));
                }
            }
        }
    }

    public long computeSectionHash(@Nullable ILevel level) {
        final long[] hash = {0};

        forEachBlock((ignored, blockPos, block) -> {
            int blockHash = (block.hashCode() ^ block.ph$block().hashCode());
            int skylight = level == null ? 0 : compileSkylight(level, blockPos);

            hash[0] = hash[0] * 31 + (((long) skylight) << 32) | blockHash;
        });

        return hash[0];
    }

    public static Vector3i getSectionCoord(IBlockPos blockPos) {
        return new Vector3i(
                blockPos.ph$x() >> 4,
                blockPos.ph$y() >> 4,
                blockPos.ph$z() >> 4
        );
    }

    public static int compileSkylight(ILevel level, IBlockPos pos) {
        int brightness = getSkylightValue(level, pos);

        int result = 0;
        for (int i = 5; i >= 0; i--) {
            var normal = new Vector3i(VoxelNormal.getNormal(i), RoundingMode.TRUNCATE);
            int skylight = getSkylightValue(level, pos.ph$offset(normal));

            result <<= 4;
            result |= Math.max(skylight, brightness);
        }

        return result;
    }

    private static int getSkylightValue(ILevel level, IBlockPos pos) {
        var lightData = level.ph$getSectionLightData(pos.ph$x() >> 4, pos.ph$y() >> 4, pos.ph$z() >> 4, ILightLayer.sky());
        if (lightData == null) return 0;
        return lightData.ph$getLightLevel(pos.ph$x() & 15, pos.ph$y() & 15, pos.ph$z() & 15);
    }
}
