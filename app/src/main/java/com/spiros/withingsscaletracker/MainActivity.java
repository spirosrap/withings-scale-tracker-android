package com.spiros.withingsscaletracker;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int SLEEP_BRIDGE_PORT = 8766;
    private static final int BG = 0xFF0B0F12;
    private static final int PANEL = 0xFF182229;
    private static final int PANEL_ALT = 0xFF202B34;
    private static final int TEXT = 0xFFF5F7FA;
    private static final int MUTED = 0xFFA6AFB8;
    private static final int ACCENT = 0xFF47DCE3;
    private static final int BLUE = 0xFF2388FF;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SecureStore secureStore;
    private final WithingsClient client = new WithingsClient();
    private SleepBridgeServer sleepBridgeServer;
    private final MacSleepBridgeClient macSleepBridgeClient = new MacSleepBridgeClient();
    private WithingsCredentials credentials = new WithingsCredentials();
    private WithingsToken token;
    private final ArrayList<ScaleReading> readings = new ArrayList<>();
    private final ArrayList<SleepSummary> sleepSummaries = new ArrayList<>();
    private HealthSnapshot healthSnapshot;
    private String status = "Not configured";
    private String error;
    private String sleepNote;
    private String healthNote;
    private String bridgeStatus = "Bridge not started";
    private String macBridgeHost = "";
    private boolean loading;
    private Tab selectedTab = Tab.SCALE;
    private long lastAutomaticBridgeImportMillis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        secureStore = new SecureStore(this);
        loadStoredState();
        startSleepBridge();
        handleOAuthCallback(getIntent());
        render();
        if (token != null) refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        maybeImportFromMacAutomatically();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleOAuthCallback(intent);
    }

    @Override
    protected void onDestroy() {
        if (sleepBridgeServer != null) sleepBridgeServer.stop();
        executor.shutdown();
        super.onDestroy();
    }

    private void loadStoredState() {
        try {
            credentials = secureStore.loadCredentials();
            token = secureStore.loadToken();
            readings.clear();
            readings.addAll(secureStore.loadScaleReadings());
            sleepSummaries.clear();
            sleepSummaries.addAll(secureStore.loadSleepSummaries());
            healthSnapshot = secureStore.loadHealthSnapshot();
            macBridgeHost = secureStore.loadMacBridgeHost();
            status = token != null ? "Connected" : (credentials.isComplete() ? "Needs authorization" : "Not configured");
        } catch (Exception exception) {
            error = exception.getMessage();
            status = "Storage error";
        }
    }

    private void startSleepBridge() {
        try {
            sleepBridgeServer = new SleepBridgeServer(SLEEP_BRIDGE_PORT, new SleepBridgeServer.Listener() {
                @Override
                public void onSleepSummaries(List<SleepSummary> summaries) {
                    runOnUiThread(() -> importSleepSummaries(summaries, "Imported Apple Health sleep from iPhone."));
                }

                @Override
                public void onHealthPayload(HealthBridgePayload payload) {
                    runOnUiThread(() -> importHealthPayload(payload, "Imported Apple Health data from iPhone."));
                }
            });
            sleepBridgeServer.start();
            bridgeStatus = "Listening on " + bridgeHostHint() + ":" + SLEEP_BRIDGE_PORT;
        } catch (Exception exception) {
            bridgeStatus = "Bridge unavailable: " + exception.getMessage();
        }
    }

    private void handleOAuthCallback(Intent intent) {
        Uri uri = intent == null ? null : intent.getData();
        if (uri == null || !"withings-scale-tracker-android".equals(uri.getScheme())) return;

        String expectedState = secureStore.loadOAuthState();
        String returnedState = uri.getQueryParameter("state");
        String code = uri.getQueryParameter("code");
        String oauthError = uri.getQueryParameter("error");
        secureStore.clearOAuthState();

        if (oauthError != null) {
            error = "Withings authorization error: " + oauthError;
            render();
            return;
        }

        if (expectedState == null || expectedState.isEmpty() || !expectedState.equals(returnedState)) {
            error = "Withings authorization state did not match.";
            render();
            return;
        }

        if (code == null || code.isEmpty()) {
            error = "Withings did not return an authorization code.";
            render();
            return;
        }

        loading = true;
        status = "Authorizing";
        render();
        executor.execute(() -> {
            try {
                WithingsToken newToken = client.requestToken(credentials, code);
                secureStore.saveToken(newToken);
                token = newToken;
                error = null;
                status = "Connected";
                runOnUiThread(() -> {
                    render();
                    refresh();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    loading = false;
                    error = exception.getMessage();
                    status = "Authorization failed";
                    render();
                });
            }
        });
    }

    private void connect() {
        try {
            if (!credentials.isComplete()) {
                error = "Enter the Withings Client ID and Client Secret first.";
                render();
                return;
            }
            secureStore.saveCredentials(credentials);
            String state = UUID.randomUUID().toString();
            secureStore.saveOAuthState(state);
            startActivity(new Intent(Intent.ACTION_VIEW, client.authorizationUri(credentials, state)));
        } catch (Exception exception) {
            error = exception.getMessage();
            render();
        }
    }

    private void refresh() {
        if (token == null) {
            status = credentials.isComplete() ? "Needs authorization" : "Not configured";
            render();
            return;
        }

        loading = true;
        status = "Refreshing";
        error = null;
        render();
        executor.execute(() -> {
            try {
                WithingsToken activeToken = token;
                if (activeToken.needsRefresh()) {
                    try {
                        activeToken = client.refreshToken(credentials, activeToken);
                        secureStore.saveToken(activeToken);
                        token = activeToken;
                    } catch (WithingsApiException exception) {
                        if (exception.isInvalidRefreshToken()) {
                            secureStore.clearToken();
                            token = null;
                            runOnUiThread(() -> {
                                loading = false;
                                status = credentials.isComplete() ? "Needs authorization" : "Not configured";
                                error = "Withings login expired. Connect Withings again.";
                                render();
                                maybeImportFromMacAutomatically();
                            });
                            return;
                        }
                        throw exception;
                    }
                }

                List<ScaleReading> fetchedReadings = client.fetchReadings(activeToken.accessToken);
                ArrayList<SleepSummary> fetchedSleep = new ArrayList<>();
                String fetchedSleepNote = null;
                try {
                    fetchedSleep.addAll(client.fetchSleepSummaries(activeToken.accessToken));
                } catch (WithingsApiException exception) {
                    if (exception.isInsufficientScope()) {
                        fetchedSleepNote = "Withings sleep scope is unavailable for this app.";
                    } else {
                        throw exception;
                    }
                }

                String finalSleepNote = fetchedSleepNote;
                runOnUiThread(() -> {
                    readings.clear();
                    readings.addAll(fetchedReadings);
                    if (!fetchedSleep.isEmpty()) {
                        mergeSleepSummaries(fetchedSleep);
                        saveSleepCache();
                    }
                    if (finalSleepNote != null && !sleepSummaries.isEmpty()) {
                        sleepNote = finalSleepNote + " Showing Apple Health sleep imported from iPhone.";
                    } else {
                        sleepNote = finalSleepNote;
                    }
                    loading = false;
                    status = "Updated " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new java.util.Date());
                    render();
                    maybeImportFromMacAutomatically();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    loading = false;
                    status = "Error";
                    error = exception.getMessage();
                    render();
                    maybeImportFromMacAutomatically();
                });
            }
        });
    }

    private void disconnect() {
        secureStore.clearToken();
        token = null;
        readings.clear();
        status = credentials.isComplete() ? "Needs authorization" : "Not configured";
        render();
    }

    private void importSleepSummaries(List<SleepSummary> imported, String note) {
        if (imported.isEmpty()) {
            sleepNote = "Bridge is reachable. No sleep summaries were sent.";
            status = "Bridge checked " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new java.util.Date());
            render();
            return;
        }
        mergeSleepSummaries(imported);
        saveSleepCache();
        sleepNote = note;
        status = "Sleep updated " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new java.util.Date());
        render();
    }

    private void importSleepFromMac(String host) {
        importSleepFromMac(host, false);
    }

    private void importSleepFromMac(String host, boolean automatic) {
        String trimmedHost = host.trim();
        secureStore.saveMacBridgeHost(trimmedHost);
        macBridgeHost = trimmedHost;
        loading = true;
        status = automatic ? "Auto-importing from Mac" : "Importing from Mac";
        error = null;
        render();
        executor.execute(() -> {
            try {
                HealthBridgePayload payload = macSleepBridgeClient.fetchPayload(trimmedHost);
                List<ScaleReading> importedReadings = macSleepBridgeClient.fetchScaleReadings(trimmedHost);
                runOnUiThread(() -> {
                    loading = false;
                    importMacBridgeData(payload, importedReadings);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    loading = false;
                    error = exception.getMessage();
                    status = "Import failed";
                    render();
                });
            }
        });
    }

    private void maybeImportFromMacAutomatically() {
        if (loading || macBridgeHost.trim().isEmpty()) return;

        long now = System.currentTimeMillis();
        if (now - lastAutomaticBridgeImportMillis < 5 * 60 * 1000) return;

        lastAutomaticBridgeImportMillis = now;
        importSleepFromMac(macBridgeHost, true);
    }

    private void importHealthPayload(HealthBridgePayload payload, String note) {
        importHealthPayload(payload, note, false);
    }

    private void importMacBridgeData(HealthBridgePayload payload, List<ScaleReading> importedReadings) {
        if (!importedReadings.isEmpty()) {
            readings.clear();
            readings.addAll(importedReadings);
            saveScaleCache();
        }

        error = null;
        importHealthPayload(payload, "Imported Apple Health data from Mac bridge.", !importedReadings.isEmpty());
    }

    private void importHealthPayload(HealthBridgePayload payload, String note, boolean importedScale) {
        boolean importedSnapshot = payload.snapshot != null;
        boolean importedSleep = !payload.sleepSummaries.isEmpty();
        if (payload.snapshot != null) {
            healthSnapshot = payload.snapshot.importedNow();
            saveHealthSnapshot();
        }
        if (importedSleep) {
            mergeSleepSummaries(payload.sleepSummaries);
            saveSleepCache();
        }

        if (!importedSnapshot && !importedSleep && !importedScale) {
            sleepNote = "Bridge is reachable. No Apple Health data was returned.";
            healthNote = sleepNote;
            status = "Bridge checked " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new java.util.Date());
            render();
            return;
        }

        if (importedSnapshot) {
            sleepNote = note;
            healthNote = importedScale ? "Imported scale and Apple Health data from Mac bridge." : note;
            status = importedScale ? "Mac import updated " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new java.util.Date()) : "Health updated " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new java.util.Date());
        } else if (importedScale) {
            healthNote = "Imported scale data from Mac bridge. No Health snapshot has been sent from the iPhone yet.";
            status = "Scale updated " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new java.util.Date());
        } else {
            sleepNote = "Imported Apple Health sleep from Mac bridge.";
            healthNote = "Imported sleep from Mac bridge. No Health snapshot has been sent from the iPhone yet.";
            status = "Sleep updated " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new java.util.Date());
        }
        render();
    }

    private void mergeSleepSummaries(List<SleepSummary> imported) {
        LinkedHashMap<String, SleepSummary> merged = new LinkedHashMap<>();
        for (SleepSummary summary : sleepSummaries) {
            merged.put(summary.id, summary);
        }
        for (SleepSummary summary : imported) {
            merged.put(summary.id, summary);
        }
        sleepSummaries.clear();
        sleepSummaries.addAll(merged.values());
        sleepSummaries.sort((left, right) -> Long.compare(right.startEpochSeconds, left.startEpochSeconds));
    }

    private void saveSleepCache() {
        try {
            secureStore.saveSleepSummaries(sleepSummaries);
        } catch (Exception exception) {
            error = exception.getMessage();
        }
    }

    private void saveScaleCache() {
        try {
            secureStore.saveScaleReadings(readings);
        } catch (Exception exception) {
            error = exception.getMessage();
        }
    }

    private void saveHealthSnapshot() {
        try {
            secureStore.saveHealthSnapshot(healthSnapshot);
        } catch (Exception exception) {
            error = exception.getMessage();
        }
    }


    private void render() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(14), systemBarHeight("status_bar_height") + dp(10), dp(14), dp(10));

        root.addView(header());
        root.addView(tabBar());

        ScrollView scrollView = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(12), 0, dp(12));
        scrollView.addView(content);

        if (error != null && !error.isEmpty()) {
            content.addView(statusPanel("Error", error));
        }

        switch (selectedTab) {
            case SCALE:
                renderScale(content);
                break;
            case HISTORY:
                renderHistory(content);
                break;
            case SLEEP:
                renderSleep(content);
                break;
            case HEALTH:
                renderHealth(content);
                break;
            case SETTINGS:
                renderSettings(content);
                break;
        }

        root.addView(scrollView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1
        ));
        root.addView(footer());
        setContentView(root);
    }

    private View header() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(10));

        LinearLayout titleColumn = new LinearLayout(this);
        titleColumn.setOrientation(LinearLayout.VERTICAL);

        ScaleMeasurement latestWeight = latestWeight();
        TextView title = text(latestWeight == null ? "Scale" : latestWeight.formattedValue(), 22, TEXT, true);
        TextView subtitle = text(status, 12, MUTED, false);
        titleColumn.addView(title);
        titleColumn.addView(subtitle);

        row.addView(titleColumn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        if (loading) {
            row.addView(text("Refreshing", 12, ACCENT, true));
        }
        return row;
    }

    private View tabBar() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, 0, dp(8));
        for (Tab tab : Tab.values()) {
            Button button = new Button(this);
            button.setText(tab.title);
            button.setAllCaps(false);
            button.setTextSize(12);
            button.setMinWidth(0);
            button.setPadding(0, 0, 0, 0);
            button.setTextColor(selectedTab == tab ? TEXT : MUTED);
            button.setBackgroundColor(selectedTab == tab ? BLUE : PANEL_ALT);
            button.setOnClickListener(view -> {
                selectedTab = tab;
                render();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1);
            params.setMargins(dp(2), 0, dp(2), 0);
            row.addView(button, params);
        }
        return row;
    }

    private View footer() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        Button refresh = new Button(this);
        refresh.setText("Refresh");
        refresh.setAllCaps(false);
        refresh.setEnabled(!loading && token != null);
        refresh.setOnClickListener(view -> refresh());
        row.addView(refresh);

        TextView spacer = new TextView(this);
        row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));

        Button settings = new Button(this);
        settings.setText("Settings");
        settings.setAllCaps(false);
        settings.setOnClickListener(view -> {
            selectedTab = Tab.SETTINGS;
            render();
        });
        row.addView(settings);
        return row;
    }

    private void renderScale(LinearLayout content) {
        ScaleReading latest = readings.isEmpty() ? null : readings.get(0);
        content.addView(sectionTitle("Latest"));
        if (latest == null) {
            content.addView(statusPanel("No Scale Data", "Connect Withings, then refresh."));
            return;
        }

        LinearLayout grid = grid();
        List<ScaleMeasurement> measurements = latest.sortedMeasurements();
        for (int i = 0; i < Math.min(measurements.size(), 8); i++) {
            grid.addView(metricCard(measurements.get(i)), gridCellParams());
        }
        content.addView(grid);

        content.addView(sectionTitle("Recent"));
        for (int i = 0; i < Math.min(readings.size(), 5); i++) {
            content.addView(readingRow(readings.get(i)));
        }
    }

    private void renderHistory(LinearLayout content) {
        content.addView(sectionTitle("History"));
        if (readings.isEmpty()) {
            content.addView(statusPanel("No History", "Connect Withings, then refresh."));
            return;
        }
        for (ScaleReading reading : readings) {
            content.addView(readingRow(reading));
        }
    }

    private void renderSleep(LinearLayout content) {
        content.addView(statusPanel(
            "Mac Bridge",
            "Send sleep and today's Apple Health snapshot from the iPhone to the Mac app first, then import it here."
        ));

        EditText macHost = editText("Mac bridge host", macBridgeHost, false);
        content.addView(macHost);

        Button importFromMac = new Button(this);
        importFromMac.setText("Import from Mac");
        importFromMac.setAllCaps(false);
        importFromMac.setEnabled(!loading);
        importFromMac.setOnClickListener(view -> importSleepFromMac(macHost.getText().toString()));
        content.addView(importFromMac);

        content.addView(statusPanel("Direct Receiver", bridgeStatus + "\nDirect iPhone to Pixel can fail while VPN routing is active."));

        if (sleepNote != null && !sleepNote.isEmpty()) {
            content.addView(statusPanel("Sleep Scope", sleepNote));
        }

        if (sleepSummaries.isEmpty()) {
            content.addView(statusPanel("No Sleep Data", "Send Apple Health sleep from the iPhone, or refresh if Withings sleep access becomes available."));
            return;
        }

        SleepSummary summary = sleepSummaries.get(0);
        content.addView(sectionTitle("Sleep  " + summary.formattedDate()));
        LinearLayout grid = grid();
        if (summary.score != null) grid.addView(valueCard("Score", Integer.toString(summary.score)), gridCellParams());
        grid.addView(valueCard("Duration", SleepSummary.formatDuration(summary.totalSleepSeconds)), gridCellParams());
        grid.addView(valueCard("Efficiency", summary.formattedEfficiency()), gridCellParams());
        grid.addView(valueCard("Awake", SleepSummary.formatDuration(summary.awakeSeconds)), gridCellParams());
        grid.addView(valueCard("Deep", SleepSummary.formatDuration(summary.deepSeconds)), gridCellParams());
        grid.addView(valueCard("Light", SleepSummary.formatDuration(summary.lightSeconds)), gridCellParams());
        grid.addView(valueCard("REM", SleepSummary.formatDuration(summary.remSeconds)), gridCellParams());
        if (summary.averageHeartRate != null) grid.addView(valueCard("Avg HR", summary.averageHeartRate + " bpm"), gridCellParams());
        if (summary.averageRespiratoryRate != null) grid.addView(valueCard("Resp.", summary.averageRespiratoryRate + "/min"), gridCellParams());
        if (summary.snoringSeconds != null) grid.addView(valueCard("Snoring", SleepSummary.formatDuration(summary.snoringSeconds)), gridCellParams());
        if (summary.apneaHypopneaIndex != null) {
            grid.addView(valueCard("AHI", String.format(Locale.US, "%.1f", summary.apneaHypopneaIndex)), gridCellParams());
        }
        content.addView(grid);

        content.addView(sectionTitle("Recent Sleep"));
        for (int i = 0; i < Math.min(sleepSummaries.size(), 6); i++) {
            SleepSummary item = sleepSummaries.get(i);
            content.addView(simpleRow(item.formattedDate(), SleepSummary.formatDuration(item.totalSleepSeconds)));
        }
    }

    private void renderHealth(LinearLayout content) {
        content.addView(statusPanel(
            "Apple Health Snapshot",
            "This is the latest iPhone Health snapshot imported through the Mac bridge. It updates when the iPhone app sends fresh data to the Mac."
        ));

        EditText macHost = editText("Mac bridge host", macBridgeHost, false);
        content.addView(macHost);

        Button importFromMac = new Button(this);
        importFromMac.setText("Import from Mac");
        importFromMac.setAllCaps(false);
        importFromMac.setEnabled(!loading);
        importFromMac.setOnClickListener(view -> importSleepFromMac(macHost.getText().toString()));
        content.addView(importFromMac);

        if (healthNote != null && !healthNote.isEmpty()) {
            content.addView(statusPanel("Import", healthNote));
        }

        if (healthSnapshot == null) {
            content.addView(statusPanel("No Health Snapshot", "Open the iPhone app, allow the new Health categories, then send to the Mac bridge."));
            return;
        }

        content.addView(sectionTitle("Today  " + healthSnapshot.formattedDate()));
        content.addView(statusPanel("Last Synced", healthSnapshot.formattedImportedAt()));
        content.addView(statusPanel("iPhone Snapshot", healthSnapshot.formattedGeneratedAt()));

        if (!healthSnapshot.hasValues()) {
            content.addView(statusPanel("No Values", "The snapshot was imported, but Apple Health did not return values for the requested categories yet."));
            return;
        }

        LinearLayout grid = grid();
        if (healthSnapshot.steps != null) {
            grid.addView(valueCard("Steps", HealthSnapshot.formatWhole(healthSnapshot.steps)), gridCellParams());
        }
        if (healthSnapshot.activeEnergyKilocalories != null) {
            grid.addView(valueCard("Active Energy", HealthSnapshot.formatCalories(healthSnapshot.activeEnergyKilocalories)), gridCellParams());
        }
        if (healthSnapshot.walkingRunningDistanceMeters != null) {
            grid.addView(valueCard("Distance", HealthSnapshot.formatDistance(healthSnapshot.walkingRunningDistanceMeters)), gridCellParams());
        }
        if (healthSnapshot.exerciseMinutes != null) {
            grid.addView(valueCard("Exercise", HealthSnapshot.formatMinutes(healthSnapshot.exerciseMinutes)), gridCellParams());
        }
        if (healthSnapshot.flightsClimbed != null) {
            grid.addView(valueCard("Flights", HealthSnapshot.formatWhole(healthSnapshot.flightsClimbed)), gridCellParams());
        }
        if (healthSnapshot.latestHeartRate != null) {
            grid.addView(valueCard("Heart Rate", HealthSnapshot.formatBpm(healthSnapshot.latestHeartRate)), gridCellParams());
        }
        if (healthSnapshot.restingHeartRate != null) {
            grid.addView(valueCard("Resting HR", HealthSnapshot.formatBpm(healthSnapshot.restingHeartRate)), gridCellParams());
        }
        if (healthSnapshot.latestRespiratoryRate != null) {
            grid.addView(valueCard("Resp.", HealthSnapshot.formatRespiratoryRate(healthSnapshot.latestRespiratoryRate)), gridCellParams());
        }
        if (healthSnapshot.oxygenSaturationPercent != null) {
            grid.addView(valueCard("Blood Oxygen", HealthSnapshot.formatPercent(healthSnapshot.oxygenSaturationPercent)), gridCellParams());
        }
        content.addView(grid);
    }

    private void renderSettings(LinearLayout content) {
        content.addView(sectionTitle("Withings Cloud"));
        EditText clientId = editText("Client ID", credentials.clientId, false);
        EditText clientSecret = editText("Client Secret", credentials.clientSecret, true);
        EditText redirectUri = editText("Redirect URI", credentials.redirectUri, false);
        content.addView(clientId);
        content.addView(clientSecret);
        content.addView(redirectUri);
        content.addView(statusPanel("Redirect URL", "Add withings-scale-tracker-android://oauth/callback in the Withings developer dashboard."));

        Button save = new Button(this);
        save.setText("Save Credentials");
        save.setAllCaps(false);
        save.setOnClickListener(view -> {
            credentials.clientId = clientId.getText().toString();
            credentials.clientSecret = clientSecret.getText().toString();
            credentials.redirectUri = redirectUri.getText().toString();
            try {
                secureStore.saveCredentials(credentials);
                error = null;
                status = token != null ? "Connected" : "Needs authorization";
            } catch (Exception exception) {
                error = exception.getMessage();
            }
            render();
        });
        content.addView(save);

        Button connect = new Button(this);
        connect.setText("Connect Withings");
        connect.setAllCaps(false);
        connect.setOnClickListener(view -> {
            credentials.clientId = clientId.getText().toString();
            credentials.clientSecret = clientSecret.getText().toString();
            credentials.redirectUri = redirectUri.getText().toString();
            connect();
        });
        content.addView(connect);

        Button disconnect = new Button(this);
        disconnect.setText("Disconnect");
        disconnect.setAllCaps(false);
        disconnect.setEnabled(token != null);
        disconnect.setOnClickListener(view -> disconnect());
        content.addView(disconnect);
    }

    private String bridgeHostHint() {
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!networkInterface.isUp() || networkInterface.isLoopback()) continue;
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "this Pixel's Wi-Fi IP";
    }

    private ScaleMeasurement latestWeight() {
        for (ScaleReading reading : readings) {
            ScaleMeasurement weight = reading.weight();
            if (weight != null) return weight;
        }
        return null;
    }

    private View metricCard(ScaleMeasurement measurement) {
        return valueCard(measurement.metric.title, measurement.formattedValue());
    }

    private View valueCard(String title, String value) {
        LinearLayout card = card();
        card.addView(text(title, 12, MUTED, false));
        card.addView(text(value, 20, TEXT, true));
        return card;
    }

    private View readingRow(ScaleReading reading) {
        ScaleMeasurement weight = reading.weight();
        return simpleRow(reading.formattedDateTime(), weight == null ? "" : weight.formattedValue());
    }

    private View simpleRow(String left, String right) {
        LinearLayout row = card();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text(left, 14, TEXT, true), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(text(right, 14, TEXT, false));
        return row;
    }

    private View statusPanel(String title, String message) {
        LinearLayout panel = card();
        panel.addView(text(title, 15, TEXT, true));
        panel.addView(text(message, 12, MUTED, false));
        return panel;
    }

    private TextView sectionTitle(String title) {
        TextView view = text(title, 18, TEXT, true);
        view.setPadding(0, dp(10), 0, dp(8));
        return view;
    }

    private LinearLayout grid() {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        return grid;
    }

    private LinearLayout.LayoutParams gridCellParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(8));
        return params;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(PANEL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(params);
        return card;
    }

    private EditText editText(String hint, String value, boolean secure) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setText(value);
        editText.setTextColor(TEXT);
        editText.setHintTextColor(MUTED);
        editText.setSingleLine(true);
        editText.setInputType(secure ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT);
        editText.setPadding(dp(10), dp(8), dp(10), dp(8));
        return editText;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(color);
        if (bold) textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return textView;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int systemBarHeight(String resourceName) {
        int resourceId = getResources().getIdentifier(resourceName, "dimen", "android");
        if (resourceId == 0) return 0;
        return getResources().getDimensionPixelSize(resourceId);
    }

    private enum Tab {
        SCALE("Scale"),
        HISTORY("History"),
        SLEEP("Sleep"),
        HEALTH("Health"),
        SETTINGS("Settings");

        final String title;

        Tab(String title) {
            this.title = title;
        }
    }
}
