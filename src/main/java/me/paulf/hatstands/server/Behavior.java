package me.paulf.hatstands.server;

import net.minecraft.entity.player.EntityPlayer;

public interface Behavior {
	Behavior ABSENT = new Behavior () {};

	default void onCreate(final EntityPlayer player) {}

	default void onStart() {}

	default void onUpdate() {}

	default void onEnd() {}
}
