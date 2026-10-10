package at.redi2go.photonics.engine.rendering.world.registry.object;

import at.redi2go.photonics.game.Disposable;

public class NoMemory implements Disposable {
    public static final NoMemory INSTANCE = new NoMemory();

    private NoMemory() {

    }

    @Override
    public void close() {

    }
}
