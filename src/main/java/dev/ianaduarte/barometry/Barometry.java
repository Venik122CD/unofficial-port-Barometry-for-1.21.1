package dev.ianaduarte.barometry;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.joml.Vector4f;
import org.slf4j.Logger;

@Mod(Barometry.MOD_ID)
public class Barometry {
    public static final String MOD_ID = "barometry";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Barometry(IEventBus bus) {
    }

    public static final ResourceLocation CLEAN_CLOUDS_LOCATION =
            getLocation("textures/environment/clouds_clean.png");
    public static final ResourceLocation CLEAR_CLEAN_CLOUDS_LOCATION =
            getLocation("textures/environment/clouds_clear_clean.png");
    public static final ResourceLocation CLEAR_CLOUDS_LOCATION =
            getLocation("textures/environment/clouds_clear.png");
    public static final ResourceLocation CLEAR_RAIN_CLOUDS_LOCATION =
            getLocation("textures/environment/clouds_clear_rain.png");
    public static final ResourceLocation RAIN_CLOUDS_LOCATION =
            getLocation("textures/environment/clouds_rain.png");
    public static final ResourceLocation RAIN_THUNDER_CLOUDS_LOCATION =
            getLocation("textures/environment/clouds_rain_thunder.png");
    public static final ResourceLocation THUNDER_CLOUDS_LOCATION =
            getLocation("textures/environment/clouds_thunder.png");

    public static final ResourceLocation[] CLOUD_TEXTURES = {
            CLEAN_CLOUDS_LOCATION,
            CLEAR_CLEAN_CLOUDS_LOCATION,
            CLEAR_CLOUDS_LOCATION,
            CLEAR_RAIN_CLOUDS_LOCATION,
            RAIN_CLOUDS_LOCATION,
            RAIN_THUNDER_CLOUDS_LOCATION,
            THUNDER_CLOUDS_LOCATION
    };

    public static ResourceLocation getLocation(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static float roundN(float f, float n) {
        return (float) Math.round(f / n) * n;
    }

    public static float gradient(float delta, float... values) {
        if (values.length == 0)
            throw new IllegalArgumentException("Gradient array cannot be empty.");
        if (delta <= 0) return values[0];
        if (delta >= 1) return values[values.length - 1];

        int index = (int) (delta * (values.length - 1));
        float t = delta * (values.length - 1) - index;
        return values[index] * (1 - t) + values[index + 1] * t;
    }

    private static final int[] cloudLayerTexOffset = {0, 2, 1, 0};
    public static ResourceLocation getCloudTexture(float forecast, int layer) {
        float f = (roundN(forecast, 0.5f) / 2f) * 4f;
        return CLOUD_TEXTURES[(int) f + cloudLayerTexOffset[layer]];
    }

    public static Vector4f getCloudColor(ClientLevel level, float partialTick) {
        float timeOfDay = level.getTimeOfDay(partialTick);
        float colorFactor = Mth.cos(timeOfDay * ((float) Math.PI * 2F)) * 2F + 0.5F;
        colorFactor = Mth.clamp(colorFactor, 0F, 1F);

        float r = 1F;
        float g = 1F;
        float b = 1F;
        float rainLevel = level.getRainLevel(partialTick);
        if (rainLevel > 0F) {
            float modulator = (r * 0.3F + g * 0.59F + b * 0.11F) * 0.6F;
            float rainFactor = (1F - rainLevel * 0.95F) + (modulator * rainLevel * 0.95F);
            r *= rainFactor;
            g *= rainFactor;
            b *= rainFactor;
        }

        r *= colorFactor * 0.9F + 0.1F;
        g *= colorFactor * 0.9F + 0.1F;
        b *= colorFactor * 0.85F + 0.15F;
        float thunderLevel = level.getThunderLevel(partialTick);
        if (thunderLevel > 0F) {
            float modulator = (r * 0.3F + g * 0.59F + b * 0.11F) * 0.6F;
            float thunderFactor = (1F - thunderLevel * 0.95F) + (modulator * thunderLevel * 0.95F);

            r *= thunderFactor;
            g *= thunderFactor;
            b *= thunderFactor;
        }

        return new Vector4f(r, g, b, 0.8F);
    }

    @EventBusSubscriber(modid = Barometry.MOD_ID)
    public static class ResourcePackHandler {
        @SubscribeEvent
        public static void onAddPackFinders(AddPackFindersEvent event) {
            if (event.getPackType() != PackType.CLIENT_RESOURCES) {
                return;
            }
            event.addPackFinders(
                    ResourceLocation.fromNamespaceAndPath(Barometry.MOD_ID, "shader_patch"),
                    PackType.CLIENT_RESOURCES,
                    Component.literal("Barometry Shader Patch"),
                    PackSource.BUILT_IN,
                    false,
                    Pack.Position.TOP
            );
        }
    }
}