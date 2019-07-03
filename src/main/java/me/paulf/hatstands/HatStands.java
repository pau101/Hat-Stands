package me.paulf.hatstands;

import me.paulf.hatstands.server.HatStandEntity;
import me.paulf.hatstands.server.HatStandItem;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod(modid = HatStands.ID)
@Mod.EventBusSubscriber(modid = HatStands.ID)
public final class HatStands {
    public static final String ID = "hatstands";

    @SubscribeEvent
    public static void onItemRegister(final RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new HatStandItem()
            .setTranslationKey("hat_stand")
            .setMaxStackSize(16)
            .setRegistryName("hat_stand")
        );
    }

    @SubscribeEvent
    public static void onEntityRegister(final RegistryEvent.Register<EntityEntry> event) {
        event.getRegistry().register(EntityEntryBuilder.create()
            .entity(HatStandEntity.class)
            .factory(HatStandEntity::new)
            .id(new ResourceLocation(ID, "hat_stand"), 0)
            .name("hat_stand")
            .tracker(160, 3, true)
            .build());
    }

    @SubscribeEvent
    public static void onSoundRegister(final RegistryEvent.Register<SoundEvent> event) {
        event.getRegistry().registerAll(
            createSound("entity.hat_stand.break"),
            createSound("entity.hat_stand.fall"),
            createSound("entity.hat_stand.hit"),
            createSound("entity.hat_stand.place"),
            createSound("entity.hat_stand.sexysong")
        );
    }

    private static SoundEvent createSound(final String name) {
        return new SoundEvent(new ResourceLocation(ID, name)).setRegistryName(name);
    }

    public static final class Items {
        private static final Item NULL = net.minecraft.init.Items.AIR;

        @GameRegistry.ObjectHolder(HatStands.ID + ":hat_stand")
        public static final Item HAT_STAND = NULL;

        private Items() {}
    }

    public static final class SoundEvents {
        private static final SoundEvent NULL = net.minecraft.init.SoundEvents.ENTITY_PLAYER_HURT;

        @GameRegistry.ObjectHolder(HatStands.ID + ":entity.hat_stand.break")
        public static final SoundEvent ENTITY_HAT_STAND_BREAK = NULL;

        @GameRegistry.ObjectHolder(HatStands.ID + ":entity.hat_stand.fall")
        public static final SoundEvent ENTITY_HAT_STAND_FALL = NULL;

        @GameRegistry.ObjectHolder(HatStands.ID + ":entity.hat_stand.hit")
        public static final SoundEvent ENTITY_HAT_STAND_HIT = NULL;

        @GameRegistry.ObjectHolder(HatStands.ID + ":entity.hat_stand.place")
        public static final SoundEvent ENTITY_HAT_STAND_PLACE = NULL;

        @GameRegistry.ObjectHolder(HatStands.ID + ":entity.hat_stand.sexysong")
        public static final SoundEvent ENTITY_HAT_STAND_SEXYSONG = NULL;

        private SoundEvents() {}
    }
}
