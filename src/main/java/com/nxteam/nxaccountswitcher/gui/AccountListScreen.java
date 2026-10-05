package com.nxteam.nxaccountswitcher.gui;

import com.nxteam.nxaccountswitcher.NXAccountSwitcher;
import com.nxteam.nxaccountswitcher.account.Account;
import com.nxteam.nxaccountswitcher.account.AccountStorage;
import com.nxteam.nxaccountswitcher.account.AuthenticationException;
import com.nxteam.nxaccountswitcher.account.MicrosoftAuthenticator;
import com.nxteam.nxaccountswitcher.account.MinecraftLoginResult;
import com.nxteam.nxaccountswitcher.account.SessionManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AccountListScreen extends Screen {
    private static final int BUTTON_LOGIN = 0;
    private static final int BUTTON_ADD_MICROSOFT = 1;
    private static final int BUTTON_ADD_OFFLINE = 2;
    private static final int BUTTON_REMOVE = 3;
    private static final int BUTTON_BACK = 4;
    private static final int ROW_HEIGHT = 24;
    private static final int LIST_WIDTH = 240;
    private static final int COLOR_WHITE = 0xFFFFFF;
    private static final int COLOR_GRAY = 0xA0A0A0;
    private static final int COLOR_GREEN = 0x55FF55;
    private static final int COLOR_RED = 0xFF5555;
    private static final int COLOR_YELLOW = 0xFFFF55;
    private static final int COLOR_BLUE = 0x55AAFF;

    private final Screen parent;
    private List<Account> accounts = new ArrayList<Account>();
    private int selectedIndex = -1;
    private int scrollOffset;
    private int pendingRemoveIndex = -1;
    private long lastClickTime;
    private int lastClickIndex = -1;
    private String status = "";
    private int statusColor = COLOR_WHITE;

    private volatile boolean busy;
    private volatile MinecraftLoginResult pendingResult;
    private volatile Account pendingAccount;
    private volatile String pendingError;

    private ButtonWidget loginButton;
    private ButtonWidget addMicrosoftButton;
    private ButtonWidget addOfflineButton;
    private ButtonWidget removeButton;

    public AccountListScreen(Screen parent) {
        this.parent = parent;
    }

    public void setStatus(String status, int color) {
        this.status = status == null ? "" : status;
        this.statusColor = color;
    }

    @Override
    public void init() {
        reloadAccounts();
        int centerX = this.width / 2;
        int firstRowY = this.height - 52;
        int secondRowY = this.height - 28;
        loginButton = new ButtonWidget(BUTTON_LOGIN, centerX - 154, firstRowY, 100, 20, "Login");
        addMicrosoftButton = new ButtonWidget(BUTTON_ADD_MICROSOFT, centerX - 50, firstRowY, 100, 20, "Add Microsoft");
        addOfflineButton = new ButtonWidget(BUTTON_ADD_OFFLINE, centerX + 54, firstRowY, 100, 20, "Add Offline");
        removeButton = new ButtonWidget(BUTTON_REMOVE, centerX - 154, secondRowY, 150, 20, "Remove");
        this.buttons.add(loginButton);
        this.buttons.add(addMicrosoftButton);
        this.buttons.add(addOfflineButton);
        this.buttons.add(removeButton);
        this.buttons.add(new ButtonWidget(BUTTON_BACK, centerX + 4, secondRowY, 150, 20, "Back"));
        updateButtons();
    }

    private void reloadAccounts() {
        accounts = NXAccountSwitcher.getStorage().getAccounts();
        if (selectedIndex >= accounts.size()) {
            selectedIndex = accounts.size() - 1;
        }
        if (selectedIndex < 0 && !accounts.isEmpty()) {
            for (int index = 0; index < accounts.size(); index++) {
                if (SessionManager.isActive(accounts.get(index))) {
                    selectedIndex = index;
                    break;
                }
            }
        }
        clampScroll();
    }

    private void updateButtons() {
        boolean hasSelection = selectedIndex >= 0 && selectedIndex < accounts.size();
        loginButton.active = hasSelection && !busy;
        removeButton.active = hasSelection && !busy;
        addMicrosoftButton.active = !busy;
        addOfflineButton.active = !busy;
        removeButton.message = pendingRemoveIndex == selectedIndex && hasSelection ? "Click again to confirm" : "Remove";
    }

    private int getListTop() {
        return 34;
    }

    private int getListBottom() {
        return this.height - 76;
    }

    private int getListLeft() {
        return (this.width - LIST_WIDTH) / 2;
    }

    private int getVisibleRows() {
        return Math.max(1, (getListBottom() - getListTop()) / ROW_HEIGHT);
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, accounts.size() - getVisibleRows());
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
        if (scrollOffset < 0) {
            scrollOffset = 0;
        }
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (!button.active) {
            return;
        }
        switch (button.id) {
            case BUTTON_LOGIN:
                loginSelected();
                break;
            case BUTTON_ADD_MICROSOFT:
                this.client.setScreen(new MicrosoftLoginScreen(this));
                break;
            case BUTTON_ADD_OFFLINE:
                this.client.setScreen(new OfflineAccountScreen(this));
                break;
            case BUTTON_REMOVE:
                removeSelected();
                break;
            case BUTTON_BACK:
                this.client.setScreen(parent);
                break;
            default:
                break;
        }
    }

    private void removeSelected() {
        if (selectedIndex < 0 || selectedIndex >= accounts.size()) {
            return;
        }
        if (pendingRemoveIndex != selectedIndex) {
            pendingRemoveIndex = selectedIndex;
            updateButtons();
            return;
        }
        Account account = accounts.get(selectedIndex);
        AccountStorage storage = NXAccountSwitcher.getStorage();
        storage.remove(account);
        storage.save();
        pendingRemoveIndex = -1;
        setStatus("Removed " + account.getUsername(), COLOR_YELLOW);
        reloadAccounts();
        updateButtons();
    }

    private void loginSelected() {
        if (busy || selectedIndex < 0 || selectedIndex >= accounts.size()) {
            return;
        }
        pendingRemoveIndex = -1;
        final Account account = accounts.get(selectedIndex);
        if (!account.isMicrosoft()) {
            SessionManager.applyOffline(account.getUsername());
            setStatus("Logged in as " + account.getUsername() + " (offline)", COLOR_GREEN);
            updateButtons();
            return;
        }
        busy = true;
        setStatus("Logging in as " + account.getUsername() + "...", COLOR_YELLOW);
        updateButtons();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    MinecraftLoginResult result = MicrosoftAuthenticator.refresh(account.getRefreshToken());
                    pendingAccount = account;
                    pendingResult = result;
                } catch (AuthenticationException exception) {
                    pendingError = exception.getMessage();
                } catch (RuntimeException exception) {
                    pendingError = "Unexpected error: " + exception;
                }
            }
        }, "NX Account Switcher Login");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void tick() {
        MinecraftLoginResult result = pendingResult;
        Account account = pendingAccount;
        if (result != null && account != null) {
            pendingResult = null;
            pendingAccount = null;
            account.setUsername(result.getUsername());
            account.setUuid(result.getUuid());
            account.setRefreshToken(result.getRefreshToken());
            AccountStorage storage = NXAccountSwitcher.getStorage();
            storage.addOrReplace(account);
            storage.save();
            SessionManager.applyMicrosoft(result.getUsername(), result.getUuid(), result.getAccessToken());
            busy = false;
            setStatus("Logged in as " + result.getUsername() + " (Microsoft)", COLOR_GREEN);
            reloadAccounts();
            updateButtons();
        }
        String error = pendingError;
        if (error != null) {
            pendingError = null;
            busy = false;
            setStatus(error, COLOR_RED);
            updateButtons();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        super.mouseClicked(mouseX, mouseY, button);
        if (button != 0 || busy) {
            return;
        }
        int left = getListLeft();
        int top = getListTop();
        if (mouseX < left || mouseX > left + LIST_WIDTH || mouseY < top || mouseY >= top + getVisibleRows() * ROW_HEIGHT) {
            return;
        }
        int index = scrollOffset + (mouseY - top) / ROW_HEIGHT;
        if (index < 0 || index >= accounts.size()) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean doubleClick = index == lastClickIndex && now - lastClickTime < 300L;
        if (index != selectedIndex) {
            pendingRemoveIndex = -1;
        }
        selectedIndex = index;
        lastClickIndex = index;
        lastClickTime = now;
        updateButtons();
        if (doubleClick) {
            loginSelected();
        }
    }

    @Override
    public void handleMouse() throws IOException {
        super.handleMouse();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scrollOffset += wheel > 0 ? -1 : 1;
            clampScroll();
        }
    }

    @Override
    protected void keyPressed(char character, int keyCode) throws IOException {
        if (keyCode == 1) {
            this.client.setScreen(parent);
            return;
        }
        if (keyCode == 28 || keyCode == 156) {
            loginSelected();
            return;
        }
        if (keyCode == 200 && selectedIndex > 0) {
            selectedIndex--;
            pendingRemoveIndex = -1;
            ensureSelectionVisible();
            updateButtons();
            return;
        }
        if (keyCode == 208 && selectedIndex < accounts.size() - 1) {
            selectedIndex++;
            pendingRemoveIndex = -1;
            ensureSelectionVisible();
            updateButtons();
            return;
        }
        super.keyPressed(character, keyCode);
    }

    private void ensureSelectionVisible() {
        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
        } else if (selectedIndex >= scrollOffset + getVisibleRows()) {
            scrollOffset = selectedIndex - getVisibleRows() + 1;
        }
        clampScroll();
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        this.renderBackground();
        drawCenteredString(this.textRenderer, "NX Account Switcher", this.width / 2, 8, COLOR_WHITE);
        drawCenteredString(this.textRenderer, "Current: " + SessionManager.getCurrentUsername(), this.width / 2, 20, COLOR_GRAY);

        int left = getListLeft();
        int top = getListTop();
        int bottom = getListBottom();
        fill(left - 2, top - 2, left + LIST_WIDTH + 2, bottom + 2, 0x90000000);

        if (accounts.isEmpty()) {
            drawCenteredString(this.textRenderer, "No accounts yet.", this.width / 2, top + 10, COLOR_GRAY);
            drawCenteredString(this.textRenderer, "Add a Microsoft or offline account below.", this.width / 2, top + 22, COLOR_GRAY);
        }

        int visibleRows = getVisibleRows();
        for (int row = 0; row < visibleRows; row++) {
            int index = scrollOffset + row;
            if (index >= accounts.size()) {
                break;
            }
            Account account = accounts.get(index);
            int rowY = top + row * ROW_HEIGHT;
            boolean hovered = mouseX >= left && mouseX <= left + LIST_WIDTH && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (index == selectedIndex) {
                fill(left, rowY, left + LIST_WIDTH, rowY + ROW_HEIGHT - 2, 0x70FFFFFF);
                fill(left + 1, rowY + 1, left + LIST_WIDTH - 1, rowY + ROW_HEIGHT - 3, 0xC0000000);
            } else if (hovered) {
                fill(left, rowY, left + LIST_WIDTH, rowY + ROW_HEIGHT - 2, 0x30FFFFFF);
            }
            boolean active = SessionManager.isActive(account);
            drawWithShadow(this.textRenderer, account.getUsername(), left + 6, rowY + 3, active ? COLOR_GREEN : COLOR_WHITE);
            String typeText = account.isMicrosoft() ? "Microsoft (Premium)" : "Offline (Cracked)";
            drawWithShadow(this.textRenderer, typeText, left + 6, rowY + 13, account.isMicrosoft() ? COLOR_BLUE : COLOR_GRAY);
            if (active) {
                String activeText = "ACTIVE";
                drawWithShadow(this.textRenderer, activeText, left + LIST_WIDTH - 6 - this.textRenderer.getStringWidth(activeText), rowY + 8, COLOR_GREEN);
            }
        }

        if (accounts.size() > visibleRows) {
            int trackHeight = bottom - top;
            int thumbHeight = Math.max(10, trackHeight * visibleRows / accounts.size());
            int maxScroll = accounts.size() - visibleRows;
            int thumbY = top + (trackHeight - thumbHeight) * scrollOffset / Math.max(1, maxScroll);
            fill(left + LIST_WIDTH - 3, thumbY, left + LIST_WIDTH, thumbY + thumbHeight, 0xFFAAAAAA);
        }

        if (!status.isEmpty()) {
            String shown = this.textRenderer.trimToWidth(status, this.width - 10);
            drawCenteredString(this.textRenderer, shown, this.width / 2, this.height - 66, statusColor);
        }

        super.render(mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }
}
