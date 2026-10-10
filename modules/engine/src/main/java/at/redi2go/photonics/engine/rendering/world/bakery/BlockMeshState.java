package at.redi2go.photonics.engine.rendering.world.bakery;

public interface BlockMeshState {
    boolean shouldCache();

    void prepareCacheUse();
}
