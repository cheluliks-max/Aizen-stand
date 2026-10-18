package com.aizenstand.client;

import com.aizenstand.AizenStandEntity;
import com.aizenstand.AizenStandMod;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public class AizenStandRenderer extends EntityRenderer<AizenStandEntity> {
    private static final Identifier TEXTURE = Identifier.of(AizenStandMod.MOD_ID, "textures/entity/aizen_stand.png");
    private static final float SCALE = 1.15F;

    private final AizenStandModel model;

    public AizenStandRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.model = new AizenStandModel(context.getPart(AizenStandModel.LAYER));
        this.shadowRadius = 0.0F;
    }

    @Override
    public Identifier getTexture(AizenStandEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(AizenStandEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevYaw, entity.getYaw());
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - bodyYaw));
        matrices.scale(-1.0F, -1.0F, 1.0F);
        matrices.scale(SCALE, SCALE, SCALE);
        matrices.translate(0.0F, -1.501F, 0.0F);

        model.setAngles(entity, 0.0F, 0.0F, entity.age + tickDelta, 0.0F, 0.0F);
        // slightly see-through and always fully lit, so it looks like a spiritual being
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
        model.render(matrices, buffer, 0xF000F0, OverlayTexture.DEFAULT_UV, ColorHelper.Argb.getArgb(225, 255, 255, 255));
        matrices.pop();
    }
}
