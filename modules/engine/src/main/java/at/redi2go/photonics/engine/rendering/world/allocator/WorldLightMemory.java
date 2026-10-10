package at.redi2go.photonics.engine.rendering.world.allocator;

import at.redi2go.photonics.game.Disposable;
import at.redi2go.photonics.engine.config.lights.BlockLightInfo;

public interface WorldLightMemory extends Disposable {
    int entryData();

    void setLight(BlockLightInfo light, int blockId);

    void upload();
}
