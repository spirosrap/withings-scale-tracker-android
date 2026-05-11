package com.spiros.withingsscaletracker;

import org.json.JSONObject;

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

    JSONObject toJson() throws Exception {
        JSONObject json = new JSONObject();
        json.put("metric", metric.type);
        json.put("value", value);
        return json;
    }

    static ScaleMeasurement fromJson(JSONObject json) {
        Metric metric = Metric.fromType(json.optInt("metric", -1));
        if (metric == null) return null;
        return new ScaleMeasurement(metric, json.optDouble("value", 0));
    }
}
