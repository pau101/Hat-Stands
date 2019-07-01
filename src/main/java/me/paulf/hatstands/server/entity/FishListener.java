package me.paulf.hatstands.server.entity;

import me.paulf.hatstands.HatStands;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

// Stop fishing by fake players spawning experience orbs
@Mod.EventBusSubscriber(modid = HatStands.ID)
public final class FishListener {
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onItemFish(final ItemFishedEvent event) {
        final EntityFishHook hook = event.getHookEntity();
        final EntityPlayer angler = hook.getAngler();
        if (angler instanceof FakePlayer) {
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
}
