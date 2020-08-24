package me.paulf.hatstands.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import me.paulf.hatstands.HatStands;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.effect.LightningBoltEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.EggEntity;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SOpenWindowPacket;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Direction;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.server.ServerChunkProvider;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class HatStandBehaviors {
    static class Builder {
        final HatStandEntity entity;
        final ImmutableMap.Builder<String, Behavior> builder = new ImmutableMap.Builder<>();

        Builder(final HatStandEntity entity) {
            this.entity = entity;
        }

        Builder put(final String id, final Behavior behavior) {
            this.builder.put(id, behavior);
            return this;
        }

        Builder putServer(final String id, final Function<? super HatStandEntity, Behavior> behavior) {
            return this.entity.world.isRemote ? this : this.put(id, behavior.apply(this.entity));
        }

        Builder putClient(final String id, final Function<? super HatStandEntity, Behavior> behavior) {
            return this.entity.world.isRemote ? this.put(id, behavior.apply(this.entity)) : this;
        }

        ImmutableMap<String, Behavior> build() {
            return this.builder.build();
        }
    }

    public static ImmutableMap<String, Behavior> create(final HatStandEntity stand) {
        return new Builder(stand)
            .putServer("askalexa", AskAlexaBehavior::new)
            .putServer("allarmed", e -> new Behavior() {
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
                                1.0F / (e.getRNG().nextFloat() * 0.4F + 1.2F) + 0.5F
                            );
                        }
                    }
                }

                @Override
                public void onEnd() {
                    e.lookForward();
                }
            })
            .putServer("bebigger", e -> new Behavior() {
                @Override
                public void onStart() {
                    e.setSize(HatStandEntity.Scale.BIG);
                }

                @Override
                public void onEnd() {
                    e.setSize(HatStandEntity.Scale.NORMAL);
                }
            })
            .putServer("beefbolt", e -> new Behavior() {
                @Override
                public void onUpdate() {
                    if (e.ticksExisted % 57 == 0 && e.getRNG().nextFloat() < 0.15F) {
                        final List<CowEntity> entities = e.world.getEntitiesWithinAABB(
                            CowEntity.class,
                            e.getBoundingBox().grow(10.0D, 6.0D, 10.0D),
                            e -> e != null && e.isAlive() && !e.isOnSameTeam(e) && e.canEntityBeSeen(e)
                        );
                        if (!entities.isEmpty()) {
                            final CowEntity beef = entities.get(e.getRNG().nextInt(entities.size()));
                            if (beef.posY + beef.getHeight() > e.world.getHeight(Heightmap.Type.MOTION_BLOCKING, new BlockPos(beef)).getY()) {
                                ((ServerWorld) e.world).addLightningBolt(new LightningBoltEntity(e.world, beef.posX, beef.posY, beef.posZ, false));
                            }
                        }
                    }
                }
            })
            .putServer("baconbow", e -> new Behavior() {
                @Override
                public void onStart() {
                    MinecraftForge.EVENT_BUS.register(this);
                }

                @Override
                public void onEnd() {
                    MinecraftForge.EVENT_BUS.unregister(this);
                }

                @SubscribeEvent(priority = EventPriority.LOW)
                public void onAttack(final LivingAttackEvent event) {
                    final DamageSource source = event.getSource();
                    final Entity entity = event.getEntity();
                    if (entity instanceof PigEntity && source.isProjectile() && "arrow".equals(source.damageType) && e.getDistanceSq(entity) < 16.0D * 16.0D) {
                        entity.setFire(5);
                    }
                }
            })
            .putServer("catfacts", e -> new Behavior() {
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
                        this.delay = e.getRNG().nextInt(delayUpper - delayLower) + delayLower;
                    } else if (this.delay-- == 0) {
                        final ImmutableList<String> facts = CatFactsHolder.CAT_FACTS;
                        e.typeMessage(facts.get(e.getRNG().nextInt(facts.size())));
                    }
                }
            })
            .putServer("cutcable", e -> new Behavior() {
                @Override
                public void onUpdate() {
                    if (e.ticksExisted % 13 == 0) {
                        final int r = 2;
                        final BlockPos pos = new BlockPos(e).add(
                            e.getRNG().nextInt(1 + 2 * r) - r,
                            e.getRNG().nextInt(1 + 2 * r) - r,
                            e.getRNG().nextInt(1 + 2 * r) - r
                        );
                        if (e.world.getBlockState(pos).getBlock() == Blocks.REDSTONE_WIRE) {
                            e.world.destroyBlock(pos, true);
                        }
                    }
                }
            })
            .putServer("digdaily", e -> new Behavior() {
                BlockPos pos = BlockPos.ZERO;
                long startTime;
                int lastProgress;

                @Override
                public void onUpdate() {
                    final int h = 8;
                    final int v = 4;
                    if (this.pos.equals(BlockPos.ZERO)) {
                        if (e.ticksExisted % 103 == 0 && e.getRNG().nextFloat() < 0.4F) {
                            final List<BlockPos> candidates = StreamSupport.stream(BlockPos.getAllInBoxMutable(new BlockPos(e).add(-h, -v, -h), new BlockPos(e).add(h, v, h)).spliterator(), false)
                                .filter(this::harvestable)
                                .map(BlockPos::toImmutable)
                                .collect(Collectors.toList());
                            if (!candidates.isEmpty()) {
                                this.pos = candidates.get(e.getRNG().nextInt(candidates.size()));
                                this.startTime = e.world.getGameTime();
                                this.lastProgress = -1;
                            }
                        }
                    } else if (this.harvestable(this.pos)) {
                        final int progress = (int) (e.world.getGameTime() - this.startTime) * 10 / 20;
                        if (progress != this.lastProgress) {
                            e.world.sendBlockBreakProgress(e.getEntityId(), this.pos, progress);
                            this.lastProgress = progress;
                            if (progress >= 10) {
                                e.world.destroyBlock(this.pos, true);
                                this.pos = BlockPos.ZERO;
                            }
                        }
                    } else {
                        this.pos = BlockPos.ZERO;
                    }
                }

                boolean harvestable(final BlockPos pos) {
                    return e.world.isBlockPresent(pos) && e.world.getBlockState(pos).getBlock() == Blocks.GRASS_BLOCK;
                }
            })
            .putServer("extraegg", e -> new Behavior() {
                @Override
                public void onUpdate() {
                    if (e.ticksExisted % 151 == 0 && e.getRNG().nextFloat() < 0.25F) {
                        e.rotationPitch = 15.0F;
                        e.playSound(SoundEvents.ENTITY_EGG_THROW, 0.5F, 0.4F / (e.getRNG().nextFloat() * 0.4F + 0.8F));
                        final EggEntity egg = new EggEntity(e.world, e);
                        egg.shoot(e, e.rotationPitch, e.rotationYaw, 0.0F, 1.5F, 1.0F);
                        e.world.addEntity(egg);
                    } else if (e.ticksExisted % 13 == 5) {
                        e.rotationPitch = 0.0F;
                    }
                }
            })
            .putServer("freefish", FreeFishBehavior::new)
            .putServer("grabgold", e -> new Behavior() {
                @Override
                public void onUpdate() {
                    if (e.ticksExisted % 7 == 0 && e.getRNG().nextFloat() < 0.2F) {
                        this.chest().ifPresent(handler -> {
                            final List<ItemEntity> entities = e.world.getEntitiesWithinAABB(
                                ItemEntity.class,
                                e.getBoundingBox().grow(6.0D, 4.0D, 6.0D),
                                e -> e.isAlive() && (e.getItem().getItem().isIn(Tags.Items.ORES_GOLD) ||
                                    e.getItem().getItem().isIn(Tags.Items.INGOTS_GOLD) ||
                                    e.getItem().getItem().isIn(Tags.Items.NUGGETS_GOLD))
                            );
                            Collections.shuffle(entities);
                            for (final ItemEntity entity : entities) {
                                final ItemStack original = entity.getItem();
                                final ItemStack stack = ItemHandlerHelper.insertItem(handler, original, false);
                                if (stack.isEmpty()) {
                                    entity.remove();
                                } else {
                                    entity.setItem(stack);
                                }
                                if (stack.getCount() < original.getCount()) {
                                    break;
                                }
                            }
                        });
                    }
                }

                LazyOptional<IItemHandler> chest() {
                    final BlockPos below = new BlockPos(e.posX, e.posY - 0.5D, e.posZ);
                    if (e.world.getBlockState(below).isIn(Tags.Blocks.CHESTS)) {
                        final TileEntity entity = e.world.getTileEntity(below);
                        if (entity != null) {
                            return entity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, Direction.UP);
                        }
                    }
                    return LazyOptional.empty();
                }
            })
            .putClient("hasheart", e -> new Behavior() {
                @Override
                public void onUpdate() {
                    if (e.ticksExisted % 5 == 0 && e.getRNG().nextFloat() < 0.4F) {
                        e.world.addParticle(
                            ParticleTypes.HEART,
                            e.posX + e.getRNG().nextFloat() * e.getWidth() * 2.0F - e.getWidth(),
                            e.posY + 0.5D + e.getRNG().nextFloat() * e.getHeight(),
                            e.posZ + e.getRNG().nextFloat() * e.getWidth() * 2.0F - e.getWidth(),
                            e.getRNG().nextGaussian() * 0.02D,
                            e.getRNG().nextGaussian() * 0.02D,
                            e.getRNG().nextGaussian() * 0.02D
                        );
                    }
                }
            })
            .putServer("hithuman", e -> new Behavior() {
                @Override
                public void onUpdate() {
                    if (e.ticksExisted % 7 == 0 && e.getRNG().nextFloat() < 0.333F) {
                        final DamageSource damage = DamageSource.causeMobDamage(e);
                        for (final ServerPlayerEntity player : e.world.getEntitiesWithinAABB(ServerPlayerEntity.class, e.getBoundingBox().grow(1.0D))) {
                            player.attackEntityFrom(damage, 1.0F);
                        }
                    }
                }
            })
            // ironitem
            .putServer("justjunk", e -> new Behavior() {
                final int[] unlucky = {
                    0x8b03a0c6, 0x8c6e76e8, 0x91f93c32, 0xabd58aaf,
                    0xaf2050f1, 0xb08ce61b, 0xc9b61c10, 0xcd02a23a,
                    0xe849c6f9, 0xedd48c03, 0x00b2057c, 0x1b02cf2b,
                    0x1c6d954d, 0x2345ef45, 0x3bd4a930, 0x3f277f5a,
                    0x59b53b75, 0x5d01c09f, 0x7848e542, 0x7ddbab64
                };

                @Override
                public void onStart() {
                    MinecraftForge.EVENT_BUS.register(this);
                }

                @Override
                public void onEnd() {
                    MinecraftForge.EVENT_BUS.unregister(this);
                }

                @SubscribeEvent
                public void onUseItem(final PlayerInteractEvent.RightClickItem event) {
                    final World world = event.getWorld();
                    final ItemStack stack = event.getItemStack();
                    final PlayerEntity player = event.getPlayer();
                    if (!world.isRemote && stack.getItem() instanceof FishingRodItem && player.fishingBobber != null && e.getDistanceSq(player) < 16.0D * 16.0D) {
                        final Random rand = Objects.requireNonNull(ObfuscationReflectionHelper.getPrivateValue(Entity.class, player.fishingBobber, "field_70146_Z"), "rand");
                        rand.setSeed(this.unlucky[player.getRNG().nextInt(this.unlucky.length)]);
                    }
                }
            })
            .putServer("moremini", e -> new Behavior() {
                @Override
                public void onStart() {
                    e.setSize(HatStandEntity.Scale.MINI);
                }

                @Override
                public void onEnd() {
                    e.setSize(HatStandEntity.Scale.NORMAL);
                }
            })
            .putServer("sexysong", e -> new RunnableBehavior(e) {
                @Override
                protected void run() {
                    this.stand.playSound(HatStands.SoundEvents.ENTITY_HAT_STAND_SEXYSONG.orElseThrow(IllegalStateException::new), 1.0F, 1.0F);
                }
            })
            .putClient("smallspy", e -> new Behavior() {
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
            })
            .putServer("pwnpeeps", e -> new Behavior() {
                @Override
                public void onName(final PlayerEntity player) {
                    final MinecraftServer server = e.world.getServer();
                    if (server != null && server.getPlayerList().canSendCommands(player.getGameProfile())) {
                        final ServerChunkProvider tracker = ((ServerWorld) e.world).getChunkProvider();
                        //noinspection ConstantConditions
                        tracker.sendToAllTracking(e, new SOpenWindowPacket(1, ContainerType.GENERIC_9X1, null));
                        //tracker.sendToAllTracking(e, new SUpdateBossInfoPacket(SUpdateBossInfoPacket.Operation.ADD, new ServerBossInfo(null, BossInfo.Color.PINK, BossInfo.Overlay.PROGRESS)));
                        //tracker.sendToAllTracking(e, new SOpenSignMenuPacket(BlockPos.ZERO));
                    }
                }
            })
            .build();
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
