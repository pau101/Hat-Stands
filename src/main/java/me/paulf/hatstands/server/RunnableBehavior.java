package me.paulf.hatstands.server;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public abstract class RunnableBehavior implements Behavior {
    protected final HatStandEntity stand;

    private boolean powered;

    private BlockPos pos;

    public RunnableBehavior(final HatStandEntity stand) {
        this.stand = stand;
        this.powered = false;
        this.pos = BlockPos.ZERO;
    }

    protected abstract void run();

    @Override
    public void onName(final PlayerEntity player) {
        this.run();
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
            if (this.stand.posX >= pos.getX() && this.stand.posX < pos.getX() + 1.0D &&
                this.stand.posZ >= pos.getZ() && this.stand.posZ < pos.getZ() + 1.0D &&
                this.stand.posY >= pos.getY() + 1.0D && this.stand.posY < pos.getY() + 2.0D) {
                final boolean powered = world.isBlockPowered(pos);
                if (powered && (!this.powered || !this.pos.equals(pos))) {
                    this.run();
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
}
