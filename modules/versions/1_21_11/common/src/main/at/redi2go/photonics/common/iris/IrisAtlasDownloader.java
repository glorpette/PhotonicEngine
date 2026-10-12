package at.redi2go.photonics.common.iris;

import at.redi2go.photonics.common.ResourceReloaderListener;
import at.redi2go.photonics.engine.Photonics;
import at.redi2go.photonics.engine.rendering.world.IgnoredInterruptedException;
import at.redi2go.photonics.engine.rendering.world.bakery.texture.AtlasDownloader;
import at.redi2go.photonics.engine.rendering.world.bakery.texture.AtlasTexture;
import at.redi2go.photonics.engine.rendering.world.bakery.texture.CpuTexture;
import at.redi2go.photonics.engine.rendering.world.bakery.texture.Rgba8Texture;
import at.redi2go.photonics.engine.rendering.world.block.TextureData;
import at.redi2go.photonics.game.minecraft.Id;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import it.unimi.dsi.fastutil.Pair;
import net.irisshaders.iris.pbr.texture.PBRTextureHolder;
import net.irisshaders.iris.pbr.texture.PBRTextureManager;
import net.irisshaders.iris.targets.backed.NativeImageBackedSingleColorTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public class IrisAtlasDownloader implements AtlasDownloader {
    private final Map<TextureFormat, CpuTexture.Factory> textureFormats = new HashMap<>();
    private final ConcurrentHashMap<Id, CompletableFuture<AtlasTexture>> cache = new ConcurrentHashMap<>();
    private final TextureManager textureManager = Minecraft.getInstance().getTextureManager();
    private final Runnable clearCacheListener = cache::clear;

    public IrisAtlasDownloader() {
        textureFormats.put(TextureFormat.RGBA8, Rgba8Texture::new);
        ResourceReloaderListener.add(clearCacheListener);
    }

    private CompletableFuture<AtlasTexture> downloadTexture(Id atlasId) {
        return cache.computeIfAbsent(atlasId, id ->
                getTextureAtlas(id)
                        .thenCompose(this::getTextureData)
                        .thenCompose(this::getPbrTextures)
                        .thenCompose(result -> {
                            var albedo = result.first();
                            var pbrHolder = result.second();
                            var normalTexture = getTextureData(pbrHolder.normalTexture());
                            var specularTexture = getTextureData(pbrHolder.specularTexture());

                            return CompletableFuture.allOf(normalTexture, specularTexture)
                                    .thenApply(ignored -> new AtlasTexture(
                                            createCpuTexture(albedo, 0),
                                            createCpuTexture(normalTexture.getNow(null), TextureData.DEFAULT_NORMAL),
                                            createCpuTexture(specularTexture.getNow(null), TextureData.DEFAULT_SPECULAR)
                                    ));
                        })
        );
    }

    @Override
    public void preloadTexture(Id textureId) {
        downloadTexture(textureId);
    }

    @Override
    public AtlasTexture get(Id textureId) {
        try {
            return downloadTexture(textureId).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IgnoredInterruptedException();
        } catch (ExecutionException e) {
            throw new IllegalArgumentException("Could not read texture atlas " + textureId, e.getCause());
        }
    }

    @Override
    public void close() {
        ResourceReloaderListener.remove(clearCacheListener);
        cache.clear();
    }

    private record TextureDownload(AbstractTexture texture, int width, int height, int[] data) {
    }

    private CompletableFuture<@Nullable TextureDownload> getTextureData(AbstractTexture texture) {
        if (texture instanceof NativeImageBackedSingleColorTexture)
            return CompletableFuture.completedFuture(null);

        var result = new CompletableFuture<TextureDownload>();
        try {
            GpuTexture gpuTexture = texture.getTexture();
            int width = gpuTexture.getWidth(0);
            int height = gpuTexture.getHeight(0);
            int byteSize = gpuTexture.getFormat().pixelSize() * width * height;
            var device = RenderSystem.getDevice();
            CommandEncoder commandEncoder = device.createCommandEncoder();

            Minecraft.getInstance().execute(() -> {
                try {
                    GpuBuffer outputBuffer = device.createBuffer(
                            () -> "Photonics voxelization texture output",
                            GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ,
                            byteSize
                    );

                    commandEncoder.copyTextureToBuffer(gpuTexture, outputBuffer, 0, () -> {
                        try (var mappedView = commandEncoder.mapBuffer(outputBuffer, true, false); outputBuffer) {
                            IntBuffer buffer = mappedView.data().asIntBuffer();
                            int[] data = new int[byteSize >> 2];
                            buffer.get(data);
                            result.complete(new TextureDownload(texture, width, height, data));
                        } catch (Throwable error) {
                            result.completeExceptionally(error);
                        }
                    }, 0);
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                }
            });
        } catch (Throwable error) {
            result.completeExceptionally(error);
        }
        return result;
    }

    private CompletableFuture<AbstractTexture> getTextureAtlas(Id atlasId) {
        var result = new CompletableFuture<AbstractTexture>();
        Minecraft.getInstance().execute(() -> {
            try {
                var texture = textureManager.getTexture((Identifier) (Object) atlasId);
                var glTexture = (com.mojang.blaze3d.opengl.GlTexture) texture.getTexture();
                PBRTextureManager.INSTANCE.getOrLoadHolder(glTexture.glId());
                result.complete(texture);
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
        });
        return result;
    }

    private CompletableFuture<Pair<TextureDownload, PBRTextureHolder>> getPbrTextures(TextureDownload atlas) {
        var result = new CompletableFuture<Pair<TextureDownload, PBRTextureHolder>>();
        Minecraft.getInstance().execute(() -> {
            try {
                var glTexture = (com.mojang.blaze3d.opengl.GlTexture) atlas.texture().getTexture();
                result.complete(Pair.of(atlas, PBRTextureManager.INSTANCE.getHolder(glTexture.glId())));
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
        });
        return result;
    }

    private @Nullable CpuTexture createCpuTexture(@Nullable TextureDownload download, int defaultValue) {
        if (download == null) return null;

        CpuTexture.Factory format = textureFormats.get(download.texture().getTexture().getFormat());
        if (format == null) {
            Photonics.LOGGER.warn("Unsupported texture format: {}", download.texture().getTexture().getFormat());
            return null;
        }
        return format.create(download.width(), download.height(), defaultValue, download.data());
    }
}
