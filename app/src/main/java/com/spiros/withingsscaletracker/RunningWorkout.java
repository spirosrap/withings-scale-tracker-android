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
    final Double runPaceSecondsPerKilometer;
    final Double runStartOffsetSeconds;
    final Double runEndOffsetSeconds;

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
        Double runPaceSecondsPerKilometer,
        Double runStartOffsetSeconds,
        Double runEndOffsetSeconds
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
        this.runPaceSecondsPerKilometer = runPaceSecondsPerKilometer;
        this.runStartOffsetSeconds = runStartOffsetSeconds;
        this.runEndOffsetSeconds = runEndOffsetSeconds;
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
        putNullable(json, "runPaceSecondsPerKilometer", runPaceSecondsPerKilometer);
        putNullable(json, "runStartOffsetSeconds", runStartOffsetSeconds);
        putNullable(json, "runEndOffsetSeconds", runEndOffsetSeconds);
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
            optionalDouble(json, "runPaceSecondsPerKilometer"),
            optionalDouble(json, "runStartOffsetSeconds"),
            optionalDouble(json, "runEndOffsetSeconds")
        );
    }

    static RunningWorkout fromBridgeJson(JSONObject json) {
        ArrayList<RouteSample> routeSamples = routeSamples(json.optJSONArray("routePoints"));
        long startEpochSeconds = bridgeDateSeconds(json, "startDate");
        long endEpochSeconds = bridgeDateSeconds(json, "endDate");
        RunSegment segment = bestRunSegment(routeSamples, startEpochSeconds, endEpochSeconds, 45 * 60.0);
        return new RunningWorkout(
            json.optString("id", ""),
            startEpochSeconds,
            endEpochSeconds,
            json.optDouble("durationSeconds", 0),
            optionalDouble(json, "totalDistanceMeters"),
            optionalDouble(json, "activeEnergyKilocalories"),
            optionalDouble(json, "averageHeartRate"),
            json.optString("source", ""),
            routeSamples.size(),
            routeDistanceMeters(routeSamples),
            segment == null ? null : segment.secondsPerKilometer,
            segment == null ? null : segment.startOffsetSeconds,
            segment == null ? null : segment.endOffsetSeconds
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

    String formattedRunPace() {
        if (runPaceSecondsPerKilometer == null) return "--";
        return formatPace(runPaceSecondsPerKilometer);
    }

    String formattedRunWindow() {
        if (runStartOffsetSeconds == null || runEndOffsetSeconds == null) return "--";
        return String.format(
            Locale.US,
            "%.0f-%.0fm",
            runStartOffsetSeconds / 60.0,
            runEndOffsetSeconds / 60.0
        );
    }

    String formattedAverageHeartRate() {
        if (averageHeartRate == null) return "--";
        return String.format(Locale.US, "%.0f bpm", averageHeartRate);
    }

    String formattedRoute() {
        return routePointCount <= 0 ? "No route" : String.format(Locale.US, "%,d", routePointCount);
    }

    Double projectionPaceSecondsPerKilometer() {
        if (runPaceSecondsPerKilometer != null) return runPaceSecondsPerKilometer;
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

    private static RunSegment bestRunSegment(
        List<RouteSample> samples,
        long workoutStartSeconds,
        long workoutEndSeconds,
        double targetDurationSeconds
    ) {
        if (samples.size() < 2 || targetDurationSeconds <= 0) return null;
        if (workoutEndSeconds - workoutStartSeconds < targetDurationSeconds) return null;

        double firstCandidate = Math.max(workoutStartSeconds, samples.get(0).timestampSeconds);
        double lastCandidate = Math.min(workoutEndSeconds, samples.get(samples.size() - 1).timestampSeconds) - targetDurationSeconds;
        if (lastCandidate < firstCandidate) return null;

        RunSegment best = null;
        for (double candidateStart = firstCandidate; candidateStart <= lastCandidate; candidateStart += 5.0) {
            double candidateEnd = candidateStart + targetDurationSeconds;
            Double startDistance = interpolatedDistanceMeters(candidateStart, samples);
            Double endDistance = interpolatedDistanceMeters(candidateEnd, samples);
            if (startDistance == null || endDistance == null) continue;

            double distanceMeters = endDistance - startDistance;
            if (distanceMeters <= 0) continue;
            if (best == null || distanceMeters > best.distanceMeters) {
                best = new RunSegment(
                    candidateStart - workoutStartSeconds,
                    candidateEnd - workoutStartSeconds,
                    distanceMeters,
                    targetDurationSeconds / (distanceMeters / 1000.0)
                );
            }
        }

        return best;
    }

    private static Double interpolatedDistanceMeters(double timestampSeconds, List<RouteSample> samples) {
        RouteSample previous = samples.get(0);
        for (int index = 1; index < samples.size(); index++) {
            RouteSample current = samples.get(index);
            if (current.timestampSeconds >= timestampSeconds) {
                if (current.timestampSeconds == previous.timestampSeconds) {
                    return current.distanceMeters;
                }
                double fraction = (timestampSeconds - previous.timestampSeconds) / (current.timestampSeconds - previous.timestampSeconds);
                return previous.distanceMeters + ((current.distanceMeters - previous.distanceMeters) * fraction);
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

    private static final class RunSegment {
        final double startOffsetSeconds;
        final double endOffsetSeconds;
        final double distanceMeters;
        final double secondsPerKilometer;

        RunSegment(
            double startOffsetSeconds,
            double endOffsetSeconds,
            double distanceMeters,
            double secondsPerKilometer
        ) {
            this.startOffsetSeconds = startOffsetSeconds;
            this.endOffsetSeconds = endOffsetSeconds;
            this.distanceMeters = distanceMeters;
            this.secondsPerKilometer = secondsPerKilometer;
        }
    }
}
