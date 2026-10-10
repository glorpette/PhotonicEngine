package at.redi2go.photonics.impl.blaze3d.opengl.systems;

import at.redi2go.photonics.game.blaze3d.buffers.BufferUsage;
import at.redi2go.photonics.game.blaze3d.buffers.IGpuBuffer;
import at.redi2go.photonics.game.blaze3d.buffers.IGpuBufferSlice;
import at.redi2go.photonics.game.blaze3d.systems.ICommandEncoder;
import at.redi2go.photonics.game.blaze3d.textures.IGpuTexture;
import at.redi2go.photonics.game.blaze3d.textures.TextureFormat;
import com.mojang.renderpearl.backend.api.CommandEncoderBackend;
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import org.joml.Vector2ic;
import org.joml.Vector3ic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

@Mixin(FrontendCommandEncoder.class)
public abstract class FrontendCommandEncoderImpl implements ICommandEncoder {
    @Shadow @Final private CommandEncoderBackend backend;

    @Override
    public void ph$writeToBuffer(IGpuBufferSlice gpuBufferSlice, ByteBuffer byteBuffer) {
        ((ICommandEncoder) backend).ph$writeToBuffer(gpuBufferSlice, byteBuffer);
    }

    @Override
    public IGpuBuffer.MappedView ph$mapBuffer(IGpuBuffer gpuBuffer, @BufferUsage int mapUsage) {
        return ((ICommandEncoder) backend).ph$mapBuffer(gpuBuffer, mapUsage);
    }

    @Override
    public IGpuBuffer.MappedView ph$mapBuffer(IGpuBufferSlice gpuBufferSlice, @BufferUsage int mapUsage) {
        return ((ICommandEncoder) backend).ph$mapBuffer(gpuBufferSlice, mapUsage);
    }

    @Override
    public void ph$copyToBuffer(IGpuBufferSlice srcSlice, IGpuBufferSlice dstSlice) {
        ((ICommandEncoder) backend).ph$copyToBuffer(srcSlice, dstSlice);
    }

    @Override
    public void ph$writeToTexture(
            IGpuTexture gpuTexture,
            ByteBuffer byteBuffer,
            TextureFormat bufferFormat,
            int layer,
            int mipLevel,
            Vector3ic size,
            Vector3ic offset
    ) {
        ((ICommandEncoder) backend).ph$writeToTexture(gpuTexture, byteBuffer, bufferFormat, layer, mipLevel, size, offset);
    }

    @Override
    public CompletableFuture<Void> ph$copyTextureToBuffer(
            IGpuTexture srcTexture,
            Vector2ic srcOffset,
            Vector2ic copySize,
            IGpuBuffer dstBuffer,
            long dstOffset,
            int layer,
            int mipLevel
    ) {
        return ((ICommandEncoder) backend).ph$copyTextureToBuffer(srcTexture, srcOffset, copySize, dstBuffer, dstOffset, layer, mipLevel);
    }
}
