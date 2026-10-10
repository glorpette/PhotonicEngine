package at.redi2go.photonics.engine.rendering;

import at.redi2go.photonics.engine.RenderingComponent;
import at.redi2go.photonics.engine.UniformUpdater;
import at.redi2go.photonics.engine.iris.IrisPack;
import at.redi2go.photonics.engine.iris.properties.PhotonicsProperties;
import at.redi2go.photonics.engine.config.PhConfig;
import at.redi2go.photonics.engine.config.lights.BlockLightInfo;
import at.redi2go.photonics.engine.iris.pipeline.uniforms.IUniformUpdateFrequency;
import at.redi2go.photonics.engine.iris.pipeline.uniforms.IrisDynamicUniformHolder;
import at.redi2go.photonics.game.minecraft.IMinecraft;
import at.redi2go.photonics.game.minecraft.client.player.IHandheldItem;
import at.redi2go.photonics.game.minecraft.client.player.ILocalPlayer;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public class HandheldLightComponent implements RenderingComponent {
    private static final Matrix4fc NO_LIGHT = new Matrix4f(
            new Vector4f(0.0f),
            new Vector4f(0.0f),
            new Vector4f(0.0f),
            new Vector4f(0.0f)
    );

    private static final Vector3f ENCHANTMENT_GLINT_COLOR = new Vector3f(0x21 / 255.0f, 0x0D / 255.0f, 0x4F / 255.0f);
    private static final Vector2f ENCHANTMENT_GLINT_ATTENUATION = new Vector2f(0.9f, 0.9f);
    private static final float ENCHANTMENT_GLINT_FALLOFF = 0.75f;

    private final UniformUpdater mainHandUpdater;
    private final HandheldLightCache mainHand;

    private final UniformUpdater offHandUpdater;
    private final HandheldLightCache offHand;

    private final Matrix4fc enchantedItemLight;

    public HandheldLightComponent(PhotonicsProperties properties) {
        this.mainHandUpdater = new UniformUpdater();
        this.mainHand = new HandheldLightCache(
                () -> Optional.ofNullable(IMinecraft.getLocalPlayer()).flatMap(ILocalPlayer::ph$getMainHand),
                mainHandUpdater
        );

        this.offHandUpdater = new UniformUpdater();
        this.offHand = new HandheldLightCache(
                () -> Optional.ofNullable(IMinecraft.getLocalPlayer()).flatMap(ILocalPlayer::ph$getOffHand),
                offHandUpdater
        );

        var enchantmentGlintStrength = properties.getHandheldProperties().getGlintStrength();
        var enchantmentGlintColor = ENCHANTMENT_GLINT_COLOR.mul(enchantmentGlintStrength, new Vector3f());
        var enchantmentGlintRadius = BlockLightInfo.getBlockRadius(enchantmentGlintColor, ENCHANTMENT_GLINT_ATTENUATION, ENCHANTMENT_GLINT_FALLOFF);

        this.enchantedItemLight = new Matrix4f(
                new Vector4f(0f, 0f, 0f, Float.intBitsToFloat(-1)),
                new Vector4f(enchantmentGlintColor, enchantmentGlintStrength),
                new Vector4f(ENCHANTMENT_GLINT_ATTENUATION, ENCHANTMENT_GLINT_FALLOFF, enchantmentGlintRadius),
                new Vector4f(0f)
        );
    }

    public boolean hasItem() {
        return !mainHand.isEmpty() || !offHand.isEmpty();
    }

    @Override
    public void onFrameBegin() {
        mainHand.update();
        offHand.update();
    }

    @Override
    public void registerDynamicUniforms(IrisDynamicUniformHolder dynamicUniforms) {
        dynamicUniforms.uniform1b(IUniformUpdateFrequency.perFrame(), "left_handed", () -> {
            var player = IMinecraft.getLocalPlayer();
            return player != null && player.ph$isLeftHanded();
        });

        dynamicUniforms.uniform1b(IUniformUpdateFrequency.perFrame(), "main_hand_has_light", () -> !mainHand.isEmpty());
        dynamicUniforms.uniformMatrix("ph_main_hand_light", mainHand::getLightData, mainHandUpdater.newNotifier());

        dynamicUniforms.uniform1b(IUniformUpdateFrequency.perFrame(), "off_hand_has_light", () -> !offHand.isEmpty());
        dynamicUniforms.uniformMatrix("ph_off_hand_light", offHand::getLightData, offHandUpdater.newNotifier());
    }

    private class HandheldLightCache {
        private final Supplier<Optional<IHandheldItem>> handheldItemSupplier;
        private final UniformUpdater updater;

        private IHandheldItem item;
        private Matrix4fc lightData;

        private HandheldLightCache(
                Supplier<Optional<IHandheldItem>> handheldItemSupplier,
                UniformUpdater updater
        ) {
            this.handheldItemSupplier = handheldItemSupplier;
            this.updater = updater;

            this.lightData = NO_LIGHT;
        }

        public boolean isEmpty() {
            return lightData == NO_LIGHT;
        }

        public Matrix4fc getLightData() {
            return lightData;
        }

        private Matrix4fc createEnchantedItemLight(IHandheldItem handheldItem) {
            return handheldItem.ph$isEnchanted() ? enchantedItemLight : NO_LIGHT;
        }

        private Matrix4fc createLightFrom(IHandheldItem handheldItem) {
            var blockState = handheldItem.ph$getBlockState();
            var light = PhConfig.getLightRegistry().get(blockState);

            return light == null ? createEnchantedItemLight(handheldItem) : light.toMatrix4(
                    new Vector3f(0f),
                    IrisPack.getCurrentPack()
                        .map(e -> e.ph$getBlockId(blockState))
                        .orElse(-1)
            );
        }

        public void update() {
            var newItem = handheldItemSupplier.get().orElse(null);
            if (Objects.equals(item, newItem)) return;

            this.item = newItem;
            this.lightData = newItem == null ? NO_LIGHT : createLightFrom(newItem);

            updater.updateNow();
        }
    }
}
