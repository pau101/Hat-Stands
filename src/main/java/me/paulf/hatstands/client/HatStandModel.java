package me.paulf.hatstands.client;

import me.paulf.hatstands.server.HatStandEntity;
import me.paulf.hatstands.util.Mth;
import net.minecraft.client.renderer.entity.model.EntityModel;
import net.minecraft.client.renderer.entity.model.RendererModel;

public final class HatStandModel extends EntityModel<HatStandEntity> {
    private final RendererModel plate, head, rod;

    public HatStandModel() {
        this.textureWidth = this.textureHeight = 32;
        this.head = new RendererModel(this, 0, 9);
        this.head.setRotationPoint(0.0F, -3.0F, 0.0F);
        this.head.addBox(-3.5F, -7.0F, -3.5F, 7, 7, 7, 0.0F);
        this.rod = new RendererModel(this, 0, 23);
        this.rod.addBox(-1.0F, -3.0F, -1.0F, 2, 3, 2, 0.0F);
        this.rod.addChild(this.head);
        this.plate = new RendererModel(this, 0, 0);
        this.plate.setRotationPoint(0.0F, 23.0F, 0.0F);
        this.plate.addBox(-4.0F, 0.0F, -4.0F, 8, 1, 8, 0.0F);
        this.plate.addChild(this.rod);
    }

    public void transformToHead() {
        this.plate.postRender(0.0625F);
        this.rod.postRender(0.0625F);
        this.head.postRender(0.0625F);
    }

    @Override
    public void render(final HatStandEntity entityIn, final float limbSwing, final float limbSwingAmount, final float ageInTicks, final float netHeadYaw, final float headPitch, final float scale) {
        this.plate.render(scale);
    }

    @Override
    public void setRotationAngles(final HatStandEntity entityIn, final float limbSwing, final float limbSwingAmount, final float ageInTicks, final float netHeadYaw, final float headPitch, final float scaleFactor) {
        this.head.rotateAngleX = Mth.toRadians(headPitch);
        this.head.rotateAngleY = Mth.toRadians(netHeadYaw);
    }
}
