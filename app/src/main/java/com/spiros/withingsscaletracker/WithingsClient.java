package com.spiros.withingsscaletracker;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class WithingsClient {
    private static final String API_BASE = "https://wbsapi.withings.net";
    private static final String AUTH_BASE = "https://account.withings.com/oauth2_user/authorize2";

    Uri authorizationUri(WithingsCredentials credentials, String state) {
        return Uri.parse(AUTH_BASE).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", credentials.clientId.trim())
            .appendQueryParameter("scope", "user.info,user.metrics")
            .appendQueryParameter("redirect_uri", credentials.redirectUri.trim())
            .appendQueryParameter("state", state)
            .build();
    }

    WithingsToken requestToken(WithingsCredentials credentials, String code) throws Exception {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("action", "requesttoken");
        params.put("client_id", credentials.clientId.trim());
        params.put("redirect_uri", credentials.redirectUri.trim());
        params.put("code", code);
        params.put("grant_type", "authorization_code");
        return requestToken(credentials, params);
    }

    WithingsToken refreshToken(WithingsCredentials credentials, WithingsToken token) throws Exception {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("action", "requesttoken");
        params.put("client_id", credentials.clientId.trim());
        params.put("refresh_token", token.refreshToken);
        params.put("grant_type", "refresh_token");
        return requestToken(credentials, params);
    }

    List<ScaleReading> fetchReadings(String accessToken) throws Exception {
        long end = System.currentTimeMillis() / 1000;
        long start = end - (2L * 365L * 24L * 60L * 60L);

        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("action", "getmeas");
        params.put("category", "1");
        params.put("startdate", Long.toString(start));
        params.put("enddate", Long.toString(end));
        params.put("meastypes", Metric.measureTypeList());

        JSONObject body = postForm(API_BASE + "/measure", params, accessToken);
        JSONArray groups = body.optJSONArray("measuregrps");
        ArrayList<ScaleReading> readings = new ArrayList<>();
        if (groups == null) return readings;

        for (int index = 0; index < groups.length(); index++) {
            JSONObject group = groups.getJSONObject(index);
            long date = flexibleLong(group, "date", 0);
            String id = flexibleString(group, "grpid", Long.toString(date));
            JSONArray measures = group.optJSONArray("measures");
            ArrayList<ScaleMeasurement> measurements = new ArrayList<>();
            if (measures != null) {
                for (int measureIndex = 0; measureIndex < measures.length(); measureIndex++) {
                    JSONObject measure = measures.getJSONObject(measureIndex);
                    Metric metric = Metric.fromType(flexibleInt(measure, "type", -1));
                    if (metric == null) continue;
                    double value = metric.normalizedValue(
                        flexibleDouble(measure, "value", 0),
                        flexibleInt(measure, "unit", 0)
                    );
                    measurements.add(new ScaleMeasurement(metric, value));
                }
            }
            readings.add(new ScaleReading(id, date, measurements));
        }

        return ScaleReading.mergeSameTimestamp(readings);
    }

    List<SleepSummary> fetchSleepSummaries(String accessToken) throws Exception {
        long now = System.currentTimeMillis();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        formatter.setTimeZone(TimeZone.getDefault());

        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("action", "getsummary");
        params.put("startdateymd", formatter.format(new Date(now - 30L * 24L * 60L * 60L * 1000L)));
        params.put("enddateymd", formatter.format(new Date(now)));
        params.put("data_fields", String.join(",",
            "sleep_score",
            "sleep_efficiency",
            "total_sleep_time",
            "total_timeinbed",
            "deepsleepduration",
            "lightsleepduration",
            "remsleepduration",
            "wakeupduration",
            "hr_average",
            "rr_average",
            "snoring",
            "apnea_hypopnea_index"
        ));

        JSONObject body = postForm(API_BASE + "/v2/sleep", params, accessToken);
        JSONArray series = body.optJSONArray("series");
        ArrayList<SleepSummary> summaries = new ArrayList<>();
        if (series == null) return summaries;

        for (int index = 0; index < series.length(); index++) {
            JSONObject item = series.getJSONObject(index);
            JSONObject data = item.optJSONObject("data");
            if (data == null) data = new JSONObject();
            long start = flexibleLong(item, "startdate", 0);
            long end = flexibleLong(item, "enddate", start);
            summaries.add(new SleepSummary(
                flexibleString(item, "id", start + "-" + end),
                start,
                end,
                optionalInt(data, "sleep_score"),
                firstInt(data, "total_sleep_time", "asleepduration"),
                optionalInt(data, "total_timeinbed"),
                optionalInt(data, "deepsleepduration"),
                optionalInt(data, "lightsleepduration"),
                optionalInt(data, "remsleepduration"),
                optionalInt(data, "wakeupduration"),
                optionalDouble(data, "sleep_efficiency"),
                optionalInt(data, "hr_average"),
                optionalInt(data, "rr_average"),
                optionalInt(data, "snoring"),
                optionalDouble(data, "apnea_hypopnea_index")
            ));
        }

        summaries.sort(Comparator.comparingLong((SleepSummary summary) -> summary.startEpochSeconds).reversed());
        return summaries;
    }

    private WithingsToken requestToken(WithingsCredentials credentials, LinkedHashMap<String, String> params) throws Exception {
        String nonce = getNonce(credentials);
        params.put("nonce", nonce);
        LinkedHashMap<String, String> signatureParams = new LinkedHashMap<>();
        signatureParams.put("action", params.get("action"));
        signatureParams.put("client_id", credentials.clientId.trim());
        signatureParams.put("nonce", nonce);
        params.put("signature", sign(signatureParams, credentials.clientSecret.trim()));

        JSONObject body = postForm(API_BASE + "/v2/oauth2", params, null);
        int expiresIn = flexibleInt(body, "expires_in", 3600);
        return new WithingsToken(
            flexibleString(body, "userid", ""),
            body.getString("access_token"),
            body.getString("refresh_token"),
            System.currentTimeMillis() + Math.max(expiresIn - 60, 60) * 1000L,
            body.optString("scope", ""),
            body.optString("token_type", "Bearer")
        );
    }

    private String getNonce(WithingsCredentials credentials) throws Exception {
        String timestamp = Long.toString(System.currentTimeMillis() / 1000);
        LinkedHashMap<String, String> signatureParams = new LinkedHashMap<>();
        signatureParams.put("action", "getnonce");
        signatureParams.put("client_id", credentials.clientId.trim());
        signatureParams.put("timestamp", timestamp);

        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("action", "getnonce");
        params.put("client_id", credentials.clientId.trim());
        params.put("timestamp", timestamp);
        params.put("signature", sign(signatureParams, credentials.clientSecret.trim()));

        return postForm(API_BASE + "/v2/signature", params, null).getString("nonce");
    }

    private String sign(LinkedHashMap<String, String> params, String clientSecret) throws Exception {
        TreeMap<String, String> sorted = new TreeMap<>(params);
        String joined = String.join(",", sorted.values());
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(joined.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte value : digest) {
            hex.append(String.format(Locale.US, "%02x", value));
        }
        return hex.toString();
    }

    private JSONObject postForm(String endpoint, LinkedHashMap<String, String> params, String bearerToken) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("User-Agent", "WithingsScaleTrackerAndroid/1.0");
        if (bearerToken != null) {
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
        }
        connection.setDoOutput(true);
        byte[] body = formEncode(params).getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body);
        }

        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        String response = readAll(stream);
        if (status < 200 || status >= 300) {
            throw new WithingsApiException(status, response);
        }

        JSONObject envelope = new JSONObject(response);
        int apiStatus = envelope.optInt("status", 0);
        if (apiStatus != 0) {
            throw new WithingsApiException(apiStatus, envelope.optString("error", "Withings API status " + apiStatus));
        }
        JSONObject responseBody = envelope.optJSONObject("body");
        if (responseBody == null) throw new WithingsApiException(apiStatus, "Withings response did not include a body.");
        return responseBody;
    }

    private String formEncode(Map<String, String> params) throws Exception {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (builder.length() > 0) builder.append('&');
            builder
                .append(URLEncoder.encode(entry.getKey(), "UTF-8"))
                .append('=')
                .append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        return builder.toString();
    }

    private String readAll(InputStream input) throws Exception {
        if (input == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) builder.append(line);
        return builder.toString();
    }

    private static String flexibleString(JSONObject object, String key, String fallback) {
        Object value = object.opt(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static int flexibleInt(JSONObject object, String key, int fallback) {
        Object value = object.opt(key);
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static long flexibleLong(JSONObject object, String key, long fallback) {
        Object value = object.opt(key);
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static double flexibleDouble(JSONObject object, String key, double fallback) {
        Object value = object.opt(key);
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static Integer optionalInt(JSONObject object, String key) {
        if (!object.has(key) || object.isNull(key)) return null;
        return flexibleInt(object, key, 0);
    }

    private static Integer firstInt(JSONObject object, String firstKey, String secondKey) {
        Integer first = optionalInt(object, firstKey);
        return first != null ? first : optionalInt(object, secondKey);
    }

    private static Double optionalDouble(JSONObject object, String key) {
        if (!object.has(key) || object.isNull(key)) return null;
        return flexibleDouble(object, key, 0);
    }
}

final class WithingsApiException extends Exception {
    final int status;

    WithingsApiException(int status, String message) {
        super("Withings status " + status + ": " + message);
        this.status = status;
    }

    boolean isInsufficientScope() {
        return status == 403 && getMessage() != null && getMessage().toLowerCase(Locale.US).contains("insufficient");
    }

    boolean isInvalidRefreshToken() {
        if (getMessage() == null) return false;
        String normalized = getMessage().toLowerCase(Locale.US).replace(' ', '_');
        return normalized.contains("invalid_refresh_token");
    }
}
