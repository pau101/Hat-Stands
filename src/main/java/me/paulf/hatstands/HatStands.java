package me.paulf.hatstands;

import me.paulf.hatstands.server.HatStandEntity;
import me.paulf.hatstands.server.HatStandItem;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

@Mod(HatStands.ID)
public final class HatStands {
    public static final String ID = "hatstands";

    public HatStands() {
        final IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        Items.REG.register(bus);
        EntityTypes.REG.register(bus);
        SoundEvents.REG.register(bus);
    }

    public static final class Items {
        private static final DeferredRegister<Item> REG = new DeferredRegister<>(ForgeRegistries.ITEMS, HatStands.ID);

        public static final RegistryObject<Item> HAT_STAND = REG.register("hat_stand", () -> new HatStandItem(new Item.Properties().group(ItemGroup.DECORATIONS).maxStackSize(16)));
    }

    public static final class EntityTypes {
        private static final DeferredRegister<EntityType<?>> REG = new DeferredRegister<>(ForgeRegistries.ENTITIES, HatStands.ID);

        public static final RegistryObject<EntityType<HatStandEntity>> HAT_STAND = REG.register("hat_stand", () ->
            EntityType.Builder.create(HatStandEntity::new, EntityClassification.MISC)
                .size(9.0F / 16.0F, 11.0F / 16.0F)
                .setTrackingRange(160)
                .setUpdateInterval(3)
                .setShouldReceiveVelocityUpdates(true)
                .build(ID + ":hat_stand")
        );
    }

    public static final class SoundEvents {
        private static final DeferredRegister<SoundEvent> REG = new DeferredRegister<>(ForgeRegistries.SOUND_EVENTS, HatStands.ID);

        public static final RegistryObject<SoundEvent> ENTITY_HAT_STAND_BREAK = create("entity.hat_stand.break");

        public static final RegistryObject<SoundEvent> ENTITY_HAT_STAND_FALL = create("entity.hat_stand.fall");

        public static final RegistryObject<SoundEvent> ENTITY_HAT_STAND_HIT = create("entity.hat_stand.hit");

        public static final RegistryObject<SoundEvent> ENTITY_HAT_STAND_PLACE = create("entity.hat_stand.place");

        public static final RegistryObject<SoundEvent> ENTITY_HAT_STAND_SEXYSONG = create("entity.hat_stand.sexysong");

        private static RegistryObject<SoundEvent> create(final String name) {
            return REG.register(name, () -> new SoundEvent(new ResourceLocation(HatStands.ID, name)));
        }
    }
}
