package dev.ianaduarte.barometry.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ianaduarte.barometry.Barometry;
import dev.ianaduarte.barometry.ProjectionGetter;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(value = LevelRenderer.class, priority = 500)
public abstract class LevelRendererMixin {

    @Shadow @Nullable
    private ClientLevel level;

    @Shadow @Final
    private Minecraft minecraft;

    @Unique
    private double barometry$cloudPos = 0.0D;
    @Unique
    private double barometry$cloudStep = 0.03D;
    @Unique @Nullable
    private VertexBuffer barometry$cloudMesh;

    @Inject(method = "tick", at = @At("HEAD"))
    private void barometry$tickCloudOffset(CallbackInfo ci) {
        barometry$cloudStep = 0.03D * barometry$weatherSpeed();
        barometry$cloudPos += barometry$cloudStep;
    }

    @Inject(method = "setLevel", at = @At("TAIL"))
    private void barometry$resetCloudState(@Nullable ClientLevel newLevel, CallbackInfo ci) {
        barometry$cloudPos = 0.0D;
        barometry$cloudStep = 0.03D;
    }

    @Unique
    private float barometry$weatherSpeed() {
        if (level == null) {
            return 1.0F;
        }
        float forecast = level.getRainLevel(1.0F) + level.getThunderLevel(1.0F);
        return Barometry.gradient(forecast / 2.0F, 0.5F, 1.5F, 2.5F);
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void barometry$renderClouds(
            PoseStack poseStack,
            Matrix4f frustumMatrix,
            Matrix4f projectionMatrix,
            float partialTick,
            double camX,
            double camY,
            double camZ,
            CallbackInfo ci
    ) {
        ci.cancel();

        ClientLevel lvl = this.level;
        if (lvl == null || minecraft.player == null) {
            return;
        }
        if (minecraft.player.isEyeInFluid(FluidTags.WATER)) {
            return;
        }

        CloudStatus option = minecraft.options.getCloudsType();
        float cloudHeight = lvl.effects().getCloudHeight();
        if (option == CloudStatus.OFF || Float.isNaN(cloudHeight)) {
            return;
        }

        final float radius = 512.0F;

        double cloudOffset = barometry$cloudPos + barometry$cloudStep * partialTick;
        double d2 = (camX + cloudOffset) / 12.0D;
        double d3 = (double) (cloudHeight - (float) camY + 0.33F);
        double d4 = camZ / 12.0D + 0.33F;
        d2 -= (double) (Mth.floor(d2 / 2048.0D) * 2048);
        d4 -= (double) (Mth.floor(d4 / 2048.0D) * 2048);

        float fx = (float) (d2 - (double) Mth.floor(d2));
        float fz = (float) (d4 - (double) Mth.floor(d4));

        FogRenderer.levelFogColor();
        float[] fog = RenderSystem.getShaderFogColor();
        float red = fog[0];
        float green = fog[1];
        float blue = fog[2];

        final float texel = 1.0F / 256.0F;
        float uCenter = (float) Mth.floor(d2) * texel;
        float vCenter = (float) Mth.floor(d4) * texel;
        float uMin = uCenter - radius * texel;
        float uMax = uCenter + radius * texel;
        float vMin = vCenter - radius * texel;
        float vMax = vCenter + radius * texel;
        float y = (float) d3;

        BufferBuilder builder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        builder.addVertex(-radius, y, radius).setUv(uMin, vMax).setColor(red, green, blue, 0.8F).setNormal(0.0F, -1.0F, 0.0F);
        builder.addVertex(radius, y, radius).setUv(uMax, vMax).setColor(red, green, blue, 0.8F).setNormal(0.0F, -1.0F, 0.0F);
        builder.addVertex(radius, y, -radius).setUv(uMax, vMin).setColor(red, green, blue, 0.8F).setNormal(0.0F, -1.0F, 0.0F);
        builder.addVertex(-radius, y, -radius).setUv(uMin, vMin).setColor(red, green, blue, 0.8F).setNormal(0.0F, -1.0F, 0.0F);
        MeshData mesh = builder.buildOrThrow();

        if (barometry$cloudMesh == null) {
            barometry$cloudMesh = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        }
        barometry$cloudMesh.bind();
        barometry$cloudMesh.upload(mesh);
        VertexBuffer.unbind();

        poseStack.pushPose();
        poseStack.mulPose(frustumMatrix);
        poseStack.scale(12.0F, 1.0F, 12.0F);
        poseStack.translate(-fx, 0.0F, -fz);
        Matrix4f baseMatrix = poseStack.last().pose();

        float forecast = lvl.getRainLevel(partialTick) + lvl.getThunderLevel(partialTick);

        int[] layers = {3, 2, 1, 0};
        float[] heights = {12.0F, 6.0F, 0.0F, -6.0F};


        RenderType renderType = RenderType.clouds();
        renderType.setupRenderState();
        RenderSystem.disableCull();

        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) {
            renderType.clearRenderState();
            poseStack.popPose();
            return;
        }
        Matrix4f extendedProjection =
                ((ProjectionGetter) minecraft.gameRenderer)
                        .getProjectionMatrix(
                                10000.0F,
                                partialTick
                        );

        barometry$cloudMesh.bind();
        for (int i = 0; i < layers.length; i++) {
            ResourceLocation texture = Barometry.getCloudTexture(forecast, layers[i]);
            minecraft.getTextureManager().getTexture(texture).setFilter(false, false);
            RenderSystem.setShaderTexture(0, texture);

            Matrix4f layerMatrix = new Matrix4f(baseMatrix).translate(0.0F, heights[i], 0.0F);
            barometry$cloudMesh.drawWithShader(layerMatrix, extendedProjection, shader);
        }
        VertexBuffer.unbind();

        renderType.clearRenderState();

        poseStack.popPose();
    }
}
