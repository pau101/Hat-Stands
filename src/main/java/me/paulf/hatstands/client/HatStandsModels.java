package me.paulf.hatstands.client;

import me.paulf.hatstands.HatStands;
import me.paulf.hatstands.client.renderer.entity.HatStandRenderer;
import me.paulf.hatstands.server.entity.HatStandEntity;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.Objects;

@Mod.EventBusSubscriber(value = Side.CLIENT, modid = HatStands.ID)
public final class HatStandsModels {
    private HatStandsModels() {}

    @SubscribeEvent
    public static void onRegister(final ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(HatStands.ITEM, 0, new ModelResourceLocation(Objects.requireNonNull(HatStands.ITEM.getRegistryName(), "registry name"), "inventory"));
        RenderingRegistry.registerEntityRenderingHandler(HatStandEntity.class, HatStandRenderer::new);

    }
}
