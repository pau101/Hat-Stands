package me.paulf.hatstands.server;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;

public interface Behavior {
    Behavior ABSENT = new Behavior() {};

    default void onName(final PlayerEntity player) {}

    default void onStart() {}

    default void onUpdate() { }

    default void onEnd() {}

    default void onSave(final CompoundNBT compound) {}

    default void onLoad(final CompoundNBT compound) {}
}
