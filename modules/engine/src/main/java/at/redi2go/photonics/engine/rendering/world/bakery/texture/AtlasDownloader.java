package at.redi2go.photonics.engine.rendering.world.bakery.texture;

import at.redi2go.photonics.game.Disposable;
import at.redi2go.photonics.game.minecraft.Id;

public interface AtlasDownloader extends Disposable {
    void preloadTexture(Id atlasId);

    AtlasTexture get(Id atlasId);

    /** Identifies the active resource generation used to bake atlas-dependent models. */
    default long generation() {
        return 0L;
    }

    /** Registers a callback that runs after the atlas resources have been reloaded. */
    default Disposable onResourceReload(Runnable callback) {
        return () -> {};
    }
}
