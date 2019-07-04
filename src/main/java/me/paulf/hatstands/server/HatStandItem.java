package me.paulf.hatstands.server;

import me.paulf.hatstands.HatStands;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.List;

public final class HatStandItem extends Item {
    public HatStandItem() {
        this.setCreativeTab(CreativeTabs.DECORATIONS);
    }

    @Override
    public EnumActionResult onItemUse(final EntityPlayer player, final World world, final BlockPos pos, final EnumHand hand, final EnumFacing facing, final float hitX, final float hitY, final float hitZ) {
        final IBlockState state = world.getBlockState(pos);
        final Block block = state.getBlock();
        final BlockPos placePos = block.isReplaceable(world, pos) ? pos : pos.offset(facing);
        final ItemStack stack = player.getHeldItem(hand);
        if (!player.canPlayerEdit(placePos, facing, stack) || !world.getBlockState(placePos).getBlock().isReplaceable(world, placePos)) {
            return EnumActionResult.FAIL;
        }
        final List<Entity> entities = world.getEntitiesWithinAABBExcludingEntity(null, new AxisAlignedBB(placePos));
        if (!entities.isEmpty()) {
            return EnumActionResult.FAIL;
        }
        if (!world.isRemote) {
            world.setBlockToAir(placePos);
            final float yaw;
            if (facing.getAxis() == EnumFacing.Axis.Y) {
                yaw = MathHelper.floor(((player.rotationYaw + 180.0D) * 8.0D / 360.0D) + 0.5D) * 360.0F / 8.0F;
            } else {
                yaw = facing.getHorizontalAngle();
            }
            final HatStandEntity stand = HatStandEntity.create(world, placePos, yaw);
            ItemMonsterPlacer.applyItemEntityDataToEntity(world, player, stack, stand);
            if (!stand.hasCustomName()) {
                stand.onName(player, stack);
            }
            world.spawnEntity(stand);
            world.playSound(null, placePos, HatStands.SoundEvents.ENTITY_HAT_STAND_PLACE, stand.getSoundCategory(), 0.75F, 0.8F);
        }
        stack.shrink(1);
        return EnumActionResult.SUCCESS;
    }
}
