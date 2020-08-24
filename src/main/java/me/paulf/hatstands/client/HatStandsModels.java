package me.paulf.hatstands.client;

import me.paulf.hatstands.HatStands;
import me.paulf.hatstands.server.HatStandEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = HatStands.ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HatStandsModels {
    private HatStandsModels() {}

    @SubscribeEvent
    public static void onRegister(final FMLClientSetupEvent event) {
        RenderingRegistry.registerEntityRenderingHandler(HatStands.EntityTypes.HAT_STAND.get(), HatStandRenderer::new);
    }
}
