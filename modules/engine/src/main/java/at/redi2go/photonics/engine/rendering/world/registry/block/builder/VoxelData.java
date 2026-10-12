package at.redi2go.photonics.engine.rendering.world.registry.block.builder;

import at.redi2go.photonics.engine.rendering.world.allocator.VoxelEntryMemory;
import at.redi2go.photonics.engine.rendering.world.tree.VoxelTreeEntry;

public class VoxelData implements VoxelTreeEntry {
    public int normal;
    public int blockId;
    public int color;
    public int textureNormal;
    public int specular;

    @Override
    public int depth() {
        return -1;
    }

    @Override
    public void uploadTo(VoxelEntryMemory memory) {
        throw new UnsupportedOperationException("uploadTo");
    }
}
