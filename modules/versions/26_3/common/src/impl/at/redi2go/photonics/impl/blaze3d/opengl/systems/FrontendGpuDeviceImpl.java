package at.redi2go.photonics.impl.blaze3d.opengl.systems;

import at.redi2go.photonics.game.blaze3d.buffers.BufferUsage;
import at.redi2go.photonics.game.blaze3d.buffers.IGpuBuffer;
import at.redi2go.photonics.game.blaze3d.systems.ICommandEncoder;
import at.redi2go.photonics.game.blaze3d.systems.IGpuDevice;
import at.redi2go.photonics.game.blaze3d.textures.IAddressMode;
import at.redi2go.photonics.game.blaze3d.textures.IFilterMode;
import at.redi2go.photonics.game.blaze3d.textures.IGpuSampler;
import at.redi2go.photonics.game.blaze3d.textures.IGpuTexture;
import at.redi2go.photonics.game.blaze3d.textures.TextureFormat;
import at.redi2go.photonics.game.blaze3d.textures.TextureUsage;
import at.redi2go.photonics.impl.blaze3d.opengl.textures.GlTexture;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.frontend.FrontendGpuDevice;
import net.minecraft.util.StringUtil;
import org.joml.Vector3ic;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.KHRDebug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.function.Supplier;

@Mixin(FrontendGpuDevice.class)
public abstract class FrontendGpuDeviceImpl implements IGpuDevice {

    @Shadow
    public abstract CommandEncoder createCommandEncoder();

    @Shadow
    public abstract GpuSampler createSampler(AddressMode addressMode, AddressMode addressMode2, FilterMode filterMode, FilterMode filterMode2, int i, OptionalDouble optionalDouble);

    @Shadow
    public abstract GpuBuffer createBuffer(@Nullable Supplier<String> supplier, int i, long l);

    @Shadow
    public abstract GpuBuffer createBuffer(@Nullable Supplier<String> supplier, int i, ByteBuffer byteBuffer);

    @Shadow
    public abstract boolean isDebuggingEnabled();

    @Override
    public ICommandEncoder ph$createCommandEncoder() {
        return (ICommandEncoder) createCommandEncoder();
    }

    @Override
    @SuppressWarnings("DataFlowIssue")
    public IGpuSampler ph$createSampler(IAddressMode addressModeU, IAddressMode addressModeV, IFilterMode minFilter, IFilterMode magFilter, int maxAnisotropy, OptionalDouble maxLod) {
        return (IGpuSampler) createSampler(
                (AddressMode) (Object) addressModeU,
                (AddressMode) (Object) addressModeV,
                (FilterMode) (Object) minFilter,
                (FilterMode) (Object) magFilter,
                maxAnisotropy,
                maxLod
        );
    }

    @Override
    public IGpuTexture ph$createTexture(@Nullable Supplier<String> label, @TextureUsage int usage, TextureFormat textureFormat, Vector3ic size, int mipLevels) {
        GlTexture texture = GlTexture.createTexture(label != null ? label.get() : null, usage, textureFormat, size, mipLevels);

        try (var state = texture.createStateAccess()) {
            state.texParameter(GL12.GL_TEXTURE_MIN_LOD, 0);
            state.texParameter(GL12.GL_TEXTURE_MAX_LOD, mipLevels - 1);
            state.texParameter(GL12.GL_TEXTURE_MAX_LEVEL, mipLevels - 1);

            for (int layer = 0; layer < texture.ph$getLayers(); layer++) {
                for (int mip = 0; mip < mipLevels; mip++)
                    state.texImage(layer, mip, texture.ph$getSize(mip), null);
            }
        }

        if (isDebuggingEnabled() && texture.ph$getLabel() != null) {
            KHRDebug.glObjectLabel(GL11.GL_TEXTURE, texture.ph$getHandle(), StringUtil.truncateStringIfNecessary(texture.ph$getLabel(), 256, true));
        }
        return texture;
    }

    @Override
    public IGpuBuffer ph$createBuffer(@Nullable Supplier<String> label, @BufferUsage int usage, long byteSize) {
        return (IGpuBuffer) createBuffer(label, usage, byteSize);
    }

    @Override
    public IGpuBuffer ph$createBuffer(@Nullable Supplier<String> label, @BufferUsage int usage, ByteBuffer contents) {
        return (IGpuBuffer) createBuffer(label, usage, contents);
    }
}
