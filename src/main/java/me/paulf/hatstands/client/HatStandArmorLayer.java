package me.paulf.hatstands.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.GlStateManager;
import me.paulf.hatstands.server.HatStandEntity;
import net.minecraft.block.AbstractSkullBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.layers.ArmorLayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.entity.model.BipedModel;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.SkullTileEntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.IDyeableArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.tileentity.SkullTileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.common.util.Constants;
import org.apache.commons.lang3.StringUtils;

public final class HatStandArmorLayer extends LayerRenderer<HatStandEntity, HatStandModel> {
    private final BipedModel<HatStandEntity> armor = new BipedModel<>(1.0F);

    public HatStandArmorLayer(final HatStandRenderer renderer) {
        super(renderer);
    }

    @Override
    public void render(final HatStandEntity entity, final float limbSwing, final float limbSwingAmount, final float delta, final float age, final float yaw, final float pitch, final float scale) {
        final EquipmentSlotType slot = EquipmentSlotType.HEAD;
        final ItemStack stack = entity.getItemStackFromSlot(slot);
        GlStateManager.pushMatrix();
        if (stack.getItem() instanceof ArmorItem && ((ArmorItem) stack.getItem()).getEquipmentSlot() == slot) {
            GlStateManager.translatef(0.0F, 20.0F * scale, 0.0F);
            final float headScale = 7.0F / 8.0F;
            GlStateManager.scalef(headScale, headScale, headScale);
            final ArmorItem item = (ArmorItem) stack.getItem();
            BipedModel<HatStandEntity> model = this.armor;
            model = ForgeHooksClient.getArmorModel(entity, stack, slot, model);
            this.getEntityModel().setModelAttributes(model);
            model.setLivingAnimations(entity, limbSwing, limbSwingAmount, delta);
            model.setVisible(false);
            model.bipedHead.showModel = true;
            model.bipedHeadwear.showModel = true;
            this.bindTexture(getTexture(entity, item, stack, slot, ""));
            if (item.getItem() instanceof IDyeableArmorItem) {
                final int rgb = ((IDyeableArmorItem) item).getColor(stack);
                final float r = (float) (rgb >> 16 & 0xFF) / 0xFF;
                final float g = (float) (rgb >> 8 & 0xFF) / 0xFF;
                final float b = (float) (rgb & 0xFF) / 0xFF;
                GlStateManager.color3f(r, g, b);
                model.render(entity, limbSwing, limbSwingAmount, age, yaw, pitch, scale);
                this.bindTexture(getTexture(entity, item, stack, slot, "overlay"));
            }
            GlStateManager.color3f(1.0F, 1.0F, 1.0F);
            model.render(entity, limbSwing, limbSwingAmount, age, yaw, pitch, scale);
            if (stack.hasEffect()) {
                ArmorLayer.func_215338_a(this::bindTexture, entity, model, limbSwing, limbSwingAmount, delta, age, yaw, pitch, scale);
            }
        } else {
            this.getEntityModel().transformToHead();
            final float headScale = 7.0F / 8.0F;
            GlStateManager.scalef(headScale, headScale, headScale);
            GlStateManager.color3f(1.0F, 1.0F, 1.0F);
            if (stack.getItem() == Items.PLAYER_HEAD) {
                GameProfile profile = null;
                final CompoundNBT compound = stack.getTag();
                if (compound != null) {
                    if (compound.contains("SkullOwner", Constants.NBT.TAG_COMPOUND)) {
                        profile = NBTUtil.readGameProfile(compound.getCompound("SkullOwner"));
                    } else if (compound.contains("SkullOwner", Constants.NBT.TAG_STRING)) {
                        final String owner = compound.getString("SkullOwner");
                        if (!StringUtils.isBlank(owner)) {
                            profile = SkullTileEntity.updateGameProfile(new GameProfile(null, owner));
                            compound.put("SkullOwner", NBTUtil.writeGameProfile(new CompoundNBT(), profile));
                        }
                    }
                }
                final float sc = 19.0F / 16.0F;
                GlStateManager.scalef(sc, -sc, -sc);
                SkullTileEntityRenderer.instance.render(-0.5F, 0.0F, -0.5F, Direction.UP, 180.0F, ((AbstractSkullBlock) ((BlockItem) stack.getItem()).getBlock()).getSkullType(), profile, -1, limbSwing);
            } else {
                final float sc = 10.0F / 16.0F;
                GlStateManager.translatef(0.0F, -0.25F, 0.0F);
                GlStateManager.rotatef(180.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.scalef(sc, -sc, -sc);
                Minecraft.getInstance().getItemRenderer().renderItem(stack, entity, ItemCameraTransforms.TransformType.HEAD, false);
            }
        }
        GlStateManager.popMatrix();
    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }

    private static ResourceLocation getTexture(final Entity entity, final ArmorItem armor, final ItemStack stack, final EquipmentSlotType slot, final String type) {
        final ResourceLocation texture = new ResourceLocation(armor.getArmorMaterial().getName());
        final StringBuilder bob = new StringBuilder(50)
            .append(texture.getNamespace())
            .append(":textures/models/armor/")
            .append(texture.getPath())
            .append("_layer_1");
        if (!type.isEmpty()) {
            bob.append('_').append(type);
        }
        return new ResourceLocation(ForgeHooksClient.getArmorTexture(entity, stack, bob.append(".png").toString(), slot, type));
    }
}
