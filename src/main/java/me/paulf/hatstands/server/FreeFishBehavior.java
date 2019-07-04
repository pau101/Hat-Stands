package me.paulf.hatstands.server;

import com.mojang.authlib.GameProfile;
import me.paulf.hatstands.util.Mth;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketDestroyEntities;
import net.minecraft.network.play.server.SPacketEntityMetadata;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.network.play.server.SPacketSpawnPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Collections;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class FreeFishBehavior implements Behavior {
	private final HatStandEntity entity;

	private EntityFishHook hook;

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
			final EnumFacing facing = e.getHorizontalFacing();
			for (int n = 0; n < 16; n++) {
				final BlockPos pos = origin.offset(facing, n / 4 + 3).down(n % 4 + 1);
				final IBlockState state = e.world.getBlockState(pos);
				if (state.getMaterial() == Material.WATER) {
					final Vec3d begin = new Vec3d(e.posX, e.posY + e.getEyeHeight(), e.posZ);
					final Vec3d end = new Vec3d(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D);
					if (e.world.rayTraceBlocks(begin, end, false, true, false) != null) {
						break;
					}
					final FakePlayer angler = this.createAngler();
					e.rotationPitch = 10.0F;
					// Position for hook to shoot from
					angler.setLocationAndAngles(e.posX, e.posY, e.posZ + 0.25D, e.rotationYaw, 35.0F);
					this.hook = new EntityFishHook(e.world, angler) {
						@Override
						protected boolean canBeHooked(final Entity entity) {
							return entity != e && super.canBeHooked(entity);
						}
					};
					// Position client for line to start at center of hat stand face
					final Vec3d offset = new Vec3d(0.0D, e.getEyeHeight(), 0.25D).rotateYaw(Mth.toRadians(-e.rotationYaw));
					angler.setLocationAndAngles(e.posX + offset.x - 0.35D, e.posY + offset.y + 0.45D - angler.getEyeHeight(), e.posZ + offset.z - 0.8D, 0.0F, 35.0F);
					angler.setPrimaryHand(EnumHandSide.RIGHT);
					angler.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
					angler.setInvisible(true);
					//noinspection unchecked
					this.sendAngler(angler, (Set<EntityPlayerMP>) ((WorldServer) e.world).getEntityTracker().getTrackingPlayers(e));
					e.world.spawnEntity(this.hook);
					break;
				}
			}
		} else if (this.hook != null) {
			if (this.hook.onGround && e.world.getBlockState(new BlockPos(this.hook)).getMaterial() != Material.WATER || this.hook.motionY == 0.0F) {
				// Position for loot to fly towards
				if (this.angler != null) {
					this.angler.setLocationAndAngles(e.posX, e.posY, e.posZ, 0.0F, 35.0F);
				}
				this.hook.handleHookRetraction();
				this.hook = null;
				this.removeAngler();
				e.rotationPitch = 0.0F;
			}
		}
	}

	private FakePlayer createAngler() {
		if (this.angler == null) {
			final UUID uuid = MathHelper.getRandomUUID(new Random(this.entity.getUniqueID().getLeastSignificantBits() ^ this.entity.getUniqueID().getMostSignificantBits()));
			this.angler = new FakePlayer((WorldServer) this.entity.world, new GameProfile(uuid, "[Hat Stands]"));
		}
		return this.angler;
	}

	private void removeAngler() {
		if (this.angler != null) {
			this.angler.setDead();
			final MinecraftServer server = this.entity.world.getMinecraftServer();
			if (server != null) {
				server.getPlayerList().sendPacketToAllPlayers(new SPacketDestroyEntities(this.angler.getEntityId()));
			}
			this.angler = null;
		}
	}

	private void sendAngler(final FakePlayer angler, final Iterable<? extends EntityPlayerMP> players) {
		final Packet<?> add = new SPacketPlayerListItem(SPacketPlayerListItem.Action.ADD_PLAYER, angler);
		final Packet<?> spawn = new SPacketSpawnPlayer(angler);
		final Packet<?> remove = new SPacketPlayerListItem(SPacketPlayerListItem.Action.REMOVE_PLAYER, angler);
		final Packet<?> metadata = new SPacketEntityMetadata(angler.getEntityId(), angler.getDataManager(), true);
		for (final EntityPlayerMP player : players) {
			final NetHandlerPlayServer conn = player.connection;
			conn.sendPacket(add);
			conn.sendPacket(spawn);
			conn.sendPacket(remove);
			conn.sendPacket(metadata);
		}
	}

	@Override
	public void onEnd() {
		this.entity.lookForward();
		MinecraftForge.EVENT_BUS.unregister(this);
		this.removeAngler();
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public void onFishItem(final ItemFishedEvent event) {
		final EntityFishHook hook = event.getHookEntity();
		final EntityPlayer angler = hook.getAngler();
		if (angler == this.angler) {
			final World world = hook.world;
			for (final ItemStack stack : event.getDrops()) {
				final EntityItem item = new EntityItem(world, hook.posX, hook.posY, hook.posZ, stack);
				final double dx = angler.posX - hook.posX;
				final double dy = angler.posY - hook.posY;
				final double dz = angler.posZ - hook.posZ;
				final double dist = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
				final double mag = 0.1D;
				item.motionX = dx * mag;
				item.motionY = dy * mag + MathHelper.sqrt(dist) * 0.08D;
				item.motionZ = dz * mag;
				world.spawnEntity(item);
			}
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public void onTrackStart(final PlayerEvent.StartTracking event) {
		if (this.angler != null && event.getTarget() == this.entity) {
			this.sendAngler(this.angler, Collections.singleton((EntityPlayerMP) event.getEntityPlayer()));
		}
	}

	@SubscribeEvent
	public void onTrackStop(final PlayerEvent.StopTracking event) {
		if (this.angler != null && event.getTarget() == this.entity) {
			((EntityPlayerMP) event.getEntityPlayer()).connection.sendPacket(new SPacketDestroyEntities(this.angler.getEntityId()));
		}
	}
}
