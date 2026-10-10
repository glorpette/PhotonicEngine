package at.redi2go.photonics.impl.blaze3d.common.textures;

import at.redi2go.photonics.game.blaze3d.textures.IAddressMode;
import at.redi2go.photonics.game.blaze3d.textures.IFilterMode;
import at.redi2go.photonics.game.blaze3d.textures.IGpuSampler;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.OptionalDouble;

@Mixin(GpuSampler.class)
public interface GpuSamplerImpl extends IGpuSampler {
    @Shadow
    AddressMode getAddressModeU();

    @Shadow
    AddressMode getAddressModeV();

    @Shadow
    FilterMode getMinFilter();

    @Shadow
    FilterMode getMagFilter();

    @Shadow
    int getMaxAnisotropy();

    @Shadow
    OptionalDouble getMaxLod();

    @Override
    default IAddressMode ph$addressModeU() {
        return (IAddressMode) (Object) getAddressModeU();
    }

    @Override
    default IAddressMode ph$addressModeV() {
        return (IAddressMode) (Object) getAddressModeV();
    }

    @Override
    default IFilterMode ph$minFilter() {
        return (IFilterMode) (Object) getMinFilter();
    }

    @Override
    default IFilterMode ph$magFilter() {
        return (IFilterMode) (Object) getMagFilter();
    }

    @Override
    default int ph$maxAnisotropy() {
        return getMaxAnisotropy();
    }

    @Override
    default OptionalDouble ph$maxLod() {
        return getMaxLod();
    }
}
