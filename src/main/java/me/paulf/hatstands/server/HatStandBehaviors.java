package me.paulf.hatstands.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import me.paulf.hatstands.HatStands;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.effect.LightningBoltEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.EggEntity;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.play.server.SOpenWindowPacket;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.server.ServerChunkProvider;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.function.Function;

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
                void run() {
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
                        tracker.sendToAllTracking(e, new SOpenWindowPacket(1, ContainerType.ENCHANTMENT, null));
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

    abstract static class RunnableBehavior implements Behavior {
        final HatStandEntity stand;
        boolean powered;
        BlockPos pos;

        public RunnableBehavior(final HatStandEntity stand) {
            this.stand = stand;
            this.powered = false;
            this.pos = BlockPos.ZERO;
        }

        abstract void run();

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
}
