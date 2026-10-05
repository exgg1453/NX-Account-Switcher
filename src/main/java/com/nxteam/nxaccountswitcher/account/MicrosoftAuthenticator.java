package com.nxteam.nxaccountswitcher.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MicrosoftAuthenticator {
    private static final String CLIENT_ID = "00000000441cc96b";
    private static final String SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";
    private static final String DEVICE_CODE_URL = "https://login.live.com/oauth20_connect.srf";
    private static final String TOKEN_URL = "https://login.live.com/oauth20_token.srf";
    private static final String XBOX_LIVE_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MINECRAFT_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MINECRAFT_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
    private static final String[] RPS_TICKET_PREFIXES = {"t=", "", "d="};

    private MicrosoftAuthenticator() {
    }

    public static DeviceCode requestDeviceCode() throws AuthenticationException {
        Map<String, String> parameters = new LinkedHashMap<String, String>();
        parameters.put("client_id", CLIENT_ID);
        parameters.put("scope", SCOPE);
        parameters.put("response_type", "device_code");
        HttpHelper.Response response = HttpHelper.postForm(DEVICE_CODE_URL, parameters);
        JsonObject json = response.getJson();
        if (!response.isSuccess() || !json.has("device_code") || !json.has("user_code")) {
            throw new AuthenticationException("Could not get a login code from Microsoft. " + describeError(json, response));
        }
        String verificationUri = getString(json, "verification_uri");
        if (verificationUri.isEmpty()) {
            verificationUri = "https://www.microsoft.com/link";
        }
        return new DeviceCode(
                getString(json, "device_code"),
                getString(json, "user_code"),
                verificationUri,
                getInt(json, "expires_in", 900),
                getInt(json, "interval", 5)
        );
    }

    public static MicrosoftTokens pollDeviceCode(DeviceCode deviceCode, AtomicBoolean cancelled) throws AuthenticationException, InterruptedException {
        long deadline = System.currentTimeMillis() + deviceCode.getExpiresIn() * 1000L;
        int interval = Math.max(1, deviceCode.getInterval());
        Map<String, String> parameters = new LinkedHashMap<String, String>();
        parameters.put("client_id", CLIENT_ID);
        parameters.put("device_code", deviceCode.getDeviceCode());
        parameters.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
        while (!cancelled.get()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AuthenticationException("The login code expired. Please try again.");
            }
            Thread.sleep(interval * 1000L);
            if (cancelled.get()) {
                break;
            }
            HttpHelper.Response response = HttpHelper.postForm(TOKEN_URL, parameters);
            JsonObject json = response.getJson();
            if (response.isSuccess() && json.has("access_token")) {
                return new MicrosoftTokens(getString(json, "access_token"), getString(json, "refresh_token"));
            }
            String error = getString(json, "error");
            if ("authorization_pending".equals(error)) {
                continue;
            }
            if ("slow_down".equals(error)) {
                interval += 5;
                continue;
            }
            if ("expired_token".equals(error)) {
                throw new AuthenticationException("The login code expired. Please try again.");
            }
            if ("authorization_declined".equals(error) || "access_denied".equals(error)) {
                throw new AuthenticationException("The login was declined.");
            }
            throw new AuthenticationException("Microsoft login failed. " + describeError(json, response));
        }
        throw new AuthenticationException("Login cancelled.");
    }

    public static MinecraftLoginResult refresh(String refreshToken) throws AuthenticationException {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new AuthenticationException("This account has no saved login. Remove it and add it again.");
        }
        Map<String, String> parameters = new LinkedHashMap<String, String>();
        parameters.put("client_id", CLIENT_ID);
        parameters.put("scope", SCOPE);
        parameters.put("grant_type", "refresh_token");
        parameters.put("refresh_token", refreshToken);
        HttpHelper.Response response = HttpHelper.postForm(TOKEN_URL, parameters);
        JsonObject json = response.getJson();
        if (!response.isSuccess() || !json.has("access_token")) {
            throw new AuthenticationException("The saved login expired. Remove the account and add it again. " + describeError(json, response));
        }
        String newRefreshToken = getString(json, "refresh_token");
        if (newRefreshToken.isEmpty()) {
            newRefreshToken = refreshToken;
        }
        return login(new MicrosoftTokens(getString(json, "access_token"), newRefreshToken));
    }

    public static MinecraftLoginResult login(MicrosoftTokens tokens) throws AuthenticationException {
        XboxToken xboxLiveToken = authenticateXboxLive(tokens.getAccessToken());
        XboxToken xstsToken = authorizeXsts(xboxLiveToken.token);
        String minecraftToken = loginMinecraft(xstsToken);
        HttpHelper.Response response = HttpHelper.get(MINECRAFT_PROFILE_URL, minecraftToken);
        JsonObject json = response.getJson();
        if (response.getStatus() == 404) {
            throw new AuthenticationException("This Microsoft account does not own Minecraft Java Edition.");
        }
        if (!response.isSuccess() || !json.has("id") || !json.has("name")) {
            throw new AuthenticationException("Could not load the Minecraft profile. " + describeError(json, response));
        }
        return new MinecraftLoginResult(getString(json, "name"), getString(json, "id"), minecraftToken, tokens.getRefreshToken());
    }

    private static XboxToken authenticateXboxLive(String microsoftAccessToken) throws AuthenticationException {
        HttpHelper.Response lastResponse = null;
        for (String prefix : RPS_TICKET_PREFIXES) {
            JsonObject properties = new JsonObject();
            properties.addProperty("AuthMethod", "RPS");
            properties.addProperty("SiteName", "user.auth.xboxlive.com");
            properties.addProperty("RpsTicket", prefix + microsoftAccessToken);
            JsonObject body = new JsonObject();
            body.add("Properties", properties);
            body.addProperty("RelyingParty", "http://auth.xboxlive.com");
            body.addProperty("TokenType", "JWT");
            HttpHelper.Response response = HttpHelper.postJson(XBOX_LIVE_URL, body);
            if (response.isSuccess()) {
                XboxToken token = parseXboxToken(response.getJson());
                if (token != null) {
                    return token;
                }
            }
            lastResponse = response;
        }
        throw new AuthenticationException("Xbox Live login failed. " + describeError(lastResponse == null ? new JsonObject() : lastResponse.getJson(), lastResponse));
    }

    private static XboxToken authorizeXsts(String xboxLiveToken) throws AuthenticationException {
        JsonArray userTokens = new JsonArray();
        userTokens.add(new JsonPrimitive(xboxLiveToken));
        JsonObject properties = new JsonObject();
        properties.addProperty("SandboxId", "RETAIL");
        properties.add("UserTokens", userTokens);
        JsonObject body = new JsonObject();
        body.add("Properties", properties);
        body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        body.addProperty("TokenType", "JWT");
        HttpHelper.Response response = HttpHelper.postJson(XSTS_URL, body);
        JsonObject json = response.getJson();
        if (!response.isSuccess()) {
            String xboxError = json.has("XErr") ? json.get("XErr").getAsString() : "";
            if ("2148916233".equals(xboxError)) {
                throw new AuthenticationException("This Microsoft account has no Xbox profile. Sign in once at xbox.com and try again.");
            }
            if ("2148916235".equals(xboxError)) {
                throw new AuthenticationException("Xbox Live is not available in this account's country.");
            }
            if ("2148916236".equals(xboxError) || "2148916237".equals(xboxError)) {
                throw new AuthenticationException("This account needs adult verification on xbox.com.");
            }
            if ("2148916238".equals(xboxError)) {
                throw new AuthenticationException("This is a child account. It must be added to a Microsoft family first.");
            }
            throw new AuthenticationException("Xbox authorization failed. " + describeError(json, response));
        }
        XboxToken token = parseXboxToken(json);
        if (token == null) {
            throw new AuthenticationException("Xbox authorization returned an invalid response.");
        }
        return token;
    }

    private static String loginMinecraft(XboxToken xstsToken) throws AuthenticationException {
        JsonObject body = new JsonObject();
        body.addProperty("identityToken", "XBL3.0 x=" + xstsToken.userHash + ";" + xstsToken.token);
        HttpHelper.Response response = HttpHelper.postJson(MINECRAFT_LOGIN_URL, body);
        JsonObject json = response.getJson();
        if (!response.isSuccess() || !json.has("access_token")) {
            throw new AuthenticationException("Minecraft login failed. " + describeError(json, response));
        }
        return getString(json, "access_token");
    }

    private static XboxToken parseXboxToken(JsonObject json) {
        try {
            String token = json.get("Token").getAsString();
            JsonObject displayClaims = json.getAsJsonObject("DisplayClaims");
            JsonArray xui = displayClaims.getAsJsonArray("xui");
            String userHash = xui.get(0).getAsJsonObject().get("uhs").getAsString();
            return new XboxToken(token, userHash);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String describeError(JsonObject json, HttpHelper.Response response) {
        String description = getString(json, "error_description");
        if (description.isEmpty()) {
            description = getString(json, "errorMessage");
        }
        if (description.isEmpty()) {
            description = getString(json, "error");
        }
        if (description.isEmpty() && json.has("XErr")) {
            description = "XErr " + json.get("XErr").getAsString();
        }
        if (description.isEmpty() && response != null) {
            description = "HTTP " + response.getStatus();
        }
        return description;
    }

    private static String getString(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return "";
        }
        return element.getAsString();
    }

    private static int getInt(JsonObject json, String key, int fallback) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static final class XboxToken {
        private final String token;
        private final String userHash;

        private XboxToken(String token, String userHash) {
            this.token = token;
            this.userHash = userHash;
        }
    }
}
