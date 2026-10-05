package com.nxteam.nxaccountswitcher.gui;

import java.awt.Desktop;
import java.net.URI;
import java.util.Locale;

public final class UrlOpener {
    private UrlOpener() {
    }

    public static void open(String url) {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (osName.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
                return;
            }
            if (osName.contains("mac")) {
                new ProcessBuilder("open", url).start();
                return;
            }
            new ProcessBuilder("xdg-open", url).start();
            return;
        } catch (Exception ignored) {
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception exception) {
            System.err.println("[NX Account Switcher] Could not open link: " + url);
        }
    }
}
