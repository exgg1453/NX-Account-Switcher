package com.nxteam.nxaccountswitcher.account;

public final class MinecraftLoginResult {
    private final String username;
    private final String uuid;
    private final String accessToken;
    private final String refreshToken;

    public MinecraftLoginResult(String username, String uuid, String accessToken, String refreshToken) {
        this.username = username;
        this.uuid = uuid;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public String getUsername() {
        return username;
    }

    public String getUuid() {
        return uuid;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }
}
