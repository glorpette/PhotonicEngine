package at.redi2go.photonics.common.meshing.mixins;

import at.redi2go.photonics.common.meshing.impl.BlockBuilderBufferSource;
import at.redi2go.photonics.mixins.Client;
import at.redi2go.photonics.mixins.Optional;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Client
@Optional
@Pseudo
@Mixin(targets = "traben.entity_model_features.models.parts.EMFModelPart$1", remap = false)
public abstract class EmfBufferRedirectMixin {
    @Inject(method = "getBuffer", at = @At("HEAD"), cancellable = true, remap = false)
    private void photonics$redirectTextureOverride(RenderType renderType, CallbackInfoReturnable<VertexConsumer> cir) {
        VertexConsumer consumer = BlockBuilderBufferSource.getEmfTextureOverrideBuffer(renderType);
        if (consumer != null)
            cir.setReturnValue(consumer);
    }
}
