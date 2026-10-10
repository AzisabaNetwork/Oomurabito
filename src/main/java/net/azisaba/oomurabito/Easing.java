package net.azisaba.oomurabito;

final class Easing {
    private Easing() {
    }

    static double clamp(double progress) {
        return Math.max(0.0, Math.min(1.0, progress));
    }

    static double in(double progress) {
        double t = clamp(progress);
        return t * t * t;
    }

    static double out(double progress) {
        double t = 1.0 - clamp(progress);
        return 1.0 - t * t * t;
    }

    static double inOut(double progress) {
        double t = clamp(progress);
        return t < 0.5 ? 4.0 * t * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 3.0) / 2.0;
    }
}
