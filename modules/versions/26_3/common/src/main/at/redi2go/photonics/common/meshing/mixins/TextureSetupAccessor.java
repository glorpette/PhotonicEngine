package at.redi2go.photonics.common.meshing.mixins;

import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = { "net.minecraft.client.renderer.rendertype.RenderSetup$TextureBinding" })
public interface TextureSetupAccessor {
    @Accessor
    Identifier getLocation();
}
