package at.redi2go.photonics.impl.blaze3d.opengl.systems;

import at.redi2go.photonics.game.blaze3d.buffers.BufferUsage;
import at.redi2go.photonics.game.blaze3d.buffers.IGpuBuffer;
import at.redi2go.photonics.game.blaze3d.buffers.IGpuBufferSlice;
import at.redi2go.photonics.game.blaze3d.systems.ICommandEncoder;
import at.redi2go.photonics.game.blaze3d.textures.IGpuTexture;
import at.redi2go.photonics.game.blaze3d.textures.TextureFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import org.joml.Vector2ic;
import org.joml.Vector3ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

@Mixin(FrontendCommandEncoder.class)
public abstract class FrontendCommandEncoderImpl implements ICommandEncoder {
    @Shadow
    public abstract void writeToBuffer(GpuBufferSlice slice, ByteBuffer data);

    @Shadow
    public abstract void copyToBuffer(GpuBufferSlice src, GpuBufferSlice dst);

    @Shadow
    public abstract void writeToTexture(
            GpuTexture destination,
            ByteBuffer source,
            int mipLevel,
            int depthOrLayer,
            int destX,
            int destY,
            int width,
            int height
    );

    @Shadow
    public abstract void copyTextureToBuffer(
            GpuTexture source,
            GpuBuffer destination,
            long offset,
            Runnable callback,
            int mipLevel,
            int x,
            int y,
            int width,
            int height
    );

    @Override
    public void ph$writeToBuffer(IGpuBufferSlice gpuBufferSlice, ByteBuffer byteBuffer) {
        writeToBuffer((GpuBufferSlice) (Object) gpuBufferSlice, byteBuffer);
    }

    @Override
    public IGpuBuffer.MappedView ph$mapBuffer(IGpuBuffer gpuBuffer, @BufferUsage int mapUsage) {
        if ((mapUsage & (BufferUsage.MAP_READ | BufferUsage.MAP_WRITE)) == 0)
            throw new IllegalArgumentException("Must have one of BufferUsage.MAP_READ or BufferUsage.MAP_WRITE");

        return (IGpuBuffer.MappedView) (Object) ((GpuBuffer) (Object) gpuBuffer).slice().map(
                (mapUsage & BufferUsage.MAP_READ) != 0,
                (mapUsage & BufferUsage.MAP_WRITE) != 0
        );
    }

    @Override
    public IGpuBuffer.MappedView ph$mapBuffer(IGpuBufferSlice gpuBufferSlice, @BufferUsage int mapUsage) {
        if ((mapUsage & (BufferUsage.MAP_READ | BufferUsage.MAP_WRITE)) == 0)
            throw new IllegalArgumentException("Must have one of BufferUsage.MAP_READ or BufferUsage.MAP_WRITE");

        return (IGpuBuffer.MappedView) (Object) ((GpuBufferSlice) (Object) gpuBufferSlice).map(
                (mapUsage & BufferUsage.MAP_READ) != 0,
                (mapUsage & BufferUsage.MAP_WRITE) != 0
        );
    }

    @Override
    public void ph$copyToBuffer(IGpuBufferSlice srcSlice, IGpuBufferSlice dstSlice) {
        copyToBuffer((GpuBufferSlice) (Object) srcSlice, (GpuBufferSlice) (Object) dstSlice);
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
        writeToTexture(
                (GpuTexture) (Object) gpuTexture,
                byteBuffer,
                mipLevel,
                layer,
                offset.x(),
                offset.y(),
                size.x(),
                size.y()
        );
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
        var future = new CompletableFuture<Void>();
        copyTextureToBuffer(
                (GpuTexture) (Object) srcTexture,
                (GpuBuffer) (Object) dstBuffer,
                dstOffset,
                () -> future.complete(null),
                mipLevel,
                srcOffset.x(),
                srcOffset.y(),
                copySize.x(),
                copySize.y()
        );
        return future;
    }
}
