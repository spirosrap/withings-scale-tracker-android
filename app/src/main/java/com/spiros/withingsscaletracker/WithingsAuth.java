package com.spiros.withingsscaletracker;

final class WithingsCredentials {
    String clientId = "";
    String clientSecret = "";
    String redirectUri = "withings-scale-tracker-android://oauth/callback";

    boolean isComplete() {
        return !clientId.trim().isEmpty() && !clientSecret.trim().isEmpty() && !redirectUri.trim().isEmpty();
    }
}

final class WithingsToken {
    final String userId;
    final String accessToken;
    final String refreshToken;
    final long expiresAtMillis;
    final String scope;
    final String tokenType;

    WithingsToken(String userId, String accessToken, String refreshToken, long expiresAtMillis, String scope, String tokenType) {
        this.userId = userId;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAtMillis = expiresAtMillis;
        this.scope = scope;
        this.tokenType = tokenType;
    }

    boolean needsRefresh() {
        return expiresAtMillis - System.currentTimeMillis() < 120_000;
    }
}
