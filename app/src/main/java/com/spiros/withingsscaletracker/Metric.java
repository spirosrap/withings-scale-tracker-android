package com.spiros.withingsscaletracker;

import java.util.Locale;

enum Metric {
    WEIGHT(1, "Weight", "kg", 1, 0),
    FAT_FREE_MASS(5, "Fat-Free", "kg", 1, 6),
    FAT_RATIO(6, "Body Fat", "%", 1, 1),
    FAT_MASS(8, "Fat Mass", "kg", 1, 3),
    HEART_RATE(11, "Heart Rate", "bpm", 0, 4),
    MUSCLE_MASS(76, "Muscle", "kg", 1, 2),
    WATER_MASS(77, "Water", "kg", 1, 7),
    BONE_MASS(88, "Bone", "kg", 1, 8),
    PULSE_WAVE_VELOCITY(91, "PWV", "m/s", 1, 9),
    VASCULAR_AGE(155, "Vascular Age", "years", 0, 10),
    VISCERAL_FAT(170, "Visceral Fat", "", 1, 11),
    BASAL_METABOLIC_RATE(226, "BMR", "kcal", 0, 12);

    final int type;
    final String title;
    final String unit;
    final int fractionDigits;
    final int priority;

    Metric(int type, String title, String unit, int fractionDigits, int priority) {
        this.type = type;
        this.title = title;
        this.unit = unit;
        this.fractionDigits = fractionDigits;
        this.priority = priority;
    }

    static Metric fromType(int type) {
        for (Metric metric : values()) {
            if (metric.type == type) return metric;
        }
        return null;
    }

    static String measureTypeList() {
        StringBuilder builder = new StringBuilder();
        for (Metric metric : values()) {
            if (builder.length() > 0) builder.append(',');
            builder.append(metric.type);
        }
        return builder.toString();
    }

    double normalizedValue(double value, int unitPower) {
        return value * Math.pow(10, unitPower);
    }

    String format(double value) {
        String number = String.format(Locale.US, "%." + fractionDigits + "f", value);
        return unit.isEmpty() ? number : number + " " + unit;
    }
}
