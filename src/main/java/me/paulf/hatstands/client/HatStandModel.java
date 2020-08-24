package me.paulf.hatstands.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import me.paulf.hatstands.server.HatStandEntity;
import me.paulf.hatstands.util.Mth;
import net.minecraft.client.renderer.entity.model.EntityModel;
import net.minecraft.client.renderer.model.ModelRenderer;

public final class HatStandModel extends EntityModel<HatStandEntity> {
    private final ModelRenderer plate, head, rod;

    public HatStandModel() {
        this.textureWidth = this.textureHeight = 32;
        this.head = new ModelRenderer(this, 0, 9);
        this.head.setRotationPoint(0.0F, -3.0F, 0.0F);
        this.head.addBox(-3.5F, -7.0F, -3.5F, 7, 7, 7, 0.0F);
        this.rod = new ModelRenderer(this, 0, 23);
        this.rod.addBox(-1.0F, -3.0F, -1.0F, 2, 3, 2, 0.0F);
        this.rod.addChild(this.head);
        this.plate = new ModelRenderer(this, 0, 0);
        this.plate.setRotationPoint(0.0F, 23.0F, 0.0F);
        this.plate.addBox(-4.0F, 0.0F, -4.0F, 8, 1, 8, 0.0F);
        this.plate.addChild(this.rod);
    }

    public void transformToHead(final MatrixStack stack) {
        this.plate.translateRotate(stack);
        this.rod.translateRotate(stack);
        this.head.translateRotate(stack);
    }

    @Override
    public void render(final MatrixStack stack, final IVertexBuilder buf, final int packedLight, final int packedOverlay, final float red, final float green, final float blue, final float alpha) {
        this.plate.render(stack, buf, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public void setRotationAngles(final HatStandEntity entityIn, final float limbSwing, final float limbSwingAmount, final float ageInTicks, final float netHeadYaw, final float headPitch) {
        this.head.rotateAngleX = Mth.toRadians(headPitch);
        this.head.rotateAngleY = Mth.toRadians(netHeadYaw);
    }
}
