package at.redi2go.photonics.impl.blaze3d.opengl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlCommandEncoder", remap = false)
public interface GlCommandEncoderAccessor {
    @Accessor(value = "readFbo", remap = false)
    int ph$getReadFbo();

}
