package me.paulf.hatstands.client.renderer.entity;

import com.mojang.authlib.GameProfile;
import me.paulf.hatstands.client.model.entity.ModelHatStandArmor;
import me.paulf.hatstands.server.entity.HatStandEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.tileentity.TileEntitySkullRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.tileentity.TileEntitySkull;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.common.util.Constants;
import org.apache.commons.lang3.StringUtils;

public final class HatStandArmorLayer implements LayerRenderer<HatStandEntity> {
	private final HatStandRenderer renderer;

	private final ModelHatStandArmor armor = new ModelHatStandArmor(1.0F);

	public HatStandArmorLayer(final HatStandRenderer renderer) {
		this.renderer = renderer;
	}

	@Override
	public void doRenderLayer(final HatStandEntity entity, final float limbSwing, final float limbSwingAmount, final float delta, final float age, final float yaw, final float pitch, final float scale) {
		final EntityEquipmentSlot slot = EntityEquipmentSlot.HEAD;
		final ItemStack stack = entity.getItemStackFromSlot(slot);
		GlStateManager.pushMatrix();
		if (stack.getItem() instanceof ItemArmor && ((ItemArmor) stack.getItem()).getEquipmentSlot() == slot) {
			GlStateManager.translate(0.0F, 20.0F * scale, 0.0F);
			final float headScale = 7.0F / 8.0F;
			GlStateManager.scale(headScale, headScale, headScale);
			final ItemArmor item = (ItemArmor) stack.getItem();
			ModelBiped model = this.armor;
			model = ForgeHooksClient.getArmorModel(entity, stack, slot, model);
			model.setModelAttributes(this.renderer.getMainModel());
			model.setLivingAnimations(entity, limbSwing, limbSwingAmount, delta);
			model.setVisible(false);
			model.bipedHead.showModel = true;
			model.bipedHeadwear.showModel = true;
			this.renderer.bindTexture(getTexture(entity, item, stack, slot, ""));
			if (item.hasOverlay(stack)) {
				final int rgb = item.getColor(stack);
				final float r = (float) (rgb >> 16 & 0xFF) / 0xFF;
				final float g = (float) (rgb >> 8 & 0xFF) / 0xFF;
				final float b = (float) (rgb & 0xFF) / 0xFF;
				GlStateManager.color(r, g, b);
				model.render(entity, limbSwing, limbSwingAmount, age, yaw, pitch, scale);
				this.renderer.bindTexture(getTexture(entity, item, stack, slot, "overlay"));
			}
			GlStateManager.color(1.0F, 1.0F, 1.0F);
			model.render(entity, limbSwing, limbSwingAmount, age, yaw, pitch, scale);
			if (stack.hasEffect()) {
				LayerBipedArmor.renderEnchantedGlint(this.renderer, entity, model, limbSwing, limbSwingAmount, delta, age, yaw, pitch, scale);
			}
		} else {
			this.renderer.getMainModel().transformToHead();
			final float headScale = 7.0F / 8.0F;
			GlStateManager.scale(headScale, headScale, headScale);
			GlStateManager.color(1.0F, 1.0F, 1.0F);
			if (stack.getItem() == Items.SKULL) {
				GameProfile profile = null;
				final NBTTagCompound compound = stack.getTagCompound();
				if (compound != null) {
					if (compound.hasKey("SkullOwner", Constants.NBT.TAG_COMPOUND)) {
						profile = NBTUtil.readGameProfileFromNBT(compound.getCompoundTag("SkullOwner"));
					} else if (compound.hasKey("SkullOwner", Constants.NBT.TAG_STRING)) {
						final String owner = compound.getString("SkullOwner");
						if (!StringUtils.isBlank(owner)) {
							profile = TileEntitySkull.updateGameProfile(new GameProfile(null, owner));
							compound.setTag("SkullOwner", NBTUtil.writeGameProfile(new NBTTagCompound(), profile));
						}
					}
				}
				final float sc = 19.0F / 16.0F;
				GlStateManager.scale(sc, -sc, -sc);
				TileEntitySkullRenderer.instance.renderSkull(-0.5F, 0.0F, -0.5F, EnumFacing.UP, 180.0F, stack.getMetadata(), profile, -1, limbSwing);
			} else {
				final float sc = 10.0F / 16.0F;
				GlStateManager.translate(0.0F, -0.25F, 0.0F);
				GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
				GlStateManager.scale(sc, -sc, -sc);
				Minecraft.getMinecraft().getItemRenderer().renderItem(entity, stack, ItemCameraTransforms.TransformType.HEAD);
			}
		}
		GlStateManager.popMatrix();
	}

	@Override
	public boolean shouldCombineTextures() {
		return false;
	}

	private static ResourceLocation getTexture(final Entity entity, final ItemArmor armor, final ItemStack stack, final EntityEquipmentSlot slot, final String type) {
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
