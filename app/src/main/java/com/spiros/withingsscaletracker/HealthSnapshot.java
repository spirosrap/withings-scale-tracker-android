package com.spiros.withingsscaletracker;

import org.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class HealthSnapshot {
    final String id;
    final long startEpochSeconds;
    final long endEpochSeconds;
    final long generatedAtEpochSeconds;
    final long importedAtEpochSeconds;
    final Double steps;
    final Double activeEnergyKilocalories;
    final Double walkingRunningDistanceMeters;
    final Double exerciseMinutes;
    final Double flightsClimbed;
    final Double latestHeartRate;
    final Double restingHeartRate;
    final Double latestRespiratoryRate;
    final Double oxygenSaturationPercent;

    HealthSnapshot(
        String id,
        long startEpochSeconds,
        long endEpochSeconds,
        long generatedAtEpochSeconds,
        long importedAtEpochSeconds,
        Double steps,
        Double activeEnergyKilocalories,
        Double walkingRunningDistanceMeters,
        Double exerciseMinutes,
        Double flightsClimbed,
        Double latestHeartRate,
        Double restingHeartRate,
        Double latestRespiratoryRate,
        Double oxygenSaturationPercent
    ) {
        this.id = id;
        this.startEpochSeconds = startEpochSeconds;
        this.endEpochSeconds = endEpochSeconds;
        this.generatedAtEpochSeconds = generatedAtEpochSeconds;
        this.importedAtEpochSeconds = importedAtEpochSeconds;
        this.steps = steps;
        this.activeEnergyKilocalories = activeEnergyKilocalories;
        this.walkingRunningDistanceMeters = walkingRunningDistanceMeters;
        this.exerciseMinutes = exerciseMinutes;
        this.flightsClimbed = flightsClimbed;
        this.latestHeartRate = latestHeartRate;
        this.restingHeartRate = restingHeartRate;
        this.latestRespiratoryRate = latestRespiratoryRate;
        this.oxygenSaturationPercent = oxygenSaturationPercent;
    }

    String formattedGeneratedAt() {
        if (generatedAtEpochSeconds <= 0) return "--";
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(new Date(generatedAtEpochSeconds * 1000));
    }

    String formattedImportedAt() {
        if (importedAtEpochSeconds <= 0) return "--";
        return new SimpleDateFormat("MMM d, yyyy HH:mm:ss", Locale.getDefault())
            .format(new Date(importedAtEpochSeconds * 1000));
    }

    String formattedDate() {
        if (startEpochSeconds <= 0) return "Today";
        return DateFormat.getDateInstance(DateFormat.MEDIUM)
            .format(new Date(startEpochSeconds * 1000));
    }

    boolean hasValues() {
        return steps != null ||
            activeEnergyKilocalories != null ||
            walkingRunningDistanceMeters != null ||
            exerciseMinutes != null ||
            flightsClimbed != null ||
            latestHeartRate != null ||
            restingHeartRate != null ||
            latestRespiratoryRate != null ||
            oxygenSaturationPercent != null;
    }

    JSONObject toJson() throws Exception {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("startEpochSeconds", startEpochSeconds);
        json.put("endEpochSeconds", endEpochSeconds);
        json.put("generatedAtEpochSeconds", generatedAtEpochSeconds);
        json.put("importedAtEpochSeconds", importedAtEpochSeconds);
        putNullable(json, "steps", steps);
        putNullable(json, "activeEnergyKilocalories", activeEnergyKilocalories);
        putNullable(json, "walkingRunningDistanceMeters", walkingRunningDistanceMeters);
        putNullable(json, "exerciseMinutes", exerciseMinutes);
        putNullable(json, "flightsClimbed", flightsClimbed);
        putNullable(json, "latestHeartRate", latestHeartRate);
        putNullable(json, "restingHeartRate", restingHeartRate);
        putNullable(json, "latestRespiratoryRate", latestRespiratoryRate);
        putNullable(json, "oxygenSaturationPercent", oxygenSaturationPercent);
        return json;
    }

    static HealthSnapshot fromStoredJson(JSONObject json) {
        return new HealthSnapshot(
            json.optString("id", ""),
            json.optLong("startEpochSeconds", 0),
            json.optLong("endEpochSeconds", 0),
            json.optLong("generatedAtEpochSeconds", 0),
            json.optLong("importedAtEpochSeconds", 0),
            optionalDouble(json, "steps"),
            optionalDouble(json, "activeEnergyKilocalories"),
            optionalDouble(json, "walkingRunningDistanceMeters"),
            optionalDouble(json, "exerciseMinutes"),
            optionalDouble(json, "flightsClimbed"),
            optionalDouble(json, "latestHeartRate"),
            optionalDouble(json, "restingHeartRate"),
            optionalDouble(json, "latestRespiratoryRate"),
            optionalDouble(json, "oxygenSaturationPercent")
        );
    }

    static HealthSnapshot fromBridgeJson(JSONObject json) {
        JSONObject metrics = json.optJSONObject("metrics");
        if (metrics == null) metrics = new JSONObject();
        return new HealthSnapshot(
            json.optString("id", ""),
            bridgeDateSeconds(json, "startDate"),
            bridgeDateSeconds(json, "endDate"),
            bridgeDateSeconds(json, "generatedAt"),
            0,
            optionalDouble(metrics, "steps"),
            optionalDouble(metrics, "activeEnergyKilocalories"),
            optionalDouble(metrics, "walkingRunningDistanceMeters"),
            optionalDouble(metrics, "exerciseMinutes"),
            optionalDouble(metrics, "flightsClimbed"),
            optionalDouble(metrics, "latestHeartRate"),
            optionalDouble(metrics, "restingHeartRate"),
            optionalDouble(metrics, "latestRespiratoryRate"),
            optionalDouble(metrics, "oxygenSaturationPercent")
        );
    }

    HealthSnapshot importedNow() {
        return new HealthSnapshot(
            id,
            startEpochSeconds,
            endEpochSeconds,
            generatedAtEpochSeconds,
            System.currentTimeMillis() / 1000,
            steps,
            activeEnergyKilocalories,
            walkingRunningDistanceMeters,
            exerciseMinutes,
            flightsClimbed,
            latestHeartRate,
            restingHeartRate,
            latestRespiratoryRate,
            oxygenSaturationPercent
        );
    }

    static String formatWhole(Double value) {
        if (value == null) return "--";
        return String.format(Locale.US, "%,.0f", value);
    }

    static String formatCalories(Double value) {
        return value == null ? "--" : formatWhole(value) + " kcal";
    }

    static String formatMinutes(Double value) {
        return value == null ? "--" : formatWhole(value) + " min";
    }

    static String formatDistance(Double meters) {
        if (meters == null) return "--";
        if (meters >= 1000) {
            return String.format(Locale.US, "%.2f km", meters / 1000.0);
        }
        return formatWhole(meters) + " m";
    }

    static String formatBpm(Double value) {
        return value == null ? "--" : formatWhole(value) + " bpm";
    }

    static String formatRespiratoryRate(Double value) {
        return value == null ? "--" : formatWhole(value) + "/min";
    }

    static String formatPercent(Double value) {
        return value == null ? "--" : formatWhole(value) + "%";
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
}
