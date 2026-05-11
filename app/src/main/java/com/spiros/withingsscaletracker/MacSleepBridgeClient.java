package com.spiros.withingsscaletracker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class MacSleepBridgeClient {
    HealthBridgePayload fetchPayload(String host) throws Exception {
        try {
            String response = get(host, "/apple-health/snapshot");
            return HealthBridgePayload.fromBridgeJson(new JSONObject(response));
        } catch (Exception exception) {
            return new HealthBridgePayload(null, fetch(host));
        }
    }

    List<SleepSummary> fetch(String host) throws Exception {
        String response = get(host, "/apple-health/sleep");
        JSONArray json = new JSONArray(response);
        ArrayList<SleepSummary> summaries = new ArrayList<>();
        for (int index = 0; index < json.length(); index++) {
            summaries.add(SleepSummary.fromBridgeJson(json.getJSONObject(index)));
        }
        return summaries;
    }

    private String get(String host, String path) throws Exception {
        String normalizedHost = host.trim()
            .replace("http://", "")
            .replace("https://", "");
        while (normalizedHost.endsWith("/")) {
            normalizedHost = normalizedHost.substring(0, normalizedHost.length() - 1);
        }
        if (normalizedHost.isEmpty()) {
            throw new IllegalArgumentException("Enter the Mac bridge host.");
        }
        if (!normalizedHost.contains(":")) {
            normalizedHost += ":8766";
        }

        HttpURLConnection connection = (HttpURLConnection) new URL("http://" + normalizedHost + path).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(8000);

        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        String response = readAll(stream);
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("Mac bridge HTTP " + status + ": " + response);
        }

        return response;
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        return builder.toString();
    }
}
