package at.redi2go.photonics.common.iris.pipeline.buffers;

import com.mojang.renderpearl.backend.opengl.GlBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GlBuffer.class)
public interface GlBufferAccessor {
    @Accessor("handle")
    int getHandle();
}
