package com.nxteam.nxaccountswitcher.account;

public class Account {
    public enum Type {
        MICROSOFT,
        OFFLINE
    }

    private Type type;
    private String username;
    private String uuid;
    private String refreshToken;

    public Account() {
    }

    public Account(Type type, String username, String uuid, String refreshToken) {
        this.type = type;
        this.username = username;
        this.uuid = uuid;
        this.refreshToken = refreshToken;
    }

    public static Account offline(String username) {
        return new Account(Type.OFFLINE, username, SessionManager.offlineUuid(username), null);
    }

    public static Account microsoft(String username, String uuid, String refreshToken) {
        return new Account(Type.MICROSOFT, username, uuid, refreshToken);
    }

    public Type getType() {
        return type == null ? Type.OFFLINE : type;
    }

    public String getUsername() {
        return username == null ? "" : username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUuid() {
        return uuid == null ? "" : uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public boolean isMicrosoft() {
        return getType() == Type.MICROSOFT;
    }

    public boolean isSameAccount(Account other) {
        if (other == null || other.getType() != getType()) {
            return false;
        }
        if (isMicrosoft()) {
            return getUuid().equalsIgnoreCase(other.getUuid());
        }
        return getUsername().equalsIgnoreCase(other.getUsername());
    }
}
