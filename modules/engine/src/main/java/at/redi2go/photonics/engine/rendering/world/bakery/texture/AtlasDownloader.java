package at.redi2go.photonics.engine.rendering.world.bakery.texture;

import at.redi2go.photonics.game.Disposable;
import at.redi2go.photonics.game.minecraft.Id;

public interface AtlasDownloader extends Disposable {
    void preloadTexture(Id atlasId);

    AtlasTexture get(Id atlasId);
}
