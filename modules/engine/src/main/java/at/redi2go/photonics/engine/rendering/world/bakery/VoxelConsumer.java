package at.redi2go.photonics.engine.rendering.world.bakery;

public interface VoxelConsumer {
    void acceptVoxel(
            int x, int y, int z,
            int normal,
            int blockId,
            int color,
            int textureNormal,
            int specular
    );
}
