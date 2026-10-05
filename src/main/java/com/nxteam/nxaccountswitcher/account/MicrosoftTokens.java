package com.nxteam.nxaccountswitcher.account;

public final class MicrosoftTokens {
    private final String accessToken;
    private final String refreshToken;

    public MicrosoftTokens(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }
}
