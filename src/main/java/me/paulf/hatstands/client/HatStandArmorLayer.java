package me.paulf.hatstands.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import me.paulf.hatstands.server.HatStandEntity;
import net.minecraft.block.AbstractSkullBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Vector3f;
import net.minecraft.client.renderer.entity.layers.ArmorLayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.entity.model.BipedModel;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
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
    public void render(final MatrixStack matrix, final IRenderTypeBuffer buffers, final int packedLight, final HatStandEntity entity, final float limbSwing, final float limbSwingAmount, final float delta, final float age, final float headYaw, final float headPitch) {
        final EquipmentSlotType slot = EquipmentSlotType.HEAD;
        final ItemStack stack = entity.getItemStackFromSlot(slot);
        matrix.push();
        if (stack.getItem() instanceof ArmorItem && ((ArmorItem) stack.getItem()).getEquipmentSlot() == slot) {
            matrix.translate(0.0F, 20.0F / 16.0F, 0.0F);
            final float headScale = 7.0F / 8.0F;
            matrix.scale(headScale, headScale, headScale);
            final ArmorItem item = (ArmorItem) stack.getItem();
            BipedModel<HatStandEntity> model = this.armor;
            model = ForgeHooksClient.getArmorModel(entity, stack, slot, model);
            this.getEntityModel().copyModelAttributesTo(model);
            model.setLivingAnimations(entity, limbSwing, limbSwingAmount, delta);
            model.setVisible(false);
            model.bipedHead.showModel = true;
            model.bipedHeadwear.showModel = true;
            if (item.getItem() instanceof IDyeableArmorItem) {
                final int rgb = ((IDyeableArmorItem) item).getColor(stack);
                final float r = (float) (rgb >> 16 & 0xFF) / 0xFF;
                final float g = (float) (rgb >> 8 & 0xFF) / 0xFF;
                final float b = (float) (rgb & 0xFF) / 0xFF;
                this.renderArmor(matrix, buffers, packedLight, stack.hasEffect(), model, r, g, b, getTexture(entity, item, stack, slot, ""));
                this.renderArmor(matrix, buffers, packedLight, stack.hasEffect(), model, 1.0F, 1.0F, 1.0F, getTexture(entity, item, stack, slot, "overlay"));
            } else {
                this.renderArmor(matrix, buffers, packedLight, stack.hasEffect(), model, 1.0F, 1.0F, 1.0F, getTexture(entity, item, stack, slot, ""));
            }
        } else {
            this.getEntityModel().transformToHead(matrix);
            final float headScale = 7.0F / 8.0F;
            matrix.scale(headScale, headScale, headScale);
            if (stack.getItem() instanceof BlockItem && ((BlockItem) stack.getItem()).getBlock() instanceof AbstractSkullBlock) {
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
                matrix.scale(sc, -sc, -sc);
                matrix.translate(-0.5F, 0.0F, -0.5F);
                SkullTileEntityRenderer.render(null, 180.0F, ((AbstractSkullBlock) ((BlockItem) stack.getItem()).getBlock()).getSkullType(), profile, limbSwing, matrix, buffers, packedLight);
            } else {
                final float sc = 10.0F / 16.0F;
                matrix.translate(0.0F, -0.25F, 0.0F);
                matrix.rotate(Vector3f.YP.rotationDegrees(180.0F));
                matrix.scale(sc, -sc, -sc);
                Minecraft.getInstance().getFirstPersonRenderer().renderItemSide(entity, stack, ItemCameraTransforms.TransformType.HEAD, false, matrix, buffers, packedLight);
            }
        }
        matrix.pop();
    }

    private void renderArmor(final MatrixStack matrix, final IRenderTypeBuffer buffers, final int packedLight, final boolean glint, final BipedModel<?> model, final float r, final float g, final float b, final ResourceLocation texture) {
        final IVertexBuilder ivertexbuilder = ItemRenderer.getBuffer(buffers, RenderType.getEntityCutoutNoCull(texture), false, glint);
        model.render(matrix, ivertexbuilder, packedLight, OverlayTexture.NO_OVERLAY, r, g, b, 1.0F);
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
