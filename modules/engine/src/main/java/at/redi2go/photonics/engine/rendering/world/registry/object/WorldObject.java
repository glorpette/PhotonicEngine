package at.redi2go.photonics.engine.rendering.world.registry.object;

import at.redi2go.photonics.game.Disposable;
import org.jspecify.annotations.Nullable;

public interface WorldObject extends Disposable {
    boolean isAllocated();

    void awaitAllocated();

    void acquireReference();

    boolean tryAcquireReference();

    interface Handle<T extends WorldObject> {
        @Nullable T free();
    }
}
