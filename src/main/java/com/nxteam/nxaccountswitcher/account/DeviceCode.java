package com.nxteam.nxaccountswitcher.account;

public final class DeviceCode {
    private final String deviceCode;
    private final String userCode;
    private final String verificationUri;
    private final int expiresIn;
    private final int interval;

    public DeviceCode(String deviceCode, String userCode, String verificationUri, int expiresIn, int interval) {
        this.deviceCode = deviceCode;
        this.userCode = userCode;
        this.verificationUri = verificationUri;
        this.expiresIn = expiresIn;
        this.interval = interval;
    }

    public String getDeviceCode() {
        return deviceCode;
    }

    public String getUserCode() {
        return userCode;
    }

    public String getVerificationUri() {
        return verificationUri;
    }

    public String getDirectLink() {
        return verificationUri + (verificationUri.contains("?") ? "&" : "?") + "otc=" + userCode;
    }

    public int getExpiresIn() {
        return expiresIn;
    }

    public int getInterval() {
        return interval;
    }
}
