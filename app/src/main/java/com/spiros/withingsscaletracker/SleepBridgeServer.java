package com.spiros.withingsscaletracker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class SleepBridgeServer {
    interface Listener {
        void onSleepSummaries(List<SleepSummary> summaries) throws Exception;
        void onHealthPayload(HealthBridgePayload payload) throws Exception;
    }

    private final int port;
    private final Listener listener;
    private ServerSocket serverSocket;
    private Thread thread;
    private volatile boolean running;

    SleepBridgeServer(int port, Listener listener) {
        this.port = port;
        this.listener = listener;
    }

    void start() throws Exception {
        if (running) return;
        serverSocket = new ServerSocket(port);
        running = true;
        thread = new Thread(this::acceptLoop, "SleepBridgeServer");
        thread.start();
    }

    void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                handle(socket);
            } catch (Exception ignored) {
                if (running) {
                    // Keep the bridge alive after malformed requests.
                }
            }
        }
    }

    private void handle(Socket socket) {
        try (Socket closeableSocket = socket) {
            InputStream input = closeableSocket.getInputStream();
            String requestLine = readLine(input);
            if (requestLine == null) {
                send(closeableSocket, "400 Bad Request", "Missing request line.");
                return;
            }

            int contentLength = 0;
            String line;
            while ((line = readLine(input)) != null && !line.isEmpty()) {
                int separator = line.indexOf(':');
                if (separator < 0) continue;
                String name = line.substring(0, separator).trim().toLowerCase(Locale.US);
                String value = line.substring(separator + 1).trim();
                if ("content-length".equals(name)) {
                    contentLength = Integer.parseInt(value);
                }
            }

            boolean isSleepRoute = requestLine.startsWith("POST /apple-health/sleep ");
            boolean isSnapshotRoute = requestLine.startsWith("POST /apple-health/snapshot ");
            if (!isSleepRoute && !isSnapshotRoute) {
                send(closeableSocket, "404 Not Found", "Unknown bridge route.");
                return;
            }

            String body = new String(readBody(input, Math.max(contentLength, 0)), StandardCharsets.UTF_8);
            if (isSnapshotRoute) {
                HealthBridgePayload payload = HealthBridgePayload.fromBridgeJson(new JSONObject(body));
                listener.onHealthPayload(payload);
                send(closeableSocket, "200 OK", "Imported Apple Health payload.");
            } else {
                JSONArray json = new JSONArray(body);
                ArrayList<SleepSummary> summaries = new ArrayList<>();
                for (int index = 0; index < json.length(); index++) {
                    summaries.add(SleepSummary.fromBridgeJson(json.getJSONObject(index)));
                }

                listener.onSleepSummaries(summaries);
                send(closeableSocket, "200 OK", "Imported " + summaries.size() + " sleep summaries.");
            }
        } catch (Exception error) {
            try {
                send(socket, "400 Bad Request", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
            } catch (Exception ignored) {
            }
        }
    }

    private String readLine(InputStream input) throws Exception {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        boolean didRead = false;
        int value;
        while ((value = input.read()) != -1) {
            didRead = true;
            if (value == '\n') break;
            if (value != '\r') line.write(value);
        }
        if (!didRead && line.size() == 0) return null;
        return line.toString(StandardCharsets.UTF_8.name());
    }

    private byte[] readBody(InputStream input, int contentLength) throws Exception {
        byte[] body = new byte[contentLength];
        int offset = 0;
        while (offset < body.length) {
            int read = input.read(body, offset, body.length - offset);
            if (read < 0) break;
            offset += read;
        }
        if (offset == body.length) return body;

        byte[] partial = new byte[offset];
        System.arraycopy(body, 0, partial, 0, offset);
        return partial;
    }

    private void send(Socket socket, String status, String body) throws Exception {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        response.write(("HTTP/1.1 " + status + "\r\n").getBytes(StandardCharsets.UTF_8));
        response.write("Content-Type: text/plain; charset=utf-8\r\n".getBytes(StandardCharsets.UTF_8));
        response.write(("Content-Length: " + bodyBytes.length + "\r\n").getBytes(StandardCharsets.UTF_8));
        response.write("Connection: close\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        response.write(bodyBytes);
        OutputStream output = socket.getOutputStream();
        output.write(response.toByteArray());
        output.flush();
    }
}
