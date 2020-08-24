package me.paulf.hatstands.server;

import com.google.common.collect.ImmutableList;
import com.mojang.authlib.GameProfile;
import me.paulf.hatstands.util.Mth;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.IPacket;
import net.minecraft.network.play.server.SDestroyEntitiesPacket;
import net.minecraft.network.play.server.SEntityMetadataPacket;
import net.minecraft.network.play.server.SPlayerListItemPacket;
import net.minecraft.network.play.server.SSpawnPlayerPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.HandSide;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Random;
import java.util.UUID;

public class FreeFishBehavior implements Behavior {
    private final HatStandEntity entity;

    private FishingBobberEntity hook;

    private FakePlayer angler;

    public FreeFishBehavior(final HatStandEntity entity) {
        this.entity = entity;
    }

    @Override
    public void onStart() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onUpdate() {
        final HatStandEntity e = this.entity;
        if (this.hook == null && e.ticksExisted % 37 == 0) {
            final BlockPos origin = new BlockPos(e);
            final Direction facing = e.getHorizontalFacing();
            for (int n = 0; n < 16; n++) {
                final BlockPos pos = origin.offset(facing, n / 4 + 3).down(n % 4 + 1);
                final BlockState state = e.world.getBlockState(pos);
                if (state.getMaterial() == Material.WATER) {
                    final Vec3d begin = new Vec3d(e.getPosX(), e.getPosY() + e.getEyeHeight(), e.getPosZ());
                    final Vec3d end = new Vec3d(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D);
                    if (e.world.rayTraceBlocks(new RayTraceContext(begin, end, RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE, e)).getType() != RayTraceResult.Type.MISS) {
                        break;
                    }
                    final FakePlayer angler = this.createAngler();
                    e.rotationPitch = 10.0F;
                    // Position for hook to shoot from
                    angler.setLocationAndAngles(e.getPosX(), e.getPosY(), e.getPosZ() + 0.25D, e.rotationYaw, 35.0F);
                    this.hook = new FishingBobberEntity(angler, e.world, 0, 0);
                    // Position client for line to start at center of hat stand face
                    final Vec3d offset = new Vec3d(0.0D, e.getEyeHeight(), 0.25D).rotateYaw(Mth.toRadians(-e.rotationYaw));
                    angler.setLocationAndAngles(e.getPosX() + offset.x - 0.35D, e.getPosY() + offset.y + 0.45D - angler.getEyeHeight(), e.getPosZ() + offset.z - 0.8D, 0.0F, 35.0F);
                    angler.setPrimaryHand(HandSide.RIGHT);
                    angler.setHeldItem(Hand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
                    angler.setInvisible(true);
                    for (final IPacket<?> pkt : this.sendAngler(angler)) {
                        ((ServerWorld) e.world).getChunkProvider().sendToAllTracking(e, pkt);
                    }
                    e.world.addEntity(this.hook);
                    break;
                }
            }
        } else if (this.hook != null) {
            if (this.hook.onGround && e.world.getBlockState(new BlockPos(this.hook)).getMaterial() != Material.WATER || this.hook.getMotion().y == 0.0F) {
                // Position for loot to fly towards
                if (this.angler != null) {
                    this.angler.setLocationAndAngles(e.getPosX(), e.getPosY(), e.getPosZ(), 0.0F, 35.0F);
                    this.hook.handleHookRetraction(this.angler.getHeldItemMainhand());
                }
                this.hook = null;
                this.removeAngler();
                e.rotationPitch = 0.0F;
            }
        }
    }

    private FakePlayer createAngler() {
        if (this.angler == null) {
            final UUID uuid = MathHelper.getRandomUUID(new Random(this.entity.getUniqueID().getLeastSignificantBits() ^ this.entity.getUniqueID().getMostSignificantBits()));
            this.angler = new FakePlayer((ServerWorld) this.entity.world, new GameProfile(uuid, "[Hat Stands]"));
        }
        return this.angler;
    }

    private void removeAngler() {
        if (this.angler != null) {
            this.angler.remove();
            final MinecraftServer server = this.entity.world.getServer();
            if (server != null) {
                server.getPlayerList().sendPacketToAllPlayers(new SDestroyEntitiesPacket(this.angler.getEntityId()));
            }
            this.angler = null;
        }
    }

    private List<IPacket<?>> sendAngler(final FakePlayer angler) {
        return ImmutableList.of(
            new SPlayerListItemPacket(SPlayerListItemPacket.Action.ADD_PLAYER, angler),
            new SSpawnPlayerPacket(angler),
            new SPlayerListItemPacket(SPlayerListItemPacket.Action.REMOVE_PLAYER, angler),
            new SEntityMetadataPacket(angler.getEntityId(), angler.getDataManager(), true)
        );
    }

    @Override
    public void onEnd() {
        this.entity.lookForward();
        MinecraftForge.EVENT_BUS.unregister(this);
        this.removeAngler();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onFishItem(final ItemFishedEvent event) {
        final FishingBobberEntity hook = event.getHookEntity();
        final PlayerEntity angler = hook.getAngler();
        if (angler != null && angler == this.angler) {
            final World world = hook.world;
            for (final ItemStack stack : event.getDrops()) {
                final ItemEntity item = new ItemEntity(world, hook.getPosX(), hook.getPosY(), hook.getPosZ(), stack);
                final double dx = angler.getPosX() - hook.getPosX();
                final double dy = angler.getPosY() - hook.getPosY();
                final double dz = angler.getPosZ() - hook.getPosZ();
                final double dist = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
                final double mag = 0.1D;
                item.setMotion(new Vec3d(
                    dx * mag,
                    dy * mag + MathHelper.sqrt(dist) * 0.08D,
                    dz * mag
                ));
                world.addEntity(item);
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onTrackStart(final PlayerEvent.StartTracking event) {
        if (this.angler != null && event.getTarget() == this.entity) {
            for (final IPacket<?> pkt : this.sendAngler(this.angler)) {
                ((ServerPlayerEntity) event.getPlayer()).connection.sendPacket(pkt);
            }
        }
    }

    @SubscribeEvent
    public void onTrackStop(final PlayerEvent.StopTracking event) {
        if (this.angler != null && event.getTarget() == this.entity) {
            ((ServerPlayerEntity) event.getPlayer()).connection.sendPacket(new SDestroyEntitiesPacket(this.angler.getEntityId()));
        }
    }
}
