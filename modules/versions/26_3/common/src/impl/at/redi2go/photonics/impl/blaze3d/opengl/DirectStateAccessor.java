package at.redi2go.photonics.impl.blaze3d.opengl;

import com.mojang.renderpearl.backend.opengl.DirectStateAccess;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(DirectStateAccess.class)
public interface DirectStateAccessor {
    default void invokeBindFrameBufferTextures(int fbo, int color0, int depth, int mipLevel, int target) {
        ((DirectStateAccess) (Object) this).bindFrameBufferTextures(fbo, color0, depth, mipLevel, target);
    }
}
