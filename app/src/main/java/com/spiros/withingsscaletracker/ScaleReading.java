package com.spiros.withingsscaletracker;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class ScaleReading {
    final String id;
    final long epochSeconds;
    final List<ScaleMeasurement> measurements;

    ScaleReading(String id, long epochSeconds, List<ScaleMeasurement> measurements) {
        this.id = id;
        this.epochSeconds = epochSeconds;
        this.measurements = measurements;
    }

    ScaleMeasurement weight() {
        for (ScaleMeasurement measurement : measurements) {
            if (measurement.metric == Metric.WEIGHT) return measurement;
        }
        return null;
    }

    List<ScaleMeasurement> sortedMeasurements() {
        ArrayList<ScaleMeasurement> copy = new ArrayList<>(measurements);
        copy.sort((left, right) -> {
            if (left.metric.priority != right.metric.priority) {
                return Integer.compare(left.metric.priority, right.metric.priority);
            }
            return left.metric.title.compareTo(right.metric.title);
        });
        return copy;
    }

    String formattedDateTime() {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(new Date(epochSeconds * 1000));
    }

    String formattedTime() {
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(epochSeconds * 1000));
    }

    static List<ScaleReading> mergeSameTimestamp(List<ScaleReading> readings) {
        TreeMap<Long, List<ScaleReading>> buckets = new TreeMap<>(Collections.reverseOrder());
        for (ScaleReading reading : readings) {
            if (!reading.measurements.isEmpty()) {
                buckets.computeIfAbsent(reading.epochSeconds, ignored -> new ArrayList<>()).add(reading);
            }
        }

        ArrayList<ScaleReading> merged = new ArrayList<>();
        for (Map.Entry<Long, List<ScaleReading>> entry : buckets.entrySet()) {
            List<ScaleReading> group = entry.getValue();
            group.sort(Comparator
                .comparingInt((ScaleReading reading) -> reading.measurements.size())
                .reversed()
                .thenComparing(reading -> reading.id));

            LinkedHashMap<Metric, ScaleMeasurement> byMetric = new LinkedHashMap<>();
            ArrayList<String> ids = new ArrayList<>();
            for (ScaleReading reading : group) {
                ids.add(reading.id);
                for (ScaleMeasurement measurement : reading.measurements) {
                    byMetric.putIfAbsent(measurement.metric, measurement);
                }
            }
            Collections.sort(ids);
            merged.add(new ScaleReading(String.join("+", ids), entry.getKey(), new ArrayList<>(byMetric.values())));
        }
        return merged;
    }
}
