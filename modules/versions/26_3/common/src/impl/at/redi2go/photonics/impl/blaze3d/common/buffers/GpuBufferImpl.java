package at.redi2go.photonics.impl.blaze3d.common.buffers;

import at.redi2go.photonics.game.blaze3d.buffers.IGpuBuffer;
import at.redi2go.photonics.game.blaze3d.buffers.IGpuBufferSlice;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(GpuBuffer.class)
public interface GpuBufferImpl extends IGpuBuffer {
    @Shadow
    int usage();

    @Shadow
    long size();

    @Shadow
    GpuBufferSlice slice(long l, long m);

    @Shadow
    GpuBufferSlice slice();

    @Shadow
    boolean isClosed();

    @Override
    default int ph$usage() {
        return usage();
    }

    @Override
    default long ph$size() {
        return size();
    }

    @Override
    default IGpuBufferSlice ph$slice(long offset, long length) {
        return (IGpuBufferSlice) (Object) slice(offset, length);
    }

    @Override
    default IGpuBufferSlice ph$slice() {
        return (IGpuBufferSlice) (Object) slice();
    }

    @Override
    default boolean ph$isClosed() {
        return isClosed();
    }
}
