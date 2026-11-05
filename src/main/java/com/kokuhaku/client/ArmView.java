package com.kokuhaku.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** A real player arm, the vanilla model with its sleeve, drawn over a scene in place of the one you'd normally hold up. */
public final class ArmView {
    /** The arm's length in model units: 12 pixels of a 16 pixel block. */
    private static final float LENGTH = 0.75f, SHOULDER = 0.125f;
    /** Enough to see the side of the arm, so it's solid and not a flat strip. */
    private static final float TILT = 14f, ROLL_IN = 18f, DEPTH = 0.2f, Z = 200f;

    private static PlayerEntityModel<LivingEntity> wide, slim;

    private ArmView() {
    }

    private static PlayerEntityModel<LivingEntity> model(boolean thin) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (wide == null) {
            wide = new PlayerEntityModel<>(client.getEntityModelLoader().getModelPart(EntityModelLayers.PLAYER), false);
            slim = new PlayerEntityModel<>(client.getEntityModelLoader().getModelPart(EntityModelLayers.PLAYER_SLIM), true);
        }
        return thin ? slim : wide;
    }

    /**
     * Draws an arm with the tips of its fingers at a point on the screen.
     * degrees: which way the fingers point, 0 is straight up and it goes clockwise.
     * size: how long the arm is on screen. shade: 1 for its own colours, near 0 for a black shape.
     */
    public static void draw(DrawContext context, Identifier skin, boolean thin, float tipX, float tipY, float degrees, float size, float shade) {
        PlayerEntityModel<LivingEntity> model = model(thin);
        ModelPart arm = model.rightArm, sleeve = model.rightSleeve;
        arm.resetTransform();
        arm.setPivot(0f, 0f, 0f);
        arm.pitch = arm.yaw = arm.roll = 0f;
        sleeve.copyTransform(arm);
        float scale = size / LENGTH;
        MatrixStack m = context.getMatrices();
        m.push();
        m.translate(tipX, tipY, Z);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(degrees));
        m.translate(0f, size, 0f);
        m.scale(scale, scale, scale * DEPTH);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180f));
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ROLL_IN));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(TILT));
        m.translate(0f, SHOULDER, 0f);
        DiffuseLighting.enableGuiDepthLighting();
        VertexConsumer buffer = context.getVertexConsumers().getBuffer(RenderLayer.getEntityTranslucent(skin));
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        arm.render(m, buffer, light, OverlayTexture.DEFAULT_UV, shade, shade, shade, 1f);
        sleeve.render(m, buffer, light, OverlayTexture.DEFAULT_UV, shade, shade, shade, 1f);
        context.draw();
        m.pop();
    }

    /** Whether a player has the thin arms. */
    public static boolean thin(net.minecraft.client.network.AbstractClientPlayerEntity player) {
        return "slim".equals(player.getModel());
    }
}
