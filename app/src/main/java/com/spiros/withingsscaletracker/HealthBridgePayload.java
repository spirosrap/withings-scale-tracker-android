package com.spiros.withingsscaletracker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class HealthBridgePayload {
    final HealthSnapshot snapshot;
    final List<SleepSummary> sleepSummaries;
    final List<HealthSnapshot> dailySnapshots;
    final List<RunningWorkout> runningWorkouts;

    HealthBridgePayload(
        HealthSnapshot snapshot,
        List<SleepSummary> sleepSummaries,
        List<HealthSnapshot> dailySnapshots,
        List<RunningWorkout> runningWorkouts
    ) {
        this.snapshot = snapshot;
        this.sleepSummaries = sleepSummaries;
        this.dailySnapshots = dailySnapshots;
        this.runningWorkouts = runningWorkouts;
    }

    static HealthBridgePayload fromBridgeJson(JSONObject json) throws Exception {
        HealthSnapshot snapshot = null;
        if (json.has("snapshot") && !json.isNull("snapshot")) {
            snapshot = HealthSnapshot.fromBridgeJson(json.getJSONObject("snapshot"));
        }

        ArrayList<SleepSummary> summaries = new ArrayList<>();
        JSONArray sleepJson = json.optJSONArray("sleepSummaries");
        if (sleepJson != null) {
            for (int index = 0; index < sleepJson.length(); index++) {
                summaries.add(SleepSummary.fromBridgeJson(sleepJson.getJSONObject(index)));
            }
        }

        ArrayList<HealthSnapshot> dailySnapshots = new ArrayList<>();
        JSONArray dailyJson = json.optJSONArray("dailySnapshots");
        if (dailyJson != null) {
            for (int index = 0; index < dailyJson.length(); index++) {
                dailySnapshots.add(HealthSnapshot.fromBridgeJson(dailyJson.getJSONObject(index)));
            }
        }

        ArrayList<RunningWorkout> workouts = new ArrayList<>();
        JSONArray workoutJson = json.optJSONArray("runningWorkouts");
        if (workoutJson != null) {
            for (int index = 0; index < workoutJson.length(); index++) {
                workouts.add(RunningWorkout.fromBridgeJson(workoutJson.getJSONObject(index)));
            }
        }

        return new HealthBridgePayload(snapshot, summaries, dailySnapshots, workouts);
    }
}
