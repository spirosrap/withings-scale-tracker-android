package com.spiros.withingsscaletracker;

final class ScaleMeasurement {
    final Metric metric;
    final double value;

    ScaleMeasurement(Metric metric, double value) {
        this.metric = metric;
        this.value = value;
    }

    String formattedValue() {
        return metric.format(value);
    }
}
