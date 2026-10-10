package at.redi2go.photonics.engine.rendering.world.allocator;

import at.redi2go.photonics.game.Disposable;

public interface VoxelEntryListMemory extends Disposable {
    int entryData();

    void resize(int newSize);

    VoxelEntryMemory get(int index);

    void upload();
}
