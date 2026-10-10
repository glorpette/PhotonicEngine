package at.redi2go.photonics.common.meshing.impl;

import at.redi2go.photonics.common.meshing.mixins.OuterWrappedRenderTypeAccessor;
import at.redi2go.photonics.common.meshing.mixins.RenderSetupAccessor;
import at.redi2go.photonics.common.meshing.mixins.RenderTypeAccessor;
import at.redi2go.photonics.common.meshing.mixins.TextureSetupAccessor;
import at.redi2go.photonics.engine.rendering.world.bakery.BlockBuilder;
import at.redi2go.photonics.game.minecraft.Id;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.irisshaders.iris.layer.OuterWrappedRenderType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;

import java.util.List;

public class BlockBuilderBufferSource implements SubmitNodeCollector {
    private static final Id BLOCK_ATLAS = (Id) (Object) TextureAtlas.LOCATION_BLOCKS;
    private final QuadInstance quadInstance = new QuadInstance();
    private BlockBuilder blockBuilder;

    public void setBlockBuilder(BlockBuilder blockBuilder) {
        this.blockBuilder = blockBuilder;
    }

    public void useTextureFromRenderType(RenderType renderType) {
        if (blockBuilder == null) return;
        if (renderType instanceof OuterWrappedRenderType wrapped)
            renderType = ((OuterWrappedRenderTypeAccessor) wrapped).getWrapped();

        RenderSetup renderSetup = ((RenderTypeAccessor) renderType).getState();
        var textures = ((RenderSetupAccessor) (Object) renderSetup).getTextureSetup();
        TextureSetupAccessor sampler0 = textures.get("Sampler0");

        if (sampler0 != null) {
            blockBuilder.useAtlas((Id) (Object) sampler0.getLocation());
        } else {
            blockBuilder.useAtlas(BLOCK_ATLAS);
        }
    }

    @Override
    public OrderedSubmitNodeCollector order(int i) {
        return this;
    }

    @Override
    public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int light, int overlay, int tint, UvMapping uvMapping, int i) {
        if (blockBuilder == null) return;
        useTextureFromRenderType(renderType);
        model.setupAnim(state);
        model.renderToBuffer(poseStack, (VertexConsumer) blockBuilder, light, overlay, tint);
        model.resetPose();
    }

    @Override
    public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int light, int overlay, int tint) {
        if (blockBuilder == null) return;
        useTextureFromRenderType(renderType);
        model.setupAnim(state);
        model.renderToBuffer(poseStack, (VertexConsumer) blockBuilder, light, overlay, tint);
        model.resetPose();
    }

    @Override
    public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, Identifier texture, int light, int overlay, int tint) {
        if (blockBuilder == null) return;
        blockBuilder.useAtlas((Id) (Object) texture);
        model.setupAnim(state);
        model.renderToBuffer(poseStack, (VertexConsumer) blockBuilder, light, overlay, tint);
        model.resetPose();
    }

    @Override
    public <S> void submitModel(Model<S> model, S state, PoseStack poseStack, int light, int overlay, int tint, SpriteId spriteId, SpriteGetter spriteGetter, int i) {
        if (blockBuilder == null) return;
        blockBuilder.useAtlas((Id) (Object) spriteId.atlasLocation());
        model.setupAnim(state);
        model.renderToBuffer(poseStack, (VertexConsumer) blockBuilder, light, overlay, tint);
        model.resetPose();
    }

    @Override
    public <S> void submitCrumblingOverlay(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int light, int overlay, int tint, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
    }

    @Override
    public void submitModelPart(ModelPart part, PoseStack poseStack, RenderType renderType, int light, int overlay, UvMapping uvMapping, int tint, int order) {
        if (blockBuilder == null) return;
        useTextureFromRenderType(renderType);
        part.render(poseStack, (VertexConsumer) blockBuilder, light, overlay, tint);
    }

    @Override
    public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState, int i) {
    }

    @Override
    public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts, int[] tint, int light, int overlay, int order) {
        if (blockBuilder == null) return;
        useTextureFromRenderType(renderType);
        for (BlockStateModelPart part : parts) {
            for (var quad : part.getQuads(null)) {
                ((VertexConsumer) blockBuilder).putBakedQuad(poseStack.last(), quad, quadInstance);
            }
        }
    }

    @Override
    public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> list, int i, boolean b) {
    }

    @Override
    public void submitShapeOutline(PoseStack poseStack, VoxelShape voxelShape, RenderType renderType, int i, float v, boolean b) {
    }

    @Override
    public void submitItem(PoseStack poseStack, ItemDisplayContext itemDisplayContext, int light, int overlay, int tint, int[] tintLayers, ItemQuads itemQuads, ItemStackRenderState.FoilType foilType) {
    }

    @Override
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
        if (blockBuilder == null) return;
        useTextureFromRenderType(renderType);
        customGeometryRenderer.render(poseStack.last(), (VertexConsumer) blockBuilder);
    }

    @Override
    public void submitQuadParticleGroup(QuadParticleRenderState quadParticleRenderState) {
    }

    @Override
    public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group group, CameraRenderState cameraRenderState, boolean b) {
    }

    @Override
    public void submitShadow(PoseStack poseStack, float v, List<EntityRenderState.ShadowPiece> list) {
    }

    @Override
    public void submitNameTag(PoseStack poseStack, Vec3 vec3, int i, Component component, boolean b, int j, CameraRenderState cameraRenderState) {
    }

    @Override
    public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence text, boolean dropShadow, Font.DisplayMode displayMode, int color, int backgroundColor, int light, int order) {
    }

    @Override
    public void submitTextBackground(PoseStack poseStack, float x, float y, float width, float height, int color, Font.DisplayMode displayMode, int order) {
    }

    @Override
    public void submitFlame(PoseStack poseStack, EntityRenderState entityRenderState, Quaternionf quaternionf) {
    }

    @Override
    public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
    }
}
