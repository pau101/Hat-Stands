package me.paulf.hatstands.server.entity;

import com.google.common.collect.ImmutableList;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.ByteBuf;
import me.paulf.hatstands.HatStands;
import me.paulf.hatstands.server.sound.HatStandsSounds;
import me.paulf.hatstands.util.Mth;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
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
import net.minecraft.entity.projectile.EntityFishHook;
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
import net.minecraft.network.play.server.SPacketDestroyEntities;
import net.minecraft.network.play.server.SPacketEntityMetadata;
import net.minecraft.network.play.server.SPacketOpenWindow;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.network.play.server.SPacketSpawnPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
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
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

public final class HatStandEntity extends EntityLivingBase implements IEntityAdditionalSpawnData {
    private static final byte PUNCH_ID = 32;

    private static final float WIDTH = 9.0F / 16.0F;

    private static final float HEIGHT = 11.0F / 16.0F;

    private long lastPunchTime;

    private final NonNullList<ItemStack> handItems;

    private final NonNullList<ItemStack> armorItems;

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

    private Runnable update = () -> {};

    private Consumer<EntityPlayer> interact = p -> {};

    @Override
    public void onUpdate() {
        super.onUpdate();
        this.update.run();
    }

    @Override
    public void notifyDataManagerChange(final DataParameter<?> key) {
        super.notifyDataManagerChange(key);
        // data parameter is private so we serializer type
        if (key.getSerializer() == DataSerializers.STRING) {
            this.handleName(this.getCustomNameTag());
        }
    }

    private void lookForward() {
        this.rotationPitch = 0.0F;
        this.rotationYawHead = this.rotationYaw;
    }

    @Override
    public boolean processInitialInteract(final EntityPlayer player, final EnumHand hand) {
        final ItemStack stack = player.getHeldItem(hand);
        if (stack.hasDisplayName()) {
            this.setCustomNameTag(stack.getDisplayName());
            this.interact.accept(player);
            stack.shrink(1);
            return true;
        }
        return super.processInitialInteract(player, hand);
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

    private static final class NewzHolder {
        private static final ImmutableList<String> NEWZ = readCatFacts();

        private static ImmutableList<String> readCatFacts() {
            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(MinecraftServer.class.getResourceAsStream("/assets/" + HatStands.ID + "/texts/newz.txt")))) {
                return reader.lines().collect(ImmutableList.toImmutableList());
            } catch (final IOException e) {
                return ImmutableList.of("Newz is surprisingly difficult to find.");
            }
        }
    }

    private void handleName(final String name) {
        this.lookForward();
        MinecraftForge.EVENT_BUS.unregister(this.update);
        this.update = () -> {};
        this.interact = p -> {};
        float w = WIDTH, h = HEIGHT;
        if (name.length() == 8) {
            switch (name) {
                case "askalexa":
                    if (!this.world.isRemote) {
                        this.update = new Alexa(this);
                        MinecraftForge.EVENT_BUS.register(this.update);
                    }
                    break;
                case "allarmed":
                    if (!this.world.isRemote) {
                        this.update = () -> {
                            if (this.ticksExisted % 23 == 0) {
                                final List<EntityMob> entities = this.world.getEntitiesWithinAABB(
                                    EntityMob.class,
                                    this.getEntityBoundingBox().grow(8.0D, 4.0D, 8.0D),
                                    e -> e != null && e.isEntityAlive() && !this.isOnSameTeam(e) && this.canEntityBeSeen(e)
                                );
                                if (entities.isEmpty()) {
                                    this.lookForward();
                                } else {
                                    final EntityMob mob = entities.get(0);
                                    this.lookAt(mob);
                                    final EntityTippedArrow arrow = new EntityTippedArrow(this.world, this);
                                    arrow.shoot(this, this.rotationPitch, this.rotationYawHead, 0.0F, 3.0F, 1.0F);
                                    arrow.setIsCritical(true);
                                    this.world.spawnEntity(arrow);
                                    this.world.playSound(
                                        null,
                                        this.posX, this.posY, this.posZ,
                                        SoundEvents.ENTITY_ARROW_SHOOT,
                                        this.getSoundCategory(),
                                        1.0F,
                                        1.0F / (this.rand.nextFloat() * 0.4F + 1.2F) + 0.5F
                                    );
                                }
                            }
                        };
                    }
                    break;
                case "bebigger":
                    w *= 1.5F;
                    h *= 1.5F;
                    break;
                case "catfacts":
                    if (!this.world.isRemote) {
                        final int[] delay = { -1 };
                        this.update = () -> {
                            if (delay[0] < 0) {
                                final int delayLower = 30 * 20;
                                final int delayUpper = 2 * 60 * 20;
                                delay[0] = this.rand.nextInt(delayUpper - delayLower) + delayLower;
                            } else if (delay[0]-- == 0) {
                                final ImmutableList<String> facts = CatFactsHolder.CAT_FACTS;
                                this.typeMessage(facts.get(this.rand.nextInt(facts.size())));
                            }
                        };
                        this.interact = p -> this.typeMessage("Thanks for signing up for Cat Facts! You now will receive fun daily facts about CATS! >o<");
                    }
                    break;
                case "cutcable":
                    if (!this.world.isRemote) {
                        this.update = () -> {
                            if (this.ticksExisted % 13 == 0) {
                                final int r = 2;
                                final BlockPos pos = new BlockPos(this).add(
                                    this.rand.nextInt(1 + 2 * r) - r,
                                    this.rand.nextInt(1 + 2 * r) - r,
                                    this.rand.nextInt(1 + 2 * r) - r
                                );
                                if (this.world.getBlockState(pos).getBlock() == Blocks.REDSTONE_WIRE) {
                                    this.world.destroyBlock(pos, true);
                                }
                            }
                        };
                    }
                    break;
                // dropdead
                // drumdude
                case "extraegg":
                    if (!this.world.isRemote) {
                        this.update = () -> {
                            if (this.ticksExisted % 29 == 0 && this.rand.nextFloat() < 0.333F) {
                                this.rotationPitch = 15.0F;
                                this.playSound(SoundEvents.ENTITY_EGG_THROW, 0.5F, 0.4F / (this.rand.nextFloat() * 0.4F + 0.8F));
                                final EntityEgg egg = new EntityEgg(this.world, this);
                                egg.ignoreEntity = this;
                                egg.shoot(this, this.rotationPitch, this.rotationYaw, 0.0F, 1.5F, 1.0F);
                                this.world.spawnEntity(egg);
                            } else if (this.ticksExisted % 13 == 5) {
                                this.rotationPitch = 0.0F;
                            }
                        };
                    }
                    break;
                case "freefish":
                    if (!this.world.isRemote) {
                        final EntityFishHook[] hook = { null };
                        this.update = () -> {
                            if (hook[0] == null && this.ticksExisted % 37 == 0) {
                                final BlockPos origin = new BlockPos(this);
                                final EnumFacing facing = this.getHorizontalFacing();
                                for (int n = 0; n < 16; n++) {
                                    final BlockPos pos = origin.offset(facing, n / 4 + 3).down(n % 4 + 1);
                                    final IBlockState state = this.world.getBlockState(pos);
                                    if (state.getMaterial() == Material.WATER) {
                                        final Vec3d begin = new Vec3d(this.posX, this.posY + this.getEyeHeight(), this.posZ);
                                        final Vec3d end = new Vec3d(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D);
                                        if (this.world.rayTraceBlocks(begin, end, false, true, false) != null) {
                                            break;
                                        }
                                        final FakePlayer angler = this.getAngler();
                                        this.rotationPitch = 10.0F;
                                        // Position for hook to shoot from
                                        angler.setLocationAndAngles(this.posX, this.posY, this.posZ + 0.25D, this.rotationYaw, 35.0F);
                                        hook[0] = new EntityFishHook(this.world, angler) {
                                            @Override
                                            protected boolean canBeHooked(final Entity entity) {
                                                return entity != HatStandEntity.this && super.canBeHooked(entity);
                                            }
                                        };
                                        final Vec3d offset = new Vec3d(-0.35D, 0.45D - angler.getEyeHeight() + this.getEyeHeight(), -0.8D + 0.25D).rotateYaw(Mth.toRadians(this.rotationYaw));
                                        // Position client for line to start at center of hat stand face
                                        angler.setLocationAndAngles(this.posX + offset.x, this.posY  + offset.y, this.posZ + offset.z, this.rotationYaw, 35.0F);
                                        angler.setPrimaryHand(EnumHandSide.RIGHT);
                                        angler.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
                                        angler.setInvisible(true);
                                        final EntityTracker tracker = ((WorldServer) this.world).getEntityTracker();
                                        tracker.sendToTracking(this, new SPacketPlayerListItem(SPacketPlayerListItem.Action.ADD_PLAYER, angler));
                                        tracker.sendToTracking(this, new SPacketSpawnPlayer(angler));
                                        tracker.sendToTracking(this, new SPacketPlayerListItem(SPacketPlayerListItem.Action.REMOVE_PLAYER, angler));
                                        tracker.sendToTracking(this, new SPacketEntityMetadata(angler.getEntityId(), angler.getDataManager(), true));
                                        // Position for loot to fly towards
                                        angler.setLocationAndAngles(this.posX, this.posY, this.posZ, this.rotationYaw, 35.0F);
                                        this.world.spawnEntity(hook[0]);
                                        break;
                                    }
                                }
                            } else if (hook[0] != null) {
                                if (hook[0].onGround && this.world.getBlockState(new BlockPos(hook[0])).getMaterial() != Material.WATER || hook[0].motionY == 0.0F) {
                                    hook[0].handleHookRetraction();
                                    final MinecraftServer server = this.world.getMinecraftServer();
                                    if (server != null) {
                                        server.getPlayerList().sendPacketToAllPlayers(new SPacketDestroyEntities(hook[0].getAngler().getEntityId()));
                                    }
                                    hook[0] = null;
                                    this.rotationPitch = 0.0F;
                                }
                            }
                        };
                    }
                    break;
                // gonegold, gold model texture
                // gotgains, muscle something
                case "hasheart":
                    if (this.world.isRemote) {
                        this.update = () -> {
                            if (this.rand.nextFloat() < 0.4F && this.ticksExisted % 5 == 0) {
                                this.world.spawnParticle(
                                    EnumParticleTypes.HEART,
                                    this.posX + this.rand.nextFloat() * this.width * 2.0F - this.width,
                                    this.posY + 0.5D + this.rand.nextFloat() * this.height,
                                    this.posZ + this.rand.nextFloat() * this.width * 2.0F - this.width,
                                    this.rand.nextGaussian() * 0.02D,
                                    this.rand.nextGaussian() * 0.02D,
                                    this.rand.nextGaussian() * 0.02D
                                );
                            }
                        };
                    }
                    break;
                case "hithuman":
                    if (!this.world.isRemote) {
                        this.update = () -> {
                            if (this.ticksExisted % 7 == 0 && this.rand.nextFloat() < 0.333F) {
                                final DamageSource damage = DamageSource.causeMobDamage(this);
                                for (final EntityPlayerMP player : this.world.getEntitiesWithinAABB(EntityPlayerMP.class, this.getEntityBoundingBox().grow(1.0D))) {
                                    player.attackEntityFrom(damage, 1.0F);
                                }
                            }
                        };
                    }
                    break;
                case "moremini":
                    w *= 0.5F;
                    h *= 0.5F;
                    break;
                case "sexysong":
                    this.interact = p-> this.playSound(HatStandsSounds.ENTITY_HAT_STAND_SEXYSONG, 1.0F, 1.0F);
                    break;
                case "smallspy":
                    if (this.world.isRemote) {
                        this.update = () -> {
                            final @Nullable EntityPlayer player = this.world.getClosestPlayerToEntity(this, 8.0D);
                            if (player == null) {
                                this.lookForward();
                            } else {
                                this.lookAt(player);
                            }
                        };
                        this.update.run();
                    }
                    break;
                // looklost, randomly look around
                // livelazy, no base or neck
                case "pwnpeeps":
                    if (!this.world.isRemote) {
                        this.interact = p -> {
                            final MinecraftServer server = this.world.getMinecraftServer();
                            if (server != null && server.getPlayerList().canSendCommands(p.getGameProfile())) {
                                final EntityTracker tracker = ((WorldServer) this.world).getEntityTracker();
                                //noinspection ConstantConditions
                                tracker.sendToTracking(this, new SPacketOpenWindow(1, "minecraft:enchanting_table", null));
                            }
                        };
                    }
                    break;
                case "powerpwn":
                    if (!this.world.isRemote) {
                        this.interact = p -> {
                            final MinecraftServer server = this.world.getMinecraftServer();
                            if (server != null && server.getPlayerList().canSendCommands(p.getGameProfile())) {
                                final EntityTracker tracker = ((WorldServer) this.world).getEntityTracker();
                                final ChunkPrimer primer = new ChunkPrimer();
                                final BlockPos b = new BlockPos(this);
                                primer.setBlockState(b.getX() & 0xF, b.getY(), b.getZ() & 0xF, Blocks.WATER.getDefaultState());
                                primer.setBlockState(b.getX() & 0xF, b.getY(), MathHelper.abs((b.getZ() & 0xF) - 1), Blocks.RED_SHULKER_BOX.getDefaultState());
                                primer.setBlockState(MathHelper.abs((b.getX() & 0xF) - 1), b.getY(), b.getZ() & 0xF, Blocks.RED_SHULKER_BOX.getDefaultState());
                                tracker.sendToTracking(this, new SPacketChunkData(new Chunk(this.world, primer, b.getX() >> 4, b.getZ() >> 4), 0xFFFF));
                            }
                        };
                    }
                    break;
            }
        }
        this.setSize(w, h);
    }

    @Override
    public void setDead() {
        super.setDead();
        MinecraftForge.EVENT_BUS.unregister(this.update);
    }

    private FakePlayer getAngler() {
        return FakePlayerFactory.get((WorldServer) this.world, new GameProfile(MathHelper.getRandomUUID(new Random(this.getUniqueID().getLeastSignificantBits() ^ this.getUniqueID().getMostSignificantBits())), "Angler"));
    }

    private void lookAt(final Entity target) {
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
