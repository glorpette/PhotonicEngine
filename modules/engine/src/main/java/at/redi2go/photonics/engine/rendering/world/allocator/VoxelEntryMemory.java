package at.redi2go.photonics.engine.rendering.world.allocator;

import at.redi2go.photonics.game.Disposable;

public interface VoxelEntryMemory extends Disposable {
    void setEntryFlag(boolean flag);

    void setEntryData(int entryData);

    void setChildMask(long mask);

    void setExtraFields(int... extra);

    void upload();
}
