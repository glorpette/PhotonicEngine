package at.redi2go.photonics.engine.rendering.world.bakery.texture;

import at.redi2go.photonics.engine.rendering.world.block.TextureData;
import org.jspecify.annotations.Nullable;
import org.joml.Math;

import java.util.Objects;

public class Rgba8Texture implements CpuTexture {
    private final int width, height;

    private final int defaultValue;
    private final int[] color;
    private final int constantColor;

    public Rgba8Texture(
            int width, int height,
            int defaultValue,
            int[] data
    ) {
        Objects.requireNonNull(data);

        this.width = width;
        this.height = height;

        this.defaultValue = defaultValue;
        if (data.length == 1) {
            this.color = null;
            this.constantColor = fromABGR(data[0]);
        } else {
            this.color = data;
            this.constantColor = defaultValue;
        }
    }

    @Override
    public int sample(float u, float v) {
        if (color == null) return constantColor;

        int realU = Math.clamp(Math.round(u * (width - 0.5f)), 0, width - 1);
        int realV = Math.clamp(Math.round(v * (height - 0.5f)), 0, height - 1);

        int index = (width * realV) + realU;
        if (index >= color.length) return defaultValue;

        return fromABGR(color[index]);
    }

    @Override
    public long memoryBytes() {
        return color == null ? 0 : (long) color.length * Integer.BYTES;
    }

    private static int fromABGR(int value) {
        return Integer.reverseBytes(Integer.rotateLeft(value, 8));
    }
}
