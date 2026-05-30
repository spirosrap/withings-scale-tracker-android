package com.spiros.withingsscaletracker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class RunningWorkout {
    final String id;
    final long startEpochSeconds;
    final long endEpochSeconds;
    final double durationSeconds;
    final Double totalDistanceMeters;
    final Double activeEnergyKilocalories;
    final Double averageHeartRate;
    final String source;
    final int routePointCount;
    final Double routeDistanceMeters;
    final Double mainPaceSecondsPerKilometer;

    RunningWorkout(
        String id,
        long startEpochSeconds,
        long endEpochSeconds,
        double durationSeconds,
        Double totalDistanceMeters,
        Double activeEnergyKilocalories,
        Double averageHeartRate,
        String source,
        int routePointCount,
        Double routeDistanceMeters,
        Double mainPaceSecondsPerKilometer
    ) {
        this.id = id;
        this.startEpochSeconds = startEpochSeconds;
        this.endEpochSeconds = endEpochSeconds;
        this.durationSeconds = durationSeconds;
        this.totalDistanceMeters = totalDistanceMeters;
        this.activeEnergyKilocalories = activeEnergyKilocalories;
        this.averageHeartRate = averageHeartRate;
        this.source = source == null ? "" : source;
        this.routePointCount = routePointCount;
        this.routeDistanceMeters = routeDistanceMeters;
        this.mainPaceSecondsPerKilometer = mainPaceSecondsPerKilometer;
    }

    JSONObject toJson() throws Exception {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("startEpochSeconds", startEpochSeconds);
        json.put("endEpochSeconds", endEpochSeconds);
        json.put("durationSeconds", durationSeconds);
        putNullable(json, "totalDistanceMeters", totalDistanceMeters);
        putNullable(json, "activeEnergyKilocalories", activeEnergyKilocalories);
        putNullable(json, "averageHeartRate", averageHeartRate);
        json.put("source", source);
        json.put("routePointCount", routePointCount);
        putNullable(json, "routeDistanceMeters", routeDistanceMeters);
        putNullable(json, "mainPaceSecondsPerKilometer", mainPaceSecondsPerKilometer);
        return json;
    }

    static RunningWorkout fromStoredJson(JSONObject json) {
        return new RunningWorkout(
            json.optString("id", ""),
            json.optLong("startEpochSeconds", 0),
            json.optLong("endEpochSeconds", 0),
            json.optDouble("durationSeconds", 0),
            optionalDouble(json, "totalDistanceMeters"),
            optionalDouble(json, "activeEnergyKilocalories"),
            optionalDouble(json, "averageHeartRate"),
            json.optString("source", ""),
            json.optInt("routePointCount", 0),
            optionalDouble(json, "routeDistanceMeters"),
            optionalDouble(json, "mainPaceSecondsPerKilometer")
        );
    }

    static RunningWorkout fromBridgeJson(JSONObject json) {
        ArrayList<RouteSample> routeSamples = routeSamples(json.optJSONArray("routePoints"));
        return new RunningWorkout(
            json.optString("id", ""),
            bridgeDateSeconds(json, "startDate"),
            bridgeDateSeconds(json, "endDate"),
            json.optDouble("durationSeconds", 0),
            optionalDouble(json, "totalDistanceMeters"),
            optionalDouble(json, "activeEnergyKilocalories"),
            optionalDouble(json, "averageHeartRate"),
            json.optString("source", ""),
            routeSamples.size(),
            routeDistanceMeters(routeSamples),
            mainPaceSecondsPerKilometer(routeSamples)
        );
    }

    String formattedDate() {
        if (startEpochSeconds <= 0) return "--";
        return DateFormat.getDateInstance(DateFormat.MEDIUM)
            .format(new Date(startEpochSeconds * 1000));
    }

    String formattedStartTime() {
        if (startEpochSeconds <= 0) return "--";
        return DateFormat.getTimeInstance(DateFormat.SHORT)
            .format(new Date(startEpochSeconds * 1000));
    }

    String formattedDistance() {
        Double distance = distanceMeters();
        if (distance == null || distance <= 0) return "--";
        return String.format(Locale.US, "%.2f km", distance / 1000.0);
    }

    String formattedDuration() {
        return formatDuration(durationSeconds);
    }

    String formattedPace() {
        Double distance = distanceMeters();
        if (distance == null || distance <= 0) return "--";
        return formatPace(durationSeconds / (distance / 1000.0));
    }

    String formattedMainPace() {
        if (mainPaceSecondsPerKilometer == null) return "--";
        return formatPace(mainPaceSecondsPerKilometer);
    }

    String formattedAverageHeartRate() {
        if (averageHeartRate == null) return "--";
        return String.format(Locale.US, "%.0f bpm", averageHeartRate);
    }

    String formattedRoute() {
        return routePointCount <= 0 ? "No route" : String.format(Locale.US, "%,d", routePointCount);
    }

    Double projectionPaceSecondsPerKilometer() {
        if (mainPaceSecondsPerKilometer != null) return mainPaceSecondsPerKilometer;
        Double distance = distanceMeters();
        if (distance == null || distance <= 0) return null;
        return durationSeconds / (distance / 1000.0);
    }

    Double distanceMeters() {
        if (totalDistanceMeters != null && totalDistanceMeters > 0) return totalDistanceMeters;
        if (routeDistanceMeters != null && routeDistanceMeters > 0) return routeDistanceMeters;
        return null;
    }

    static String formatDuration(double seconds) {
        int total = Math.max(0, (int) Math.round(seconds));
        int hours = total / 3600;
        int minutes = (total % 3600) / 60;
        int remainingSeconds = total % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainingSeconds);
        }
        return String.format(Locale.US, "%d:%02d", minutes, remainingSeconds);
    }

    static String formatPace(double secondsPerKilometer) {
        int rounded = Math.max(0, (int) Math.round(secondsPerKilometer));
        return String.format(Locale.US, "%d:%02d/km", rounded / 60, rounded % 60);
    }

    static List<RunningWorkout> sortedNewestFirst(List<RunningWorkout> workouts) {
        ArrayList<RunningWorkout> sorted = new ArrayList<>(workouts);
        sorted.sort((left, right) -> Long.compare(right.startEpochSeconds, left.startEpochSeconds));
        return sorted;
    }

    private static ArrayList<RouteSample> routeSamples(JSONArray json) {
        ArrayList<RouteSample> samples = new ArrayList<>();
        if (json == null) return samples;
        for (int index = 0; index < json.length(); index++) {
            JSONObject point = json.optJSONObject(index);
            if (point == null) continue;
            Double distance = optionalDouble(point, "distanceFromStartMeters");
            if (distance == null) continue;
            samples.add(new RouteSample(bridgeDateSeconds(point, "timestamp"), distance));
        }
        samples.sort(Comparator.comparingLong(sample -> sample.timestampSeconds));
        return samples;
    }

    private static Double routeDistanceMeters(List<RouteSample> samples) {
        if (samples.size() < 2) return null;
        double distance = samples.get(samples.size() - 1).distanceMeters - samples.get(0).distanceMeters;
        return distance > 0 ? distance : null;
    }

    private static Double mainPaceSecondsPerKilometer(List<RouteSample> samples) {
        Double routeDistance = routeDistanceMeters(samples);
        if (routeDistance == null || routeDistance <= 3000) return null;

        double firstDistance = samples.get(0).distanceMeters;
        double lastDistance = samples.get(samples.size() - 1).distanceMeters;
        double startDistance = firstDistance + 1000;
        double endDistance = lastDistance - 1000;
        Double startTime = interpolatedTimeSeconds(startDistance, samples);
        Double endTime = interpolatedTimeSeconds(endDistance, samples);
        if (startTime == null || endTime == null || endTime <= startTime) return null;
        return (endTime - startTime) / ((endDistance - startDistance) / 1000.0);
    }

    private static Double interpolatedTimeSeconds(double distance, List<RouteSample> samples) {
        RouteSample previous = samples.get(0);
        for (int index = 1; index < samples.size(); index++) {
            RouteSample current = samples.get(index);
            if (current.distanceMeters >= distance) {
                if (current.distanceMeters == previous.distanceMeters) {
                    return (double) current.timestampSeconds;
                }
                double fraction = (distance - previous.distanceMeters) / (current.distanceMeters - previous.distanceMeters);
                return previous.timestampSeconds + ((current.timestampSeconds - previous.timestampSeconds) * fraction);
            }
            previous = current;
        }
        return null;
    }

    private static long bridgeDateSeconds(JSONObject json, String key) {
        Object raw = json.opt(key);
        if (raw instanceof Number) {
            double value = ((Number) raw).doubleValue();
            if (value > 1_000_000_000_000.0) return (long) (value / 1000.0);
            if (value > 1_000_000_000.0) return (long) value;
            return (long) (value + 978_307_200.0);
        }
        return 0;
    }

    private static Double optionalDouble(JSONObject json, String key) {
        if (!json.has(key) || json.isNull(key)) return null;
        Object raw = json.opt(key);
        if (raw instanceof Number) return ((Number) raw).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(raw));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void putNullable(JSONObject json, String key, Object value) throws Exception {
        if (value != null) json.put(key, value);
    }

    private static final class RouteSample {
        final long timestampSeconds;
        final double distanceMeters;

        RouteSample(long timestampSeconds, double distanceMeters) {
            this.timestampSeconds = timestampSeconds;
            this.distanceMeters = distanceMeters;
        }
    }
}
