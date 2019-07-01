package me.paulf.hatstands.server.sound;

import me.paulf.hatstands.HatStands;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod.EventBusSubscriber(modid = HatStands.ID)
public final class HatStandsSounds {
    private HatStandsSounds() {}

    private static final SoundEvent NULL = new SoundEvent(new ResourceLocation(HatStands.ID, "null"));

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

    @SubscribeEvent
    public static void onRegister(final RegistryEvent.Register<SoundEvent> event) {
        event.getRegistry().registerAll(
            make("entity.hat_stand.break"),
            make("entity.hat_stand.fall"),
            make("entity.hat_stand.hit"),
            make("entity.hat_stand.place"),
            make("entity.hat_stand.sexysong")
        );
    }

    private static SoundEvent make(final String name) {
        return new SoundEvent(new ResourceLocation(HatStands.ID, name)).setRegistryName(name);
    }
}
