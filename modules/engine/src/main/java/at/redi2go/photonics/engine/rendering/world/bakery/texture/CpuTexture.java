package at.redi2go.photonics.engine.rendering.world.bakery.texture;

import at.redi2go.photonics.engine.rendering.world.block.TextureData;

public interface CpuTexture {
    int sample(float u, float v);

    @FunctionalInterface
    interface Factory {
        CpuTexture create(int width, int height, int defaultValue, int[] data);
    }
}
