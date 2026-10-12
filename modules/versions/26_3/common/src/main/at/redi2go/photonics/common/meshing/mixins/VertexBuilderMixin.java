package at.redi2go.photonics.common.meshing.mixins;

import at.redi2go.photonics.engine.rendering.world.bakery.BlockBuilder;
import at.redi2go.photonics.engine.rendering.world.block.VoxelColor;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormatElement;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BlockBuilder.class)
public interface VertexBuilderMixin extends VertexConsumer, VertexBufferWriter {
    @Shadow
    BlockBuilder shadow$addVertex(float x, float y, float z);

    @Override
    default @NonNull VertexConsumer addVertex(float x, float y, float z) {
        return (VertexConsumer) shadow$addVertex(x, y, z);
    }

    @Shadow
    BlockBuilder shadow$setTint(int color);

    @Override
    default @NonNull VertexConsumer setColor(int argb) {
        return (VertexConsumer) shadow$setTint(argb);
    }

    @Override
    default @NonNull VertexConsumer setColor(int r, int g, int b, int a) {
        return (VertexConsumer) shadow$setTint(VoxelColor.from(r, g, b, a));
    }

    @Shadow
    BlockBuilder shadow$setUv(float u, float v);

    @Override
    default @NonNull VertexConsumer setUv(float u, float v) {
        return (VertexConsumer) shadow$setUv(u, v);
    }

    @Override
    default @NonNull VertexConsumer setUv1(int i, int j) {
        return this;
    }

    @Override
    default VertexConsumer setUv2(int i, int j) {
        return this;
    }

    @Override
    default VertexConsumer setNormal(float f, float g, float h) {
        return this;
    }

    @Override
    default VertexConsumer setUv3(float u, float v) {
        return this;
    }

    @Override
    default VertexConsumer setLineWidth(float f) {
        return this;
    }

    @Override
    default void push(MemoryStack stack, long pointer, int vertexCount, VertexFormat format) {
        VertexFormatElement position = format.getElement(DefaultVertexFormat.POSITION_SEMANTIC_NAME);
        VertexFormatElement color = format.getElement(DefaultVertexFormat.COLOR_SEMANTIC_NAME);
        VertexFormatElement uv = format.getElement(DefaultVertexFormat.UV0_SEMANTIC_NAME);

        if (position == null || position.format() != GpuFormat.RGB32_FLOAT)
            throw new IllegalArgumentException("Unsupported Sodium position format for Photonics: " + format);
        if (color != null && color.format() != GpuFormat.RGBA8_UNORM && color.format() != GpuFormat.RGBA8_UINT)
            throw new IllegalArgumentException("Unsupported Sodium color format for Photonics: " + format);
        if (uv != null && uv.format() != GpuFormat.RG32_FLOAT)
            throw new IllegalArgumentException("Unsupported Sodium UV format for Photonics: " + format);

        int stride = format.getVertexSize();
        BlockBuilder builder = (BlockBuilder) (Object) this;
        for (int i = 0; i < vertexCount; i++) {
            long vertex = pointer + (long) i * stride;
            builder.addVertex(
                    MemoryUtil.memGetFloat(vertex + position.offset()),
                    MemoryUtil.memGetFloat(vertex + position.offset() + Float.BYTES),
                    MemoryUtil.memGetFloat(vertex + position.offset() + Float.BYTES * 2)
            );

            if (color != null)
                builder.setTint(MemoryUtil.memGetInt(vertex + color.offset()));
            if (uv != null)
                builder.setUv(
                        MemoryUtil.memGetFloat(vertex + uv.offset()),
                        MemoryUtil.memGetFloat(vertex + uv.offset() + Float.BYTES)
                );
        }
    }
}
