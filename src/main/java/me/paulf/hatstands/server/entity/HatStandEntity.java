package me.paulf.hatstands.server.entity;

import io.netty.buffer.ByteBuf;
import me.paulf.hatstands.HatStands;
import me.paulf.hatstands.sound.HatStandsSounds;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;

import javax.annotation.Nullable;

public final class HatStandEntity extends EntityLivingBase implements IEntityAdditionalSpawnData {
	private static final byte PUNCH_ID = 32;

	private long lastPunchTime;

	private final NonNullList<ItemStack> handItems;

	private final NonNullList<ItemStack> armorItems;

	public HatStandEntity(final World world) {
		super(world);
		this.handItems = NonNullList.withSize(2, ItemStack.EMPTY);
		this.armorItems = NonNullList.withSize(4, ItemStack.EMPTY);
		this.setSize(0.5625F, 0.6875F);
	}

	@Override
	public float getEyeHeight() {
		return 0.4140625F;
	}

	@Override
	public ItemStack getPickedResult(final RayTraceResult target) {
		return new ItemStack(HatStands.ITEM);
	}

	@Override
	public Iterable<ItemStack> getHeldEquipment() {
		return this.handItems;
	}

	@Override
	public Iterable<ItemStack> getArmorInventoryList() {
		return this.armorItems;
	}

	@Override
	public ItemStack getItemStackFromSlot(final EntityEquipmentSlot slot) {
		switch (slot.getSlotType()) {
		case HAND:
			return this.handItems.get(slot.getIndex());
		case ARMOR:
			return this.armorItems.get(slot.getIndex());
		default:
			return ItemStack.EMPTY;
		}
	}

	@Override
	public void setItemStackToSlot(final EntityEquipmentSlot slot, final ItemStack stack) {
		switch (slot.getSlotType()) {
		case HAND:
			this.playEquipSound(stack);
			this.handItems.set(slot.getIndex(), stack);
			break;
		case ARMOR:
			this.playEquipSound(stack);
			this.armorItems.set(slot.getIndex(), stack);
		}
	}

	@Override
	public boolean replaceItemInInventory(final int invSlot, final ItemStack stack) {
		final EntityEquipmentSlot slot = EntityEquipmentSlot.HEAD;
		if (invSlot == 100 + slot.getIndex() && (stack.isEmpty() || EntityLiving.isItemStackInSlot(slot, stack))) {
			this.setItemStackToSlot(slot, stack);
			return true;
		}
		return false;
	}

	@Override
	public EnumHandSide getPrimaryHand() {
		return EnumHandSide.RIGHT;
	}

	@Override
	protected SoundEvent getFallSound(final int distance) {
		return HatStandsSounds.ENTITY_HAT_STAND_FALL;
	}

	@Override
	protected SoundEvent getHurtSound(final DamageSource damage) {
		return HatStandsSounds.ENTITY_HAT_STAND_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return HatStandsSounds.ENTITY_HAT_STAND_BREAK;
	}

	@Nullable
	@Override
	public AxisAlignedBB getCollisionBox(final Entity entity) {
		return entity.canBePushed() ? entity.getEntityBoundingBox() : null;
	}

	@Override
	public AxisAlignedBB getCollisionBoundingBox() {
		return this.getEntityBoundingBox();
	}

	@Override
	public boolean canBePushed() {
		return false;
	}

	@Override
	protected void collideWithEntity(final Entity entity) {}

	@Override
	protected void collideWithNearbyEntities() {}

	@Override
	public boolean attackable() {
		return false;
	}

	@Override
	public boolean canBeHitWithPotion() {
		return false;
	}


	@Override
	public void onStruckByLightning(final EntityLightningBolt bolt) {}

	@Override
	public EnumActionResult applyPlayerInteraction(final EntityPlayer player, final Vec3d vec, final EnumHand hand) {
		final ItemStack stack = player.getHeldItem(hand);
		if (stack.getItem() == Items.NAME_TAG) {
			return EnumActionResult.PASS;
		}
		if (!this.world.isRemote && !player.isSpectator()) {
			if (stack.isEmpty() || EntityLiving.getSlotForItemStack(stack) == EntityEquipmentSlot.HEAD) {
				this.swapHead(player, stack, hand);
			} else {
				return EnumActionResult.FAIL;
			}
		}
		return EnumActionResult.SUCCESS;
	}

	private void swapHead(final EntityPlayer player, final ItemStack heldStack, final EnumHand hand) {
		final EntityEquipmentSlot slot = EntityEquipmentSlot.HEAD;
		final ItemStack slotStack = this.getItemStackFromSlot(slot);
		if (player.capabilities.isCreativeMode && slotStack.isEmpty() && !heldStack.isEmpty()) {
			final ItemStack stack = heldStack.copy();
			stack.setCount(1);
			this.setItemStackToSlot(slot, stack);
		} else if (heldStack.getCount() > 1) {
			if (slotStack.isEmpty()) {
				final ItemStack stack = heldStack.copy();
				stack.setCount(1);
				this.setItemStackToSlot(slot, stack);
				heldStack.shrink(1);
			}
		} else {
			this.setItemStackToSlot(slot, heldStack);
			player.setHeldItem(hand, slotStack);
		}
	}

	@Override
	public boolean attackEntityFrom(final DamageSource source, final float amount) {
		if (this.world.isRemote || this.isDead || this.isEntityInvulnerable(source)) {
			return false;
		}
		if (source == DamageSource.OUT_OF_WORLD) {
			this.setDead();
		} else if (source.isExplosion()) {
			this.dropContents();
			this.setDead();
		} else if (source == DamageSource.IN_FIRE) {
			if (this.isBurning()) {
				this.damage(0.15F);
			} else {
				this.setFire(5);
			}
		} else if (source == DamageSource.ON_FIRE && this.getHealth() > 0.5F) {
			this.damage(4);
		} else if (this.isPlayerDamage(source)) {
			if (source.isCreativePlayer()) {
				this.playBreakSound();
				this.playParticles();
				this.setDead();
			} else {
				final long time = this.world.getTotalWorldTime();
				if (time - this.lastPunchTime > 5) {
					this.world.setEntityState(this, PUNCH_ID);
					this.lastPunchTime = time;
				} else {
					this.dropItem();
					this.playParticles();
					this.setDead();
				}
			}
		}
		return false;
	}

	private boolean isPlayerDamage(final DamageSource source) {
		if ("player".equals(source.getDamageType())) {
			final Entity e = source.getTrueSource();
			return !(e instanceof EntityPlayer) || ((EntityPlayer) e).capabilities.allowEdit;
		}
		return false;
	}

	private void dropItem() {
		Block.spawnAsEntity(this.world, new BlockPos(this), new ItemStack(HatStands.ITEM));
		this.dropContents();
	}

	private void dropContents() {
		this.playBreakSound();
		final BlockPos pos = new BlockPos(this);
		for (int i = 0; i < this.handItems.size(); i++) {
			final ItemStack stack = this.handItems.get(i);
			if (!stack.isEmpty()) {
				Block.spawnAsEntity(this.world, pos, stack);
				this.handItems.set(i, ItemStack.EMPTY);
			}
		}
		for (int i = 0; i < this.armorItems.size(); i++) {
			final ItemStack stack = this.armorItems.get(i);
			if (!stack.isEmpty()) {
				Block.spawnAsEntity(this.world, pos, stack);
				this.armorItems.set(i, ItemStack.EMPTY);
			}
		}
	}

	private void playBreakSound() {
		this.world.playSound(null, this.posX, this.posY, this.posZ, HatStandsSounds.ENTITY_HAT_STAND_BREAK, this.getSoundCategory(), 1.0F, 1.0F);
	}

	private void playParticles() {
		if (this.world instanceof WorldServer) {
			((WorldServer) this.world).spawnParticle(EnumParticleTypes.BLOCK_DUST, this.posX, this.posY + this.height / 1.5D, this.posZ, 6, this.width / 4.0D, this.height / 4.0D, this.width / 4.0D, 0.05D, Block.getStateId(Blocks.PLANKS.getDefaultState()));
		}
	}

	private void damage(final float amount) {
		final float newHealth = this.getHealth() - amount;
		if (newHealth <= 0.5F) {
			this.dropContents();
			this.setDead();
		} else {
			this.setHealth(newHealth);
		}
	}

	@Override
	public void handleStatusUpdate(final byte id) {
		if (id == PUNCH_ID) {
			if (this.world.isRemote) {
				this.world.playSound(this.posX, this.posY, this.posZ, HatStandsSounds.ENTITY_HAT_STAND_HIT, this.getSoundCategory(), 0.3F, 1.0F, false);
				this.lastPunchTime = this.world.getTotalWorldTime();
			}
		} else {
			super.handleStatusUpdate(id);
		}
	}

	@Override
	public void onKillCommand() {
		this.setDead();
	}

	@Override
	public void writeEntityToNBT(final NBTTagCompound compound) {
		super.writeEntityToNBT(compound);
		final NBTTagList armorList = new NBTTagList();
		for (final ItemStack stack : this.armorItems) {
			final NBTTagCompound itemCompound = new NBTTagCompound();
			if (!stack.isEmpty()) {
				stack.writeToNBT(itemCompound);
			}
			armorList.appendTag(itemCompound);
		}
		compound.setTag("ArmorItems", armorList);
		final NBTTagList handList = new NBTTagList();
		for (final ItemStack stack : this.handItems) {
			final NBTTagCompound itemCompound = new NBTTagCompound();
			if (!stack.isEmpty()) {
				stack.writeToNBT(itemCompound);
			}
			handList.appendTag(itemCompound);
		}
		compound.setTag("HandItems", handList);
		compound.setBoolean("Invisible", this.isInvisible());
	}

	@Override
	public void readEntityFromNBT(final NBTTagCompound compound) {
		super.readEntityFromNBT(compound);
		if (compound.hasKey("ArmorItems", Constants.NBT.TAG_LIST)) {
			final NBTTagList armorList = compound.getTagList("ArmorItems", Constants.NBT.TAG_COMPOUND);
			for (int i = 0; i < this.armorItems.size(); i++) {
				this.armorItems.set(i, new ItemStack(armorList.getCompoundTagAt(i)));
			}
		}
		if (compound.hasKey("HandItems", Constants.NBT.TAG_LIST)) {
			final NBTTagList handList = compound.getTagList("HandItems", Constants.NBT.TAG_COMPOUND);
			for (int i = 0; i < this.handItems.size(); i++) {
				this.handItems.set(i, new ItemStack(handList.getCompoundTagAt(i)));
			}
		}
		this.setInvisible(compound.getBoolean("Invisible"));
	}

	@Override
	public void writeSpawnData(final ByteBuf buf) {}

	@Override
	public void readSpawnData(final ByteBuf buf) {
		this.prevRenderYawOffset = this.prevRotationYawHead = this.prevRotationYaw = this.renderYawOffset = this.rotationYawHead = this.rotationYaw;
	}

	public static HatStandEntity create(final World world, final BlockPos pos, final float yaw) {
		final HatStandEntity hatStand = new HatStandEntity(world);
		hatStand.setPositionAndRotation(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, yaw, 0.0F);
		return hatStand;
	}
}
