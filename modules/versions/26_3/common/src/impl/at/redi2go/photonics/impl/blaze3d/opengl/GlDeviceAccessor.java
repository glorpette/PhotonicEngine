package at.redi2go.photonics.impl.blaze3d.opengl;

import com.mojang.renderpearl.backend.opengl.DirectStateAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlDevice", remap = false)
public interface GlDeviceAccessor {
    @Accessor(value = "directStateAccess", remap = false)
    DirectStateAccess ph$getDirectStateAccess();
}
