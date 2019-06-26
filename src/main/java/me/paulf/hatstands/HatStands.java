package me.paulf.hatstands;

import me.paulf.hatstands.server.entity.HatStandEntity;
import me.paulf.hatstands.server.item.HatStandItem;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
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

	private static final String NAME = "hat_stand";

	@GameRegistry.ObjectHolder(HatStands.ID + ":" + NAME)
	public static final Item ITEM = Items.AIR;

	@SubscribeEvent
	public static void onItemRegister(final RegistryEvent.Register<Item> event) {
		event.getRegistry().register(new HatStandItem()
			.setTranslationKey(NAME)
			.setMaxStackSize(16)
			.setRegistryName(NAME)
		);
	}

	@SubscribeEvent
	public static void onEntityRegister(final RegistryEvent.Register<EntityEntry> event) {
		event.getRegistry().register(EntityEntryBuilder.create()
			.entity(HatStandEntity.class)
			.factory(HatStandEntity::new)
			.id(new ResourceLocation(ID, NAME), 0)
			.name(NAME)
			.tracker(160, 3, true)
			.build());
	}
}
