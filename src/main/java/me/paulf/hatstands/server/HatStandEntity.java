package me.paulf.hatstands.server;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableMap;
import me.paulf.hatstands.HatStands;
import me.paulf.hatstands.util.Mth;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntitySize;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.Pose;
import net.minecraft.entity.effect.LightningBoltEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.particles.BlockParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.HandSide;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.function.Function;

public final class HatStandEntity extends LivingEntity {
    private static final byte PUNCH_ID = 32;

    private static final DataParameter<Byte> SCALE = EntityDataManager.createKey(HatStandEntity.class, DataSerializers.BYTE);

    private long lastPunchTime;

    private final NonNullList<ItemStack> handItems;

    private final NonNullList<ItemStack> armorItems;

    private final ImmutableMap<String, Behavior> behaviors = HatStandBehaviors.create(this);

    private Behavior behavior = Behavior.ABSENT;

    public HatStandEntity(final EntityType<? extends HatStandEntity> type, final World world) {
        super(type, world);
        this.handItems = NonNullList.withSize(2, ItemStack.EMPTY);
        this.armorItems = NonNullList.withSize(4, ItemStack.EMPTY);
    }

    @Override
    protected void registerData() {
        super.registerData();
        this.dataManager.register(SCALE, (byte) Scale.NORMAL.ordinal());
    }

    void setSize(final Scale scale) {
        this.dataManager.set(SCALE, (byte) scale.ordinal());
    }

    private Scale getSize() {
        return Scale.byOrdinal(this.dataManager.get(SCALE));
    }

    @Override
    public float getRenderScale() {
        return this.getSize().scale;
    }

    @Override
    public float getStandingEyeHeight(final Pose pose, final EntitySize size) {
        return 0.6F * size.height;
    }

    @Override
    public ItemStack getPickedResult(final RayTraceResult target) {
        return new ItemStack(HatStands.Items.HAT_STAND.orElseThrow(IllegalStateException::new));
    }

    @Override
    public ITextComponent getDisplayName() {
        final ItemStack stack = this.getItemStackFromSlot(EquipmentSlotType.HEAD);
        if (!stack.isEmpty() && stack.hasDisplayName()) {
            return stack.getDisplayName();
        }
        return super.getDisplayName();
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
    public ItemStack getItemStackFromSlot(final EquipmentSlotType slot) {
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
    public void setItemStackToSlot(final EquipmentSlotType slot, final ItemStack stack) {
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
        final EquipmentSlotType slot = EquipmentSlotType.HEAD;
        if (invSlot == 100 + slot.getIndex() && (stack.isEmpty() || MobEntity.isItemStackInSlot(slot, stack))) {
            this.setItemStackToSlot(slot, stack);
            return true;
        }
        return false;
    }

    @Override
    public HandSide getPrimaryHand() {
        return HandSide.RIGHT;
    }

    @Override
    protected SoundEvent getFallSound(final int distance) {
        return HatStands.SoundEvents.ENTITY_HAT_STAND_FALL.orElseThrow(IllegalStateException::new);
    }

    @Override
    protected SoundEvent getHurtSound(final DamageSource damage) {
        return HatStands.SoundEvents.ENTITY_HAT_STAND_HIT.orElseThrow(IllegalStateException::new);
    }

    @Override
    protected SoundEvent getDeathSound() {
        return HatStands.SoundEvents.ENTITY_HAT_STAND_BREAK.orElseThrow(IllegalStateException::new);
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBox(final Entity entity) {
        return entity.canBePushed() ? entity.getBoundingBox() : null;
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
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public void setRenderYawOffset(final float value) {
        this.prevRenderYawOffset = this.renderYawOffset = value;
    }

    @Override
    public void setRotationYawHead(final float value) {
        this.prevRotationYawHead = this.rotationYawHead = value;
    }

    @Override
    public void tick() {
        super.tick();
        this.behavior.onUpdate();
    }

    @Override
    public void notifyDataManagerChange(final DataParameter<?> key) {
        super.notifyDataManagerChange(key);
        // data parameter is private so we use serializer type
        if (key.getSerializer() == DataSerializers.OPTIONAL_TEXT_COMPONENT) {
            final ITextComponent name = this.getCustomName();
            this.setBehavior(name == null ? "" : name.getString());
        }
        if (SCALE.equals(key)) {
            this.recalculateSize();
        }
    }

    private void setBehavior(final String name) {
        final Behavior behavior = this.behaviors.getOrDefault(CharMatcher.whitespace().removeFrom(name).toLowerCase(Locale.ROOT), Behavior.ABSENT);
        if (!behavior.equals(this.behavior)) {
            this.behavior.onEnd();
            this.behavior = behavior;
            this.behavior.onStart();
        }
    }

    @Override
    public boolean processInitialInteract(final PlayerEntity player, final Hand hand) {
        final ItemStack stack = player.getHeldItem(hand);
        if (this.onName(player, stack)) {
            stack.shrink(1);
            return true;
        }
        return super.processInitialInteract(player, hand);
    }

    public boolean onName(final PlayerEntity player, final ItemStack stack) {
        if (stack.hasDisplayName()) {
            this.setCustomName(stack.getDisplayName());
            this.behavior.onName(player);
            return true;
        }
        return false;
    }

    private Behavior onServer(final Function<? super HatStandEntity, Behavior> behavior) {
        return this.world.isRemote ? Behavior.ABSENT : behavior.apply(this);
    }

    private Behavior onClient(final Function<? super HatStandEntity, Behavior> behavior) {
        return this.world.isRemote ? behavior.apply(this) : Behavior.ABSENT;
    }

    @Override
    public void remove(final boolean keepData) {
        super.remove(keepData);
        this.behavior.onEnd();
    }

    void lookForward() {
        this.rotationPitch = 0.0F;
        this.rotationYawHead = this.rotationYaw;
    }

    void lookAt(final Entity target) {
        final double dx = target.posX - this.posX;
        final double dy = (target.posY + target.getEyeHeight()) - (this.posY + this.getEyeHeight());
        final double dz = target.posZ - this.posZ;
        this.rotationPitch = -Mth.toDegrees(MathHelper.atan2(dy, MathHelper.sqrt(dx * dx + dz * dz)));
        this.rotationYawHead = Mth.toDegrees(MathHelper.atan2(dz, dx)) - 90.0F;
    }

    void typeMessage(final String text) {
        this.typeMessage(new StringTextComponent(text));
    }

    void typeMessage(final ITextComponent text) {
        this.emitChat(new TranslationTextComponent("chat.type.text", this.getDisplayName(), text));
    }

    void emitChat(final ITextComponent chat) {
        for (final ServerPlayerEntity players : this.world.getEntitiesWithinAABB(ServerPlayerEntity.class, this.getBoundingBox().grow(16.0D))) {
            players.sendStatusMessage(chat, false);
        }
    }

    @Override
    public void recalculateSize() {
        final double x = this.posX;
        final double y = this.posY;
        final double z = this.posZ;
        super.recalculateSize();
        this.setPosition(x, y, z);
    }

    @Override
    public void onStruckByLightning(final LightningBoltEntity bolt) {}

    @Override
    public ActionResultType applyPlayerInteraction(final PlayerEntity player, final Vec3d vec, final Hand hand) {
        final ItemStack stack = player.getHeldItem(hand);
        if (stack.getItem() == Items.NAME_TAG) {
            return ActionResultType.PASS;
        }
        if (!this.world.isRemote && !player.isSpectator()) {
            if (stack.isEmpty() || MobEntity.getSlotForItemStack(stack) == EquipmentSlotType.HEAD) {
                this.swapHead(player, stack, hand);
            } else {
                return ActionResultType.FAIL;
            }
        }
        return ActionResultType.SUCCESS;
    }

    private void swapHead(final PlayerEntity player, final ItemStack heldStack, final Hand hand) {
        final EquipmentSlotType slot = EquipmentSlotType.HEAD;
        final ItemStack slotStack = this.getItemStackFromSlot(slot);
        if (player.abilities.isCreativeMode && slotStack.isEmpty() && !heldStack.isEmpty()) {
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
        if (this.world.isRemote || !this.isAlive() || this.isInvulnerableTo(source)) {
            return false;
        }
        if (source == DamageSource.OUT_OF_WORLD) {
            this.remove();
        } else if (source.isExplosion()) {
            this.dropContents();
            this.remove();
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
                this.remove();
            } else {
                final long time = this.world.getGameTime();
                if (time - this.lastPunchTime > 5) {
                    this.world.setEntityState(this, PUNCH_ID);
                    this.lastPunchTime = time;
                } else {
                    this.dropItem();
                    this.playParticles();
                    this.remove();
                }
            }
        }
        return false;
    }

    private boolean isPlayerDamage(final DamageSource source) {
        if ("player".equals(source.getDamageType())) {
            final Entity e = source.getTrueSource();
            return !(e instanceof PlayerEntity) || ((PlayerEntity) e).abilities.allowEdit;
        }
        return false;
    }

    private void dropItem() {
        final ItemStack stack = new ItemStack(HatStands.Items.HAT_STAND.orElseThrow(IllegalStateException::new));
        if (this.hasCustomName()) {
            stack.setDisplayName(this.getCustomName());
        }
        Block.spawnAsEntity(this.world, new BlockPos(this), stack);
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
        this.world.playSound(null, this.posX, this.posY, this.posZ, HatStands.SoundEvents.ENTITY_HAT_STAND_BREAK.orElseThrow(IllegalStateException::new), this.getSoundCategory(), 1.0F, 1.0F);
    }

    @Override
    protected float updateDistance(final float yaw, final float pitch) {
        this.prevRenderYawOffset = this.prevRotationYaw;
        this.renderYawOffset = this.rotationYaw;
        return 0.0F;
    }

    private void playParticles() {
        if (this.world instanceof ServerWorld) {
            ((ServerWorld) this.world).spawnParticle(new BlockParticleData(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.getDefaultState()), this.posX, this.posY + this.getHeight() / 1.5D, this.posZ, 6, this.getWidth() / 4.0D, this.getHeight() / 4.0D, this.getWidth() / 4.0D, 0.05D);
        }
    }

    private void damage(final float amount) {
        final float newHealth = this.getHealth() - amount;
        if (newHealth <= 0.5F) {
            this.dropContents();
            this.remove();
        } else {
            this.setHealth(newHealth);
        }
    }

    @Override
    public void handleStatusUpdate(final byte id) {
        if (id == PUNCH_ID) {
            if (this.world.isRemote) {
                this.world.playSound(this.posX, this.posY, this.posZ, HatStands.SoundEvents.ENTITY_HAT_STAND_HIT.orElseThrow(IllegalStateException::new), this.getSoundCategory(), 0.3F, 1.0F, false);
                this.lastPunchTime = this.world.getGameTime();
            }
        } else {
            super.handleStatusUpdate(id);
        }
    }

    @Override
    public void onKillCommand() {
        this.remove();
    }

    @Override
    public IPacket<?> createSpawnPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void writeAdditional(final CompoundNBT compound) {
        super.writeAdditional(compound);
        final ListNBT armorList = new ListNBT();
        for (final ItemStack stack : this.armorItems) {
            final CompoundNBT itemCompound = new CompoundNBT();
            if (!stack.isEmpty()) {
                stack.write(itemCompound);
            }
            armorList.add(itemCompound);
        }
        compound.put("ArmorItems", armorList);
        final ListNBT handList = new ListNBT();
        for (final ItemStack stack : this.handItems) {
            final CompoundNBT itemCompound = new CompoundNBT();
            if (!stack.isEmpty()) {
                stack.write(itemCompound);
            }
            handList.add(itemCompound);
        }
        compound.put("HandItems", handList);
        compound.putBoolean("Invisible", this.isInvisible());
        this.behavior.onSave(compound);
    }

    @Override
    public void readAdditional(final CompoundNBT compound) {
        super.readAdditional(compound);
        if (compound.contains("ArmorItems", Constants.NBT.TAG_LIST)) {
            final ListNBT armorList = compound.getList("ArmorItems", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < this.armorItems.size(); i++) {
                this.armorItems.set(i, ItemStack.read(armorList.getCompound(i)));
            }
        }
        if (compound.contains("HandItems", Constants.NBT.TAG_LIST)) {
            final ListNBT handList = compound.getList("HandItems", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < this.handItems.size(); i++) {
                this.handItems.set(i, ItemStack.read(handList.getCompound(i)));
            }
        }
        this.setInvisible(compound.getBoolean("Invisible"));
        this.behavior.onLoad(compound);
    }

    public static HatStandEntity create(final World world, final BlockPos pos, final float yaw) {
        final HatStandEntity hatStand = HatStands.EntityTypes.HAT_STAND.orElseThrow(IllegalStateException::new).create(world);
        if (hatStand == null) throw new NullPointerException("entity");
        hatStand.setLocationAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, yaw, 0.0F);
        hatStand.rotationYawHead = yaw;
        hatStand.renderYawOffset = yaw;
        return hatStand;
    }

    enum Scale {
        MINI(0.5F),
        NORMAL(1.0F),
        BIG(1.5F);

        static final Scale[] VALUES = Scale.values();

        final float scale;

        Scale(final float scale) {
            this.scale = scale;
        }

        static Scale byOrdinal(final int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : NORMAL;
        }
    }
}
