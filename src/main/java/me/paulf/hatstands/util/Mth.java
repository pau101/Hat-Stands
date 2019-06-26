package me.paulf.hatstands.util;

public final class Mth {
	private Mth() {}

	private static final float DEG_TO_RAD = (float) (Math.PI / 180.0D);

	public static float toRadians(final float degrees) {
		return DEG_TO_RAD * degrees;
	}
}
