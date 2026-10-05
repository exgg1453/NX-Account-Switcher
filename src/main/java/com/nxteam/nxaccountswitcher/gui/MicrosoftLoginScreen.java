package com.nxteam.nxaccountswitcher.gui;

import com.nxteam.nxaccountswitcher.NXAccountSwitcher;
import com.nxteam.nxaccountswitcher.account.Account;
import com.nxteam.nxaccountswitcher.account.AccountStorage;
import com.nxteam.nxaccountswitcher.account.AuthenticationException;
import com.nxteam.nxaccountswitcher.account.DeviceCode;
import com.nxteam.nxaccountswitcher.account.MicrosoftAuthenticator;
import com.nxteam.nxaccountswitcher.account.MicrosoftTokens;
import com.nxteam.nxaccountswitcher.account.MinecraftLoginResult;
import com.nxteam.nxaccountswitcher.account.SessionManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.opengl.GL11;

import java.util.concurrent.atomic.AtomicBoolean;

public class MicrosoftLoginScreen extends Screen {
    private static final int BUTTON_OPEN = 0;
    private static final int BUTTON_RETRY = 1;
    private static final int BUTTON_CANCEL = 2;

    private enum State {
        REQUESTING,
        WAITING,
        AUTHENTICATING,
        FAILED
    }

    private final AccountListScreen parent;
    private AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile State state = State.REQUESTING;
    private volatile DeviceCode deviceCode;
    private volatile MinecraftLoginResult result;
    private volatile String error = "";
    private boolean started;
    private boolean copied;

    private ButtonWidget openButton;
    private ButtonWidget retryButton;
    private ButtonWidget cancelButton;

    public MicrosoftLoginScreen(AccountListScreen parent) {
        this.parent = parent;
    }

    @Override
    public void init() {
        int centerX = this.width / 2;
        int bottomY = this.height / 2 + 50;
        openButton = new ButtonWidget(BUTTON_OPEN, centerX - 100, bottomY, 200, 20, "Open Link & Copy Code");
        retryButton = new ButtonWidget(BUTTON_RETRY, centerX - 100, bottomY + 24, 98, 20, "Try Again");
        cancelButton = new ButtonWidget(BUTTON_CANCEL, centerX + 2, bottomY + 24, 98, 20, "Cancel");
        this.buttons.add(openButton);
        this.buttons.add(retryButton);
        this.buttons.add(cancelButton);
        if (!started) {
            started = true;
            startLogin();
        }
        updateButtons();
    }

    private void updateButtons() {
        openButton.active = state == State.WAITING && deviceCode != null;
        openButton.message = copied ? "Open Link (code copied)" : "Open Link & Copy Code";
        retryButton.active = state == State.FAILED;
    }

    private void startLogin() {
        cancelled.set(true);
        final AtomicBoolean taskCancelled = new AtomicBoolean(false);
        cancelled = taskCancelled;
        state = State.REQUESTING;
        deviceCode = null;
        result = null;
        error = "";
        copied = false;
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    DeviceCode code = MicrosoftAuthenticator.requestDeviceCode();
                    if (taskCancelled.get()) {
                        return;
                    }
                    deviceCode = code;
                    state = State.WAITING;
                    MicrosoftTokens tokens = MicrosoftAuthenticator.pollDeviceCode(code, taskCancelled);
                    if (taskCancelled.get()) {
                        return;
                    }
                    state = State.AUTHENTICATING;
                    MinecraftLoginResult loginResult = MicrosoftAuthenticator.login(tokens);
                    if (!taskCancelled.get()) {
                        result = loginResult;
                    }
                } catch (AuthenticationException exception) {
                    if (!taskCancelled.get()) {
                        error = exception.getMessage();
                        state = State.FAILED;
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException exception) {
                    if (!taskCancelled.get()) {
                        error = "Unexpected error: " + exception;
                        state = State.FAILED;
                    }
                }
            }
        }, "NX Account Switcher Microsoft Login");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void tick() {
        MinecraftLoginResult loginResult = result;
        if (loginResult != null) {
            result = null;
            Account account = Account.microsoft(loginResult.getUsername(), loginResult.getUuid(), loginResult.getRefreshToken());
            AccountStorage storage = NXAccountSwitcher.getStorage();
            storage.addOrReplace(account);
            storage.save();
            SessionManager.applyMicrosoft(loginResult.getUsername(), loginResult.getUuid(), loginResult.getAccessToken());
            parent.setStatus("Logged in as " + loginResult.getUsername() + " (Microsoft)", 0x55FF55);
            this.client.setScreen(parent);
            return;
        }
        updateButtons();
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (!button.active) {
            return;
        }
        if (button.id == BUTTON_OPEN) {
            DeviceCode code = deviceCode;
            if (code != null) {
                Screen.setClipboard(code.getUserCode());
                copied = true;
                UrlOpener.open(code.getDirectLink());
                updateButtons();
            }
        } else if (button.id == BUTTON_RETRY) {
            startLogin();
            updateButtons();
        } else if (button.id == BUTTON_CANCEL) {
            close();
        }
    }

    private void close() {
        cancelled.set(true);
        this.client.setScreen(parent);
    }

    @Override
    public void removed() {
        cancelled.set(true);
    }

    @Override
    protected void keyPressed(char character, int keyCode) {
        if (keyCode == 1) {
            close();
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        this.renderBackground();
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        drawCenteredString(this.textRenderer, "Add Microsoft Account", centerX, centerY - 80, 0xFFFFFF);
        State currentState = state;
        DeviceCode code = deviceCode;
        if (currentState == State.REQUESTING) {
            drawCenteredString(this.textRenderer, "Getting a login code from Microsoft" + dots(), centerX, centerY - 30, 0xFFFF55);
        } else if (currentState == State.WAITING && code != null) {
            drawCenteredString(this.textRenderer, "1. Open this link in a browser:", centerX, centerY - 56, 0xA0A0A0);
            drawCenteredString(this.textRenderer, code.getVerificationUri(), centerX, centerY - 44, 0x55AAFF);
            drawCenteredString(this.textRenderer, "2. Enter this code and sign in:", centerX, centerY - 28, 0xA0A0A0);
            GL11.glPushMatrix();
            GL11.glScalef(2.0F, 2.0F, 1.0F);
            drawCenteredString(this.textRenderer, code.getUserCode(), centerX / 2, (centerY - 14) / 2, 0xFFFF55);
            GL11.glPopMatrix();
            drawCenteredString(this.textRenderer, "Waiting for you to sign in" + dots(), centerX, centerY + 14, 0x808080);
            drawCenteredString(this.textRenderer, "The account must own Minecraft Java Edition.", centerX, centerY + 30, 0x808080);
        } else if (currentState == State.AUTHENTICATING) {
            drawCenteredString(this.textRenderer, "Signing in to Xbox Live and Minecraft" + dots(), centerX, centerY - 30, 0xFFFF55);
        } else if (currentState == State.FAILED) {
            drawCenteredString(this.textRenderer, "Login failed", centerX, centerY - 44, 0xFF5555);
            int lineY = centerY - 28;
            for (Object line : this.textRenderer.wrapLines(error, this.width - 40)) {
                drawCenteredString(this.textRenderer, String.valueOf(line), centerX, lineY, 0xFFAAAA);
                lineY += 10;
                if (lineY > centerY + 40) {
                    break;
                }
            }
        }
        super.render(mouseX, mouseY, delta);
    }

    private String dots() {
        int count = (int) ((System.currentTimeMillis() / 400L) % 4L);
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < count; index++) {
            builder.append('.');
        }
        return builder.toString();
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }
}
