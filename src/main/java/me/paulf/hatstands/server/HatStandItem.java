package me.paulf.hatstands.server;

import me.paulf.hatstands.HatStands;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.List;

public final class HatStandItem extends Item {
    public HatStandItem(final Item.Properties properties) {
        super(properties);
    }

    @Override
    public ActionResultType onItemUse(final ItemUseContext context) {
        final World world = context.getWorld();
        final PlayerEntity player = context.getPlayer();
        final BlockItemUseContext blockContext = new BlockItemUseContext(context);
        final BlockPos placePos = blockContext.getPos();
        final ItemStack stack = blockContext.getItem();
        if (!blockContext.canPlace()) {
            return ActionResultType.FAIL;
        }
        final List<Entity> entities = world.getEntitiesWithinAABBExcludingEntity(null, new AxisAlignedBB(placePos));
        if (!entities.isEmpty()) {
            return ActionResultType.FAIL;
        }
        if (!world.isRemote) {
            world.removeBlock(placePos, false);
            final float yaw;
            if (context.getFace().getAxis() == Direction.Axis.Y) {
                yaw = MathHelper.floor(((context.getPlacementYaw() + 180.0D) * 8.0D / 360.0D) + 0.5D) * 360.0F / 8.0F;
            } else {
                yaw = context.getFace().getHorizontalAngle();
            }
            final HatStandEntity stand = HatStandEntity.create(world, placePos, yaw);
            EntityType.applyItemNBT(world, player, stand, stack.getTag());
            world.addEntity(stand);
            if (!stand.hasCustomName()) {
                stand.onName(player, stack);
            }
            world.playSound(null, placePos, HatStands.SoundEvents.ENTITY_HAT_STAND_PLACE.get(), stand.getSoundCategory(), 0.75F, 0.8F);
        }
        stack.shrink(1);
        return ActionResultType.SUCCESS;
    }
}
