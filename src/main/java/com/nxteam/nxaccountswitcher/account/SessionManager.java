package com.nxteam.nxaccountswitcher.account;

import com.nxteam.nxaccountswitcher.mixin.MinecraftClientAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Session;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class SessionManager {
    private SessionManager() {
    }

    public static String offlineUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }

    public static void applyOffline(String username) {
        setSession(new Session(username, offlineUuid(username), "0", "legacy"));
    }

    public static void applyMicrosoft(String username, String uuid, String accessToken) {
        setSession(new Session(username, uuid.replace("-", ""), accessToken, "mojang"));
    }

    public static String getCurrentUsername() {
        Session session = MinecraftClient.getInstance().getSession();
        return session == null ? "" : session.getUsername();
    }

    public static boolean isActive(Account account) {
        Session session = MinecraftClient.getInstance().getSession();
        if (session == null || account == null) {
            return false;
        }
        if (!account.getUsername().equals(session.getUsername())) {
            return false;
        }
        String sessionUuid = session.getUuid() == null ? "" : session.getUuid().replace("-", "");
        return account.getUuid().replace("-", "").equalsIgnoreCase(sessionUuid);
    }

    private static void setSession(Session session) {
        ((MinecraftClientAccessor) MinecraftClient.getInstance()).setSession(session);
    }
}
