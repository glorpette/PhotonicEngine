package at.redi2go.photonics.engine.rendering.world.bakery.texture;

import at.redi2go.photonics.engine.rendering.world.block.TextureData;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public class AtlasTexture {
    private final CpuTexture albedo;
    private final @Nullable CpuTexture normal;
    private final @Nullable CpuTexture specular;
    private final long textureHash;

    public AtlasTexture(CpuTexture albedo, @Nullable CpuTexture normal, @Nullable CpuTexture specular) {
        this(albedo, normal, specular, System.identityHashCode(albedo));
    }

    public AtlasTexture(CpuTexture albedo, @Nullable CpuTexture normal, @Nullable CpuTexture specular, long textureHash) {
        Objects.requireNonNull(albedo, "albedo was null");

        this.albedo = albedo;
        this.normal = normal;
        this.specular = specular;
        this.textureHash = textureHash;
    }

    public TextureData sample(
            int blockId,
            float u,
            float v
    ) {
        return new TextureData(
                blockId,
                albedo.sample(u, v),
                normal != null ? normal.sample(u, v) : TextureData.DEFAULT_NORMAL,
                specular != null ? specular.sample(u, v) : TextureData.DEFAULT_SPECULAR
        );
    }

    public int sampleColor(float u, float v) {
        return albedo.sample(u, v);
    }

    public int sampleNormal(float u, float v) {
        return normal == null ? TextureData.DEFAULT_NORMAL : normal.sample(u, v);
    }

    public int sampleSpecular(float u, float v) {
        return specular == null ? TextureData.DEFAULT_SPECULAR : specular.sample(u, v);
    }

    public long memoryBytes() {
        return albedo.memoryBytes()
                + (normal == null ? 0 : normal.memoryBytes())
                + (specular == null ? 0 : specular.memoryBytes());
    }

    public long textureHash() {
        return textureHash;
    }
}
