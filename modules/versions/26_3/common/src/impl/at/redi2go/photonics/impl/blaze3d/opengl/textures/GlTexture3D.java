package at.redi2go.photonics.impl.blaze3d.opengl.textures;

import at.redi2go.photonics.game.blaze3d.textures.TextureFormat;
import at.redi2go.photonics.game.blaze3d.textures.TextureUsage;
import at.redi2go.photonics.impl.blaze3d.opengl.GlConstExt;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import org.joml.Vector3ic;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.ByteBuffer;

public final class GlTexture3D extends GlTexture {
    GlTexture3D(
            @Nullable String label,
            @TextureUsage int usage,
            TextureFormat format,
            Vector3ic size,
            int mipLevels
    ) {
        super(label, usage, format, size, mipLevels);
    }

    @Override
    protected String printDimensions(Vector3ic size) {
        return "%sx%sx%s".formatted(size.x(), size.y(), size.z());
    }

    @Override
    public int getTarget() {
        return GL12.GL_TEXTURE_3D;
    }

    @Override
    public int ph$getLayers() {
        return 1;
    }

    @Override
    public int ph$getDepth(int mipLevel) {
        return mipDimension(depthOrLayers, mipLevel);
    }

    @Override
    protected void texImageImpl(int layer, int mipLevel, Vector3ic size, @Nullable ByteBuffer pixels) {
        GL12.glTexImage3D(
                GL12.GL_TEXTURE_3D,
                mipLevel,
                GlConstExt.toGlInternalId(format),
                size.x(),
                size.y(),
                size.z(),
                0,
                GlConstExt.toGlTexelFormatId(format.getTexelFormat()),
                GlConstExt.toGlTexelTypeId(format.getTexelType()),
                pixels
        );
    }

    @Override
    protected void texSubDataImpl(ByteBuffer byteBuffer, TextureFormat bufferFormat, int layer, int mipLevel, Vector3ic size, Vector3ic offset) {
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 1);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);

        GL12.glTexSubImage3D(
                GL12.GL_TEXTURE_3D,
                mipLevel,
                offset.x(),
                offset.y(),
                offset.z(),
                size.x(),
                size.y(),
                size.z(),
                GlConstExt.toGlTexelFormatId(bufferFormat.getTexelFormat()),
                GlConstExt.toGlTexelTypeId(bufferFormat.getTexelType()),
                byteBuffer
        );
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
    }
}
