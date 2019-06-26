package me.paulf.hatstands.client.model.entity;

import net.minecraft.client.model.ModelBiped;

public final class ModelHatStandArmor extends ModelBiped {
	public ModelHatStandArmor() {
		this(0.0F);
	}

	public ModelHatStandArmor(final float modelSize) {
		this(modelSize, 64, 32);
	}

	private ModelHatStandArmor(final float modelSize, final int textureWidth, final int textureHeight) {
		super(modelSize, 0.0F, textureWidth, textureHeight);
	}
}
