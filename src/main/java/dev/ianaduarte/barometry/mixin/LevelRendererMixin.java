package dev.ianaduarte.barometry.mixin;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.ianaduarte.barometry.Barometry;
import dev.ianaduarte.barometry.ProjectionGetter;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("DataFlowIssue")
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow
    @Nullable
    private ClientLevel level;
    @Shadow
    @Nullable
    private VertexBuffer cloudBuffer;
    @Shadow
    @Final
    private Minecraft minecraft;
    @Unique
    private double cloudOffsetPrev;
    @Unique
    private double cloudOffset;

    @Unique
    private void setupCloudShader(ShaderInstance shader, Vector4f color, float partialTick) {
        Uniform cloudColor = shader.getUniform("cloudColor");
        if (cloudColor != null) {
            cloudColor.set(color.x, color.y, color.z, color.w);
        }
        Uniform fogColor = shader.getUniform("FogColor");
        if (fogColor != null) {
            Vec3 sky = level.getSkyColor(minecraft.gameRenderer.getMainCamera().getPosition(), partialTick);
            fogColor.set((float) sky.x, (float) sky.y, (float) sky.z, 1F);
        }
        Uniform fogStart = shader.getUniform("FogStart");
        if (fogStart != null) {
            int chunks = minecraft.options.renderDistance().get();
            float radius = 256.0F * 12.0F;
            float renderRadius = Math.min(radius, chunks * 16.0F);
            fogStart.set(renderRadius * 0.10F);
        }
        Uniform fogEnd = shader.getUniform("FogEnd");
        if (fogEnd != null) {
            int chunks = minecraft.options.renderDistance().get();
            float radius = 256.0F * 12.0F;
            float renderRadius = Math.min(radius, chunks * 16.0F);
            fogEnd.set(renderRadius * 0.45F);
        }
        Uniform sunDirection = shader.getUniform("sunDirection");
        if (sunDirection != null && level != null) {
            float angle = level.getSunAngle(partialTick);
            sunDirection.set(Mth.sin(angle), 0.0F, Mth.cos(angle));
        }
    }

    @Unique
    private void renderCloudLayer(PoseStack poseStack, Matrix4f projectionMatrix, ShaderInstance shader, int layer, float forecast, Vector4f color, float uvX, float uvZ, float height, float partialTick) {
        ResourceLocation texture = Barometry.getCloudTexture(forecast, layer);
        Minecraft.getInstance().getTextureManager().getTexture(texture).setFilter(false, false);
        RenderSystem.setShaderTexture(0, texture);
        Uniform uvOffset = shader.getUniform("uvOffset");
        if (uvOffset != null) {
            uvOffset.set((uvX % 256F) / 256F, (uvZ % 256F) / 256F);
        }
        setupCloudShader(shader, color, partialTick);
        poseStack.pushPose();
        poseStack.translate(0, height, 0);
        if (cloudBuffer == null) {
            poseStack.popPose();
            return;
        }
        cloudBuffer.bind();
        cloudBuffer.drawWithShader(poseStack.last().pose(), projectionMatrix, shader);
        poseStack.popPose();
    }

    @Overwrite
    public void renderClouds(PoseStack poseStack, Matrix4f frustumMatrix, Matrix4f projectionMatrix, float partialTick, double camX, double camY, double camZ) {
        if (level == null) return;
        float cloudHeight = level.effects().getCloudHeight();
        if (Float.isNaN(cloudHeight)) return;
        Camera camera = this.minecraft.gameRenderer.getMainCamera();
        FogRenderer.setupColor(camera, partialTick, this.level, this.minecraft.options.renderDistance().get(), 0.0F);
        if (cloudBuffer == null) {
            cloudBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            cloudBuffer.bind();
            cloudBuffer.upload(buildClouds(Tesselator.getInstance()));
            VertexBuffer.unbind();
        }
        poseStack.pushPose();
        poseStack.mulPose(frustumMatrix);
        poseStack.scale(12F, 1F, 12F);
        poseStack.translate(0, cloudHeight - camY, 0);
        RenderType renderType = RenderType.clouds();
        renderType.setupRenderState();
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) {
            renderType.clearRenderState();
            poseStack.popPose();
            return;
        }
        float cloudDistance = 10000F;
        Matrix4f farPlane = ((ProjectionGetter) minecraft.gameRenderer).getProjectionMatrix(cloudDistance, partialTick);
        Vector4f color = Barometry.getCloudColor(level, partialTick);
        float forecast = level.getRainLevel(partialTick) + level.getThunderLevel(partialTick);
        float wind = (float) (cloudOffsetPrev + (cloudOffset - cloudOffsetPrev) * partialTick);
        float speedX = (float)(camX / 12D + wind * 0.01F);
        float speedZ = (float)(camZ / 12D);
        float darkness = 1F - forecast * 0.25F;
        color.mul(darkness, darkness, darkness, 1F);
        renderCloudLayer(poseStack, farPlane, shader, 3, forecast, color, speedX, speedZ, 12, partialTick);
        renderCloudLayer(poseStack, farPlane, shader, 2, forecast, color, speedX, speedZ, 6, partialTick);
        renderCloudLayer(poseStack, farPlane, shader, 1, forecast, color, speedX, speedZ, 0, partialTick);
        renderCloudLayer(poseStack, farPlane, shader, 0, forecast, color, speedX, speedZ, -6, partialTick);
        renderType.clearRenderState();
        VertexBuffer.unbind();
        poseStack.popPose();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void updateClouds(CallbackInfo ci) {
        if (level == null) return;
        float forecast = level.getRainLevel(1) + level.getThunderLevel(1);
        float speed = Barometry.gradient(forecast / 2F, 0.5F, 1.5F, 2.5F);
        cloudOffsetPrev = cloudOffset;
        cloudOffset += speed * 0.065F;
    }

    @Unique
    private MeshData buildClouds(Tesselator tesselator) {
        BufferBuilder builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        final int GRID = 128;
        final float SIZE = 512.0F;
        final float HALF = SIZE / 2.0F;
        final float STEP = SIZE / GRID;
        for (int x = 0; x < GRID; x++) {
            float x0 = -HALF + x * STEP;
            float x1 = x0 + STEP;
            float u0 = x0 / HALF;
            float u1 = x1 / HALF;
            for (int z = 0; z < GRID; z++) {
                float z0 = -HALF + z * STEP;
                float z1 = z0 + STEP;
                float v0 = z0 / HALF;
                float v1 = z1 / HALF;
                builder.addVertex(x0, 0, z1).setUv(u0, v1).setColor(255, 255, 255, 255).setNormal(0, -1, 0);
                builder.addVertex(x1, 0, z1).setUv(u1, v1).setColor(255, 255, 255, 255).setNormal(0, -1, 0);
                builder.addVertex(x1, 0, z0).setUv(u1, v0).setColor(255, 255, 255, 255).setNormal(0, -1, 0);
                builder.addVertex(x0, 0, z0).setUv(u0, v0).setColor(255, 255, 255, 255).setNormal(0, -1, 0);
            }
        }
        return builder.build();
    }
}
