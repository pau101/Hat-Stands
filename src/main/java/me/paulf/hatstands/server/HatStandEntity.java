package me.paulf.hatstands.server;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.netty.buffer.ByteBuf;
import me.paulf.hatstands.HatStands;
import me.paulf.hatstands.util.Mth;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityTracker;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityEgg;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.network.play.server.SPacketOpenWindow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public final class HatStandEntity extends EntityLivingBase implements IEntityAdditionalSpawnData {
    private static final byte PUNCH_ID = 32;

    private static final float WIDTH = 9.0F / 16.0F;

    private static final float HEIGHT = 11.0F / 16.0F;

    private long lastPunchTime;

    private final NonNullList<ItemStack> handItems;

    private final NonNullList<ItemStack> armorItems;

    private final ImmutableMap<String, Behavior> behaviors = ImmutableMap.<String, Behavior>builder()
        .put("askalexa", this.onServer(AskAlexaBehavior::new))
        .put("allarmed", this.onServer(e -> new Behavior() {
            @Override
            public void onUpdate() {
                if (e.ticksExisted % 23 == 0) {
                    final List<EntityMob> entities = e.world.getEntitiesWithinAABB(
                        EntityMob.class,
                        e.getEntityBoundingBox().grow(8.0D, 4.0D, 8.0D),
                        e -> e != null && e.isEntityAlive() && !e.isOnSameTeam(e) && e.canEntityBeSeen(e)
                    );
                    if (entities.isEmpty()) {
                        e.lookForward();
                    } else {
                        final EntityMob mob = entities.get(0);
                        e.lookAt(mob);
                        final EntityTippedArrow arrow = new EntityTippedArrow(e.world, e);
                        arrow.shoot(e, e.rotationPitch, e.rotationYawHead, 0.0F, 3.0F, 1.0F);
                        arrow.setIsCritical(true);
                        e.world.spawnEntity(arrow);
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
        .put("bebigger", new Behavior() {
            @Override
            public void onStart() {
                HatStandEntity.this.setScale(1.5F);
            }

            @Override
            public void onEnd() {
                HatStandEntity.this.resetSize();
            }
        })
        .put("catfacts", this.onServer(e -> new Behavior() {
            int delay = -1;

            @Override
            public void onName(final EntityPlayer player) {
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
                    final EntityEgg egg = new EntityEgg(e.world, e);
                    egg.ignoreEntity = e;
                    egg.shoot(e, e.rotationPitch, e.rotationYaw, 0.0F, 1.5F, 1.0F);
                    e.world.spawnEntity(egg);
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
                    e.world.spawnParticle(
                        EnumParticleTypes.HEART,
                        e.posX + e.rand.nextFloat() * e.width * 2.0F - e.width,
                        e.posY + 0.5D + e.rand.nextFloat() * e.height,
                        e.posZ + e.rand.nextFloat() * e.width * 2.0F - e.width,
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
                    for (final EntityPlayerMP player : e.world.getEntitiesWithinAABB(EntityPlayerMP.class, e.getEntityBoundingBox().grow(1.0D))) {
                        player.attackEntityFrom(damage, 1.0F);
                    }
                }
            }
        }))
        .put("moremini", new Behavior() {
            @Override
            public void onStart() {
                HatStandEntity.this.setScale(0.5F);
            }

            @Override
            public void onEnd() {
                HatStandEntity.this.resetSize();
            }
        })
        .put("sexysong", this.onServer(e -> new Behavior() {
            boolean powered = false;
            BlockPos pos = BlockPos.ORIGIN;

            void play() {
                e.playSound(HatStands.SoundEvents.ENTITY_HAT_STAND_SEXYSONG, 1.0F, 1.0F);
            }

            @Override
            public void onName(final EntityPlayer player) {
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
                if (event.getState().isNormalCube()) {
                    final World world = event.getWorld();
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
            public void onSave(final NBTTagCompound compound) {
                compound.setBoolean("Powered", this.powered);
                compound.setInteger("PoweredX", this.pos.getX());
                compound.setInteger("PoweredY", this.pos.getY());
                compound.setInteger("PoweredZ", this.pos.getZ());
            }

            @Override
            public void onLoad(final NBTTagCompound compound) {
                this.powered = compound.getBoolean("Powered");
                this.pos = new BlockPos(
                    compound.getInteger("PoweredX"),
                    compound.getInteger("PoweredY"),
                    compound.getInteger("PoweredZ")
                );
            }
        }))
        .put("smallspy", this.onClient(e -> new Behavior() {
            @Override
            public void onUpdate() {
                final @Nullable EntityPlayer player = e.world.getClosestPlayerToEntity(e, 8.0D);
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
            public void onName(final EntityPlayer player) {
                final MinecraftServer server = e.world.getMinecraftServer();
                if (server != null && server.getPlayerList().canSendCommands(player.getGameProfile())) {
                    final EntityTracker tracker = ((WorldServer) e.world).getEntityTracker();
                    //noinspection ConstantConditions
                    tracker.sendToTracking(e, new SPacketOpenWindow(1, "minecraft:enchanting_table", null));
                }
            }
        }))
        .put("powerpwn", this.onServer(e -> new Behavior() {
            @Override
            public void onName(final EntityPlayer player) {
                final MinecraftServer server = e.world.getMinecraftServer();
                if (server != null && server.getPlayerList().canSendCommands(player.getGameProfile())) {
                    final EntityTracker tracker = ((WorldServer) e.world).getEntityTracker();
                    final ChunkPrimer primer = new ChunkPrimer();
                    final BlockPos b = new BlockPos(e);
                    primer.setBlockState(b.getX() & 0xF, b.getY(), b.getZ() & 0xF, Blocks.WATER.getDefaultState());
                    primer.setBlockState(b.getX() & 0xF, b.getY(), MathHelper.abs((b.getZ() & 0xF) - 1), Blocks.RED_SHULKER_BOX.getDefaultState());
                    primer.setBlockState(MathHelper.abs((b.getX() & 0xF) - 1), b.getY(), b.getZ() & 0xF, Blocks.RED_SHULKER_BOX.getDefaultState());
                    tracker.sendToTracking(e, new SPacketChunkData(new Chunk(e.world, primer, b.getX() >> 4, b.getZ() >> 4), 0xFFFF));
                }
            }
        }))
        .build();

    private Behavior behavior = Behavior.ABSENT;

    public HatStandEntity(final World world) {
        super(world);
        this.handItems = NonNullList.withSize(2, ItemStack.EMPTY);
        this.armorItems = NonNullList.withSize(4, ItemStack.EMPTY);
        this.setSize(WIDTH, HEIGHT);
    }

    public float getScale() {
        return this.height / HEIGHT;
    }

    @Override
    public float getEyeHeight() {
        return 0.4140625F * this.getScale();
    }

    @Override
    public ItemStack getPickedResult(final RayTraceResult target) {
        return new ItemStack(HatStands.Items.HAT_STAND);
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
        return HatStands.SoundEvents.ENTITY_HAT_STAND_FALL;
    }

    @Override
    protected SoundEvent getHurtSound(final DamageSource damage) {
        return HatStands.SoundEvents.ENTITY_HAT_STAND_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return HatStands.SoundEvents.ENTITY_HAT_STAND_BREAK;
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBox(final Entity entity) {
        return entity.canBePushed() ? entity.getEntityBoundingBox() : null;
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
    public void onUpdate() {
        super.onUpdate();
        this.behavior.onUpdate();
    }

    @Override
    public void notifyDataManagerChange(final DataParameter<?> key) {
        super.notifyDataManagerChange(key);
        // data parameter is private so we use serializer type
        if (key.getSerializer() == DataSerializers.STRING) {
            this.setBehavior(this.getCustomNameTag());
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
    public boolean processInitialInteract(final EntityPlayer player, final EnumHand hand) {
        final ItemStack stack = player.getHeldItem(hand);
        if (this.onName(player, stack)) {
            stack.shrink(1);
            return true;
        }
        return super.processInitialInteract(player, hand);
    }

    public boolean onName(final EntityPlayer player, final ItemStack stack) {
        if (stack.hasDisplayName()) {
            this.setCustomNameTag(stack.getDisplayName());
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
    public void setDead() {
        super.setDead();
        this.behavior.onEnd();
    }

    void setScale(final float scale) {
        this.setSize(WIDTH * scale, HEIGHT * scale);
    }

    void resetSize() {
        this.setSize(WIDTH, HEIGHT);
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
        this.typeMessage(new TextComponentString(text));
    }

    void typeMessage(final ITextComponent text) {
        this.emitChat(new TextComponentTranslation("chat.type.text", this.getDisplayName(), text));
    }

    void emitChat(final ITextComponent chat) {
        for (final EntityPlayerMP players : this.world.getEntitiesWithinAABB(EntityPlayerMP.class, this.getEntityBoundingBox().grow(16.0D))) {
            players.sendStatusMessage(chat, false);
        }
    }

    @Override
    protected void setSize(final float width, final float height) {
        if (width != this.width || height != this.height) {
            final float oldWidth = this.width;
            this.width = width;
            this.height = height;
            if (this.width < oldWidth) {
                final double r = width / 2.0D;
                this.setEntityBoundingBox(new AxisAlignedBB(this.posX - r, this.posY, this.posZ - r, this.posX + r, this.posY + this.height, this.posZ + r));
            } else {
                final AxisAlignedBB b = this.getEntityBoundingBox();
                this.setEntityBoundingBox(new AxisAlignedBB(b.minX, b.minY, b.minZ, b.minX + this.width, b.minY + this.height, b.minZ + this.width));
                if (this.width > oldWidth && !this.firstUpdate && !this.world.isRemote) {
                    // Fix vanilla: move just half the width change to maintain pos
                    this.move(MoverType.SELF, (oldWidth - this.width) / 2.0D, 0.0D, (oldWidth - this.width) / 2.0D);
                }
            }

        }
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
        final ItemStack stack = new ItemStack(HatStands.Items.HAT_STAND);
        if (this.hasCustomName()) {
            stack.setStackDisplayName(this.getCustomNameTag());
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
        this.world.playSound(null, this.posX, this.posY, this.posZ, HatStands.SoundEvents.ENTITY_HAT_STAND_BREAK, this.getSoundCategory(), 1.0F, 1.0F);
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
                this.world.playSound(this.posX, this.posY, this.posZ, HatStands.SoundEvents.ENTITY_HAT_STAND_HIT, this.getSoundCategory(), 0.3F, 1.0F, false);
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
        this.behavior.onSave(compound);
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
        this.behavior.onLoad(compound);
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
}
