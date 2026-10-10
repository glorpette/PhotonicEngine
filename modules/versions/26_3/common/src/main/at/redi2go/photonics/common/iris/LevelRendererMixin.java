package at.redi2go.photonics.common.iris;

import at.redi2go.photonics.engine.iris.IrisManager;
import at.redi2go.photonics.mixins.MixinOrder;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(
            method = "render",
            at = @At("HEAD"),
            order = MixinOrder.EARLY
    )
    public void onRender(CallbackInfo ci) {
        IrisManager.onFrameBegin();
    }
}
