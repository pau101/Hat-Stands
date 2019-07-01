package me.paulf.hatstands.util;

public final class Mth {
    private Mth() {}

    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0D);

    private static final float RAD_TO_DEG = (float) (180.0D / Math.PI);

    public static float toRadians(final double degrees) {
        return DEG_TO_RAD * (float) degrees;
    }

    public static float toDegrees(final double radians) {
        return RAD_TO_DEG * (float) radians;
    }

    public static float wrap(final float a, final float b) {
        return (a % b + b) % b;
    }
}
