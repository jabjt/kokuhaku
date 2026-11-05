package com.kokuhaku.client;

import com.kokuhaku.KokuhakuClient;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Glasses worn on the head, kept apart from the skins so they can sit over the
 * face instead of being painted on it. Drawn in the head's own space, where the
 * face is at z -4 and the hair over it reaches -4.5, and the eyes are y -3 to -1.
 */
public final class GlassesModel {
    private static final Identifier FRAME = new Identifier(KokuhakuClient.MOD_ID, "textures/entity/glasses_frame.png");
    private static final Identifier LENS = new Identifier(KokuhakuClient.MOD_ID, "textures/entity/glasses_lens.png");
    private static final float FRONT = -5.0f, DEPTH = 0.35f, BAR = 0.35f;
    /** Each lens, from the nose outwards, and from its top to its bottom. */
    private static final float INNER = 0.5f, OUTER = 3.5f, TOP = -3.5f, BOTTOM = -0.5f;
    private static final float ARM_Y = -2.85f, SIDE = 4.55f, ARM_LENGTH = 6f;

    private final ModelPart frame, lenses;

    public GlassesModel() {
        ModelData data = new ModelData();
        ModelPartBuilder rims = ModelPartBuilder.create().uv(0, 0);
        ModelPartBuilder glass = ModelPartBuilder.create().uv(0, 0);
        float width = OUTER - INNER, height = BOTTOM - TOP;
        for (float side : new float[]{-1f, 1f}) {
            float left = side < 0 ? -OUTER : INNER;
            rims.cuboid(left, TOP, FRONT, width, BAR, DEPTH);
            rims.cuboid(left, BOTTOM - BAR, FRONT, width, BAR, DEPTH);
            rims.cuboid(left, TOP + BAR, FRONT, BAR, height - BAR * 2, DEPTH);
            rims.cuboid(left + width - BAR, TOP + BAR, FRONT, BAR, height - BAR * 2, DEPTH);
            // Round the corner of the head and back along the side of it.
            float hinge = side < 0 ? -SIDE - BAR : OUTER;
            rims.cuboid(hinge, ARM_Y, FRONT, SIDE + BAR - OUTER, BAR, DEPTH);
            rims.cuboid(side < 0 ? -SIDE - BAR : SIDE, ARM_Y, FRONT, BAR, BAR, ARM_LENGTH);
            glass.cuboid(left + BAR, TOP + BAR, FRONT + 0.1f, width - BAR * 2, height - BAR * 2, 0.05f);
        }
        rims.cuboid(-INNER, -2.6f, FRONT, INNER * 2, BAR, DEPTH); // the bridge
        data.getRoot().addChild("frame", rims, ModelTransform.NONE);
        data.getRoot().addChild("lenses", glass, ModelTransform.NONE);
        ModelPart root = TexturedModelData.of(data, 16, 16).createModel();
        frame = root.getChild("frame");
        lenses = root.getChild("lenses");
    }

    /** Call with the matrices already in the head's space. */
    public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, float r, float g, float b) {
        frame.render(matrices, consumers.getBuffer(RenderLayer.getEntityTranslucent(FRAME)), light, OverlayTexture.DEFAULT_UV, r, g, b, 1f);
        lenses.render(matrices, consumers.getBuffer(RenderLayer.getEntityTranslucent(LENS)), light, OverlayTexture.DEFAULT_UV, r, g, b, 1f);
    }
}
