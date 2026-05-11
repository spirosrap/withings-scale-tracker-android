package com.spiros.withingsscaletracker;

import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;

final class SleepSummary {
    final String id;
    final long startEpochSeconds;
    final long endEpochSeconds;
    final Integer score;
    final Integer totalSleepSeconds;
    final Integer totalTimeInBedSeconds;
    final Integer deepSeconds;
    final Integer lightSeconds;
    final Integer remSeconds;
    final Integer awakeSeconds;
    final Double efficiency;
    final Integer averageHeartRate;
    final Integer averageRespiratoryRate;
    final Integer snoringSeconds;
    final Double apneaHypopneaIndex;

    SleepSummary(
        String id,
        long startEpochSeconds,
        long endEpochSeconds,
        Integer score,
        Integer totalSleepSeconds,
        Integer totalTimeInBedSeconds,
        Integer deepSeconds,
        Integer lightSeconds,
        Integer remSeconds,
        Integer awakeSeconds,
        Double efficiency,
        Integer averageHeartRate,
        Integer averageRespiratoryRate,
        Integer snoringSeconds,
        Double apneaHypopneaIndex
    ) {
        this.id = id;
        this.startEpochSeconds = startEpochSeconds;
        this.endEpochSeconds = endEpochSeconds;
        this.score = score;
        this.totalSleepSeconds = totalSleepSeconds;
        this.totalTimeInBedSeconds = totalTimeInBedSeconds;
        this.deepSeconds = deepSeconds;
        this.lightSeconds = lightSeconds;
        this.remSeconds = remSeconds;
        this.awakeSeconds = awakeSeconds;
        this.efficiency = efficiency;
        this.averageHeartRate = averageHeartRate;
        this.averageRespiratoryRate = averageRespiratoryRate;
        this.snoringSeconds = snoringSeconds;
        this.apneaHypopneaIndex = apneaHypopneaIndex;
    }

    String formattedDate() {
        return DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(startEpochSeconds * 1000));
    }

    static String formatDuration(Integer seconds) {
        if (seconds == null) return "--";
        int clamped = Math.max(seconds, 0);
        int hours = clamped / 3600;
        int minutes = (clamped % 3600) / 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    String formattedEfficiency() {
        if (efficiency == null) return "--";
        double percent = efficiency <= 1 ? efficiency * 100 : efficiency;
        return Math.round(percent) + "%";
    }

    JSONObject toJson() throws Exception {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("startEpochSeconds", startEpochSeconds);
        json.put("endEpochSeconds", endEpochSeconds);
        putNullable(json, "score", score);
        putNullable(json, "totalSleepSeconds", totalSleepSeconds);
        putNullable(json, "totalTimeInBedSeconds", totalTimeInBedSeconds);
        putNullable(json, "deepSeconds", deepSeconds);
        putNullable(json, "lightSeconds", lightSeconds);
        putNullable(json, "remSeconds", remSeconds);
        putNullable(json, "awakeSeconds", awakeSeconds);
        putNullable(json, "efficiency", efficiency);
        putNullable(json, "averageHeartRate", averageHeartRate);
        putNullable(json, "averageRespiratoryRate", averageRespiratoryRate);
        putNullable(json, "snoringSeconds", snoringSeconds);
        putNullable(json, "apneaHypopneaIndex", apneaHypopneaIndex);
        return json;
    }

    static SleepSummary fromStoredJson(JSONObject json) {
        return new SleepSummary(
            json.optString("id", ""),
            json.optLong("startEpochSeconds", 0),
            json.optLong("endEpochSeconds", 0),
            optionalInt(json, "score"),
            optionalInt(json, "totalSleepSeconds"),
            optionalInt(json, "totalTimeInBedSeconds"),
            optionalInt(json, "deepSeconds"),
            optionalInt(json, "lightSeconds"),
            optionalInt(json, "remSeconds"),
            optionalInt(json, "awakeSeconds"),
            optionalDouble(json, "efficiency"),
            optionalInt(json, "averageHeartRate"),
            optionalInt(json, "averageRespiratoryRate"),
            optionalInt(json, "snoringSeconds"),
            optionalDouble(json, "apneaHypopneaIndex")
        );
    }

    static SleepSummary fromBridgeJson(JSONObject json) {
        JSONObject data = json.optJSONObject("data");
        if (data == null) data = new JSONObject();
        long start = bridgeDateSeconds(json, "startDate");
        long end = bridgeDateSeconds(json, "endDate");
        Integer totalSleep = firstInt(data, "total_sleep_time", "asleepduration");
        Integer stageTotal = sumPositive(
            optionalInt(data, "deepsleepduration"),
            optionalInt(data, "lightsleepduration"),
            optionalInt(data, "remsleepduration")
        );
        return new SleepSummary(
            json.optString("id", start + "-" + end),
            start,
            end,
            optionalInt(data, "sleep_score"),
            totalSleep != null ? totalSleep : stageTotal,
            optionalInt(data, "total_timeinbed"),
            optionalInt(data, "deepsleepduration"),
            optionalInt(data, "lightsleepduration"),
            optionalInt(data, "remsleepduration"),
            firstInt(data, "wakeupduration", "waso"),
            optionalDouble(data, "sleep_efficiency"),
            optionalInt(data, "hr_average"),
            optionalInt(data, "rr_average"),
            optionalInt(data, "snoring"),
            optionalDouble(data, "apnea_hypopnea_index")
        );
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

    private static Integer firstInt(JSONObject json, String... keys) {
        for (String key : keys) {
            Integer value = optionalInt(json, key);
            if (value != null) return value;
        }
        return null;
    }

    private static Integer sumPositive(Integer... values) {
        int total = 0;
        for (Integer value : values) {
            if (value != null && value > 0) total += value;
        }
        return total > 0 ? total : null;
    }

    private static Integer optionalInt(JSONObject json, String key) {
        if (!json.has(key) || json.isNull(key)) return null;
        Object raw = json.opt(key);
        if (raw instanceof Number) return (int) Math.round(((Number) raw).doubleValue());
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException ignored) {
            return null;
        }
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
