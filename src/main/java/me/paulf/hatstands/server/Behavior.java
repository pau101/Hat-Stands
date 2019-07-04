package me.paulf.hatstands.server;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

public interface Behavior {
	Behavior ABSENT = new Behavior () {};

	default void onName(final EntityPlayer player) {}

	default void onStart() {}

	default void onUpdate() {}

	default void onEnd() {}

	default void onSave(final NBTTagCompound compound) {}

	default void onLoad(final NBTTagCompound compound) {}
}
