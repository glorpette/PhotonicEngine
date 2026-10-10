package at.redi2go.photonics.engine.rendering.world.compiler;

public interface CompilerTask {
    void queueJob(Runnable task);

    void awaitCompletion() throws InterruptedException;
}
