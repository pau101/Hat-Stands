package me.paulf.hatstands.client;

import com.mojang.blaze3d.platform.GlStateManager;
import me.paulf.hatstands.HatStands;
import me.paulf.hatstands.server.HatStandEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public final class HatStandRenderer extends LivingRenderer<HatStandEntity, HatStandModel> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(HatStands.ID, "textures/entity/hat_stand.png");

    public HatStandRenderer(final EntityRendererManager mgr) {
        super(mgr, new HatStandModel(), 0);
        this.addLayer(new HatStandArmorLayer(this));
    }

    @Override
    protected void preRenderCallback(final HatStandEntity stand, final float delta) {
        super.preRenderCallback(stand, delta);
        final float s = stand.getRenderScale();
        if (s != 1.0F) {
            GlStateManager.scalef(s, s, s);
        }
    }

    @Override
    protected ResourceLocation getEntityTexture(final HatStandEntity stand) {
        return TEXTURE;
    }

    @Override
    protected boolean canRenderName(final HatStandEntity stand) {
        return stand.getAlwaysRenderNameTagForRender();
    }

    @Override
    public void renderName(final HatStandEntity entity, final double x, final double y, final double z) {
        if (Minecraft.isGuiEnabled() && this.renderManager.pointedEntity == entity) {
            final ItemStack stack = entity.getItemStackFromSlot(EquipmentSlotType.HEAD);
            if (!stack.isEmpty() && stack.hasDisplayName()) {
                this.renderLivingLabel(entity, stack.getDisplayName().getFormattedText(), x, y, z, entity.shouldRenderSneaking() ? 32 : 64);
            }
        }
    }
}
