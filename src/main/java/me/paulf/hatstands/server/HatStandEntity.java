package me.paulf.hatstands.server;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
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
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.EggEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.network.play.server.SOpenWindowPacket;
import net.minecraft.particles.BlockParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.HandSide;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerChunkProvider;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.network.NetworkHooks;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public final class HatStandEntity extends LivingEntity {
    private static final byte PUNCH_ID = 32;

    private static final DataParameter<Byte> SCALE = EntityDataManager.createKey(HatStandEntity.class, DataSerializers.BYTE);

    private long lastPunchTime;

    private final NonNullList<ItemStack> handItems;

    private final NonNullList<ItemStack> armorItems;

    private final ImmutableMap<String, Behavior> behaviors = ImmutableMap.<String, Behavior>builder()
        .put("askalexa", this.onServer(AskAlexaBehavior::new))
        .put("allarmed", this.onServer(e -> new Behavior() {
            @Override
            public void onUpdate() {
                if (e.ticksExisted % 23 == 0) {
                    final List<MobEntity> entities = e.world.getEntitiesWithinAABB(
                        MobEntity.class,
                        e.getBoundingBox().grow(8.0D, 4.0D, 8.0D),
                        e -> e != null && e.isAlive() && !e.isOnSameTeam(e) && e.canEntityBeSeen(e)
                    );
                    if (entities.isEmpty()) {
                        e.lookForward();
                    } else {
                        final MobEntity mob = entities.get(0);
                        e.lookAt(mob);
                        final ArrowEntity arrow = new ArrowEntity(e.world, e);
                        arrow.shoot(e, e.rotationPitch, e.rotationYawHead, 0.0F, 3.0F, 1.0F);
                        arrow.setIsCritical(true);
                        e.world.addEntity(arrow);
                        e.world.playSound(
                            null,
                            e.posX, e.posY, e.posZ,
                            SoundEvents.ENTITY_ARROW_SHOOT,
                            e.getSoundCategory(),
                            1.0F,
                            1.0F / (e.rand.nextFloat() * 0.4F + 1.2F) + 0.5F
                        );
                    }
                }
            }

            @Override
            public void onEnd() {
                e.lookForward();
            }
        }))
        .put("bebigger", this.onServer(e -> new Behavior() {
            @Override
            public void onStart() {
                e.setSize(Scale.BIG);
            }

            @Override
            public void onEnd() {
                e.setSize(Scale.NORMAL);
            }
        }))
        .put("catfacts", this.onServer(e -> new Behavior() {
            int delay = -1;

            @Override
            public void onName(final PlayerEntity player) {
                e.typeMessage("Thanks for signing up for Cat Facts! You now will receive fun daily facts about CATS! >o<");
            }

            @Override
            public void onUpdate() {
                if (this.delay < 0) {
                    final int delayLower = 30 * 20;
                    final int delayUpper = 2 * 60 * 20;
                    this.delay = e.rand.nextInt(delayUpper - delayLower) + delayLower;
                } else if (this.delay-- == 0) {
                    final ImmutableList<String> facts = CatFactsHolder.CAT_FACTS;
                    e.typeMessage(facts.get(e.rand.nextInt(facts.size())));
                }
            }
        }))
        .put("cutcable", this.onServer(e -> new Behavior() {
            @Override
            public void onUpdate() {
                if (e.ticksExisted % 13 == 0) {
                    final int r = 2;
                    final BlockPos pos = new BlockPos(e).add(
                        e.rand.nextInt(1 + 2 * r) - r,
                        e.rand.nextInt(1 + 2 * r) - r,
                        e.rand.nextInt(1 + 2 * r) - r
                    );
                    if (e.world.getBlockState(pos).getBlock() == Blocks.REDSTONE_WIRE) {
                        e.world.destroyBlock(pos, true);
                    }
                }
            }
        }))
        .put("extraegg", this.onServer(e -> new Behavior() {
            @Override
            public void onUpdate() {
                if (e.ticksExisted % 151 == 0 && e.rand.nextFloat() < 0.25F) {
                    e.rotationPitch = 15.0F;
                    e.playSound(SoundEvents.ENTITY_EGG_THROW, 0.5F, 0.4F / (e.rand.nextFloat() * 0.4F + 0.8F));
                    final EggEntity egg = new EggEntity(e.world, e);
                    // egg.ignoreEntity = e; FIXME
                    egg.shoot(e, e.rotationPitch, e.rotationYaw, 0.0F, 1.5F, 1.0F);
                    e.world.addEntity(egg);
                } else if (e.ticksExisted % 13 == 5) {
                    e.rotationPitch = 0.0F;
                }
            }
        }))
        .put("freefish", this.onServer(FreeFishBehavior::new))
        .put("hasheart", this.onClient(e -> new Behavior() {
            @Override
            public void onUpdate() {
                if (e.ticksExisted % 5 == 0 && e.rand.nextFloat() < 0.4F) {
                    e.world.addParticle(
                        ParticleTypes.HEART,
                        e.posX + e.rand.nextFloat() * e.getWidth() * 2.0F - e.getWidth(),
                        e.posY + 0.5D + e.rand.nextFloat() * e.getHeight(),
                        e.posZ + e.rand.nextFloat() * e.getWidth() * 2.0F - e.getWidth(),
                        e.rand.nextGaussian() * 0.02D,
                        e.rand.nextGaussian() * 0.02D,
                        e.rand.nextGaussian() * 0.02D
                    );
                }
            }
        }))
        .put("hithuman", this.onServer(e -> new Behavior() {
            @Override
            public void onUpdate() {
                if (e.ticksExisted % 7 == 0 && e.rand.nextFloat() < 0.333F) {
                    final DamageSource damage = DamageSource.causeMobDamage(e);
                    for (final ServerPlayerEntity player : e.world.getEntitiesWithinAABB(ServerPlayerEntity.class, e.getBoundingBox().grow(1.0D))) {
                        player.attackEntityFrom(damage, 1.0F);
                    }
                }
            }
        }))
        .put("moremini", this.onServer(e -> new Behavior() {
            @Override
            public void onStart() {
                e.setSize(Scale.MINI);
            }

            @Override
            public void onEnd() {
                e.setSize(Scale.NORMAL);
            }
        }))
        .put("sexysong", this.onServer(e -> new Behavior() {
            boolean powered = false;
            BlockPos pos = BlockPos.ZERO;

            void play() {
                e.playSound(HatStands.SoundEvents.ENTITY_HAT_STAND_SEXYSONG.orElseThrow(IllegalStateException::new), 1.0F, 1.0F);
            }

            @Override
            public void onName(final PlayerEntity player) {
                this.play();
            }

            @Override
            public void onStart() {
                MinecraftForge.EVENT_BUS.register(this);
            }

            @Override
            public void onEnd() {
                MinecraftForge.EVENT_BUS.unregister(this);
            }

            @SubscribeEvent(priority = EventPriority.LOW)
            public void onNeighborNotify(final BlockEvent.NeighborNotifyEvent event) {
                if (event.getState().isNormalCube(event.getWorld(), event.getPos())) {
                    final World world = (World) event.getWorld();
                    final BlockPos pos = event.getPos();
                    // new BlockPos(e).equals(pos.up())
                    if (e.posX >= pos.getX() && e.posX < pos.getX() + 1.0D &&
                        e.posZ >= pos.getZ() && e.posZ < pos.getZ() + 1.0D &&
                        e.posY >= pos.getY() + 1.0D && e.posY < pos.getY() + 2.0D) {
                        final boolean powered = world.isBlockPowered(pos);
                        if (powered && (!this.powered || !this.pos.equals(pos))) {
                            this.play();
                        }
                        this.powered = powered;
                        this.pos = pos.toImmutable();
                    }
                }
            }

            @Override
            public void onSave(final CompoundNBT compound) {
                compound.putBoolean("Powered", this.powered);
                compound.putInt("PoweredX", this.pos.getX());
                compound.putInt("PoweredY", this.pos.getY());
                compound.putInt("PoweredZ", this.pos.getZ());
            }

            @Override
            public void onLoad(final CompoundNBT compound) {
                this.powered = compound.getBoolean("Powered");
                this.pos = new BlockPos(
                    compound.getInt("PoweredX"),
                    compound.getInt("PoweredY"),
                    compound.getInt("PoweredZ")
                );
            }
        }))
        .put("smallspy", this.onClient(e -> new Behavior() {
            @Override
            public void onUpdate() {
                @Nullable final PlayerEntity player = e.world.getClosestPlayer(e, 8.0D);
                if (player == null) {
                    e.lookForward();
                } else {
                    e.lookAt(player);
                }
            }

            @Override
            public void onEnd() {
                e.lookForward();
            }
        }))
        .put("pwnpeeps", this.onServer(e -> new Behavior() {
            @Override
            public void onName(final PlayerEntity player) {
                final MinecraftServer server = e.world.getServer();
                if (server != null && server.getPlayerList().canSendCommands(player.getGameProfile())) {
                    final ServerChunkProvider tracker = ((ServerWorld) e.world).getChunkProvider();
                    //noinspection ConstantConditions
                    tracker.sendToAllTracking(e, new SOpenWindowPacket(1, ContainerType.ENCHANTMENT, null));
                }
            }
        }))
        .build();

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

    private void setSize(final Scale scale) {
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
        return 0.65F * size.height;
    }

    @Override
    public ItemStack getPickedResult(final RayTraceResult target) {
        return new ItemStack(HatStands.Items.HAT_STAND.orElseThrow(IllegalStateException::new));
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

    private static final class CatFactsHolder {
        private static final ImmutableList<String> CAT_FACTS = readCatFacts();

        private static ImmutableList<String> readCatFacts() {
            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(MinecraftServer.class.getResourceAsStream("/assets/" + HatStands.ID + "/texts/catfacts.txt")))) {
                return reader.lines().collect(ImmutableList.toImmutableList());
            } catch (final IOException e) {
                return ImmutableList.of("Cat facts are surprisingly difficult to find.");
            }
        }
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
