package com.spiros.withingsscaletracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecureStore {
    private static final String KEY_ALIAS = "withings_scale_tracker_key";
    private static final String PREFS = "secure_store";
    private static final String CREDENTIALS = "credentials";
    private static final String TOKEN = "token";
    private static final String OAUTH_STATE = "oauth_state";
    private static final String SCALE_READINGS = "scale_readings";
    private static final String SLEEP_SUMMARIES = "sleep_summaries";
    private static final String HEALTH_SNAPSHOT = "health_snapshot";
    private static final String MAC_BRIDGE_HOST = "mac_bridge_host";

    private final SharedPreferences prefs;

    SecureStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    void saveCredentials(WithingsCredentials credentials) throws Exception {
        JSONObject json = new JSONObject();
        json.put("clientId", credentials.clientId.trim());
        json.put("clientSecret", credentials.clientSecret.trim());
        json.put("redirectUri", credentials.redirectUri.trim());
        putEncrypted(CREDENTIALS, json.toString());
    }

    WithingsCredentials loadCredentials() throws Exception {
        WithingsCredentials credentials = new WithingsCredentials();
        String raw = getEncrypted(CREDENTIALS);
        if (raw == null) return credentials;

        JSONObject json = new JSONObject(raw);
        credentials.clientId = json.optString("clientId", "");
        credentials.clientSecret = json.optString("clientSecret", "");
        credentials.redirectUri = json.optString("redirectUri", credentials.redirectUri);
        return credentials;
    }

    void saveToken(WithingsToken token) throws Exception {
        JSONObject json = new JSONObject();
        json.put("userId", token.userId);
        json.put("accessToken", token.accessToken);
        json.put("refreshToken", token.refreshToken);
        json.put("expiresAtMillis", token.expiresAtMillis);
        json.put("scope", token.scope);
        json.put("tokenType", token.tokenType);
        putEncrypted(TOKEN, json.toString());
    }

    WithingsToken loadToken() throws Exception {
        String raw = getEncrypted(TOKEN);
        if (raw == null) return null;

        JSONObject json = new JSONObject(raw);
        return new WithingsToken(
            json.optString("userId", ""),
            json.optString("accessToken", ""),
            json.optString("refreshToken", ""),
            json.optLong("expiresAtMillis", 0),
            json.optString("scope", ""),
            json.optString("tokenType", "Bearer")
        );
    }

    void clearToken() {
        prefs.edit().remove(TOKEN).apply();
    }

    void saveScaleReadings(Iterable<ScaleReading> readings) throws Exception {
        JSONArray json = new JSONArray();
        for (ScaleReading reading : readings) {
            json.put(reading.toJson());
        }
        putEncrypted(SCALE_READINGS, json.toString());
    }

    java.util.ArrayList<ScaleReading> loadScaleReadings() throws Exception {
        java.util.ArrayList<ScaleReading> readings = new java.util.ArrayList<>();
        String raw = getEncrypted(SCALE_READINGS);
        if (raw == null) return readings;

        JSONArray json = new JSONArray(raw);
        for (int index = 0; index < json.length(); index++) {
            readings.add(ScaleReading.fromStoredJson(json.getJSONObject(index)));
        }
        return readings;
    }

    void saveSleepSummaries(Iterable<SleepSummary> summaries) throws Exception {
        JSONArray json = new JSONArray();
        for (SleepSummary summary : summaries) {
            json.put(summary.toJson());
        }
        putEncrypted(SLEEP_SUMMARIES, json.toString());
    }

    java.util.ArrayList<SleepSummary> loadSleepSummaries() throws Exception {
        java.util.ArrayList<SleepSummary> summaries = new java.util.ArrayList<>();
        String raw = getEncrypted(SLEEP_SUMMARIES);
        if (raw == null) return summaries;

        JSONArray json = new JSONArray(raw);
        for (int index = 0; index < json.length(); index++) {
            summaries.add(SleepSummary.fromStoredJson(json.getJSONObject(index)));
        }
        return summaries;
    }

    void saveHealthSnapshot(HealthSnapshot snapshot) throws Exception {
        putEncrypted(HEALTH_SNAPSHOT, snapshot.toJson().toString());
    }

    HealthSnapshot loadHealthSnapshot() throws Exception {
        String raw = getEncrypted(HEALTH_SNAPSHOT);
        if (raw == null) return null;
        return HealthSnapshot.fromStoredJson(new JSONObject(raw));
    }

    void saveOAuthState(String state) {
        prefs.edit().putString(OAUTH_STATE, state).apply();
    }

    String loadOAuthState() {
        return prefs.getString(OAUTH_STATE, "");
    }

    void clearOAuthState() {
        prefs.edit().remove(OAUTH_STATE).apply();
    }

    void saveMacBridgeHost(String host) {
        prefs.edit().putString(MAC_BRIDGE_HOST, host.trim()).apply();
    }

    String loadMacBridgeHost() {
        return prefs.getString(MAC_BRIDGE_HOST, "");
    }

    private void putEncrypted(String key, String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        JSONObject envelope = new JSONObject();
        envelope.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP));
        envelope.put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP));
        prefs.edit().putString(key, envelope.toString()).apply();
    }

    private String getEncrypted(String key) throws Exception {
        String raw = prefs.getString(key, null);
        if (raw == null) return null;

        JSONObject envelope = new JSONObject(raw);
        byte[] iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(envelope.getString("data"), Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(
            new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        );
        return generator.generateKey();
    }
}
