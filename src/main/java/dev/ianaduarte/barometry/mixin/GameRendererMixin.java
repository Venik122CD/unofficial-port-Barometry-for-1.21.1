package dev.ianaduarte.barometry.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ianaduarte.barometry.ProjectionGetter;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@SuppressWarnings("DataFlowIssue")
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin implements ProjectionGetter {
    @Shadow protected abstract double getFov(Camera activeRenderInfo, float partialTicks, boolean useFOVSetting);
    @Shadow @Final private Camera mainCamera;

    @Shadow private float zoom;

    @Shadow private float zoomX;

    @Shadow private float zoomY;

    @Shadow @Final Minecraft minecraft;

    @Shadow protected abstract void bobHurt(PoseStack poseStack, float partialTicks);

    @Shadow protected abstract void bobView(PoseStack poseStack, float partialTicks);

    @Shadow private int confusionAnimationTick;

    public Matrix4f getProjectionMatrix(float farPlane, float partialTicks) {
        double fov = this.getFov(this.mainCamera, partialTicks, true);
        Matrix4f matrix4f = new Matrix4f();
        if(this.zoom != 1.0F) {
            matrix4f.translate(this.zoomX, -this.zoomY, 0.0F);
            matrix4f.scale(this.zoom, this.zoom, 1.0F);
        }

        matrix4f.perspective(
                (float)(fov * (float) (Math.PI / 180.0)),
                (float)this.minecraft.getWindow().getWidth() / (float)this.minecraft.getWindow().getHeight(),
                0.05F,
                farPlane
        );

        PoseStack poseStack = new PoseStack();
        this.bobHurt(poseStack, this.mainCamera.getPartialTickTime());
        if(this.minecraft.options.bobView().get()) {
            this.bobView(poseStack, this.mainCamera.getPartialTickTime());
        }

        matrix4f.mul(poseStack.last().pose());
        float h = this.minecraft.options.screenEffectScale().get().floatValue();
        float i = Mth.lerp(partialTicks, this.minecraft.player.oSpinningEffectIntensity, this.minecraft.player.spinningEffectIntensity) * h * h;
        if (i > 0.0F) {
            int j = this.minecraft.player.hasEffect(MobEffects.CONFUSION) ? 7 : 20;
            float k = 5.0F / (i * i + 5.0F) - i * 0.04F;
            k *= k;
            Vector3f vector3f = new Vector3f(0.0F, Mth.SQRT_OF_TWO / 2.0F, Mth.SQRT_OF_TWO / 2.0F);
            float l = ((float)this.confusionAnimationTick + partialTicks) * (float)j * (float) (Math.PI / 180.0);
            matrix4f.rotate(l, vector3f);
            matrix4f.scale(1.0F / k, 1.0F, 1.0F);
            matrix4f.rotate(-l, vector3f);
        }
        return matrix4f;
    }
}