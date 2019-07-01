package me.paulf.hatstands.client.renderer.entity;

import me.paulf.hatstands.HatStands;
import me.paulf.hatstands.client.model.entity.ModelHatStand;
import me.paulf.hatstands.server.entity.HatStandEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public final class HatStandRenderer extends RenderLivingBase<HatStandEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(HatStands.ID, "textures/entity/hat_stand.png");

    public HatStandRenderer(final RenderManager mgr) {
        super(mgr, new ModelHatStand(), 0);
        this.addLayer(new HatStandArmorLayer(this));
    }

    @Override
    public ModelHatStand getMainModel() {
        return (ModelHatStand) super.getMainModel();
    }

    @Override
    protected void preRenderCallback(final HatStandEntity stand, final float delta) {
        super.preRenderCallback(stand, delta);
        final float s = stand.getScale();
        if (s != 1.0F) {
            GlStateManager.scale(s, s, s);
        }
    }

    @Override
    protected ResourceLocation getEntityTexture(final HatStandEntity stand) {
        return TEXTURE;
    }

    @Override
    protected boolean canRenderName(final HatStandEntity stand) {
        return stand.getAlwaysRenderNameTag();
    }

    @Override
    public void renderName(final HatStandEntity entity, final double x, final double y, final double z) {
        if (Minecraft.isGuiEnabled() && this.renderManager.pointedEntity == entity) {
            final ItemStack stack = entity.getItemStackFromSlot(EntityEquipmentSlot.HEAD);
            if (!stack.isEmpty() && stack.hasDisplayName()) {
                this.renderLivingLabel(entity, stack.getDisplayName(), x, y, z, entity.isSneaking() ? 32 : 64);
            }
        }
    }
}
