package at.redi2go.photonics.engine.rendering.world.bakery;

import at.redi2go.photonics.engine.rendering.world.block.TextureData;

public interface VoxelConsumer {
    void acceptVoxel(
            int x, int y, int z,
            int normal,
            TextureData textureData
    );
}
