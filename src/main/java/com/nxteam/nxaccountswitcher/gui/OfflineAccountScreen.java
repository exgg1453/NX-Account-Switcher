package com.nxteam.nxaccountswitcher.gui;

import com.nxteam.nxaccountswitcher.NXAccountSwitcher;
import com.nxteam.nxaccountswitcher.account.Account;
import com.nxteam.nxaccountswitcher.account.AccountStorage;
import com.nxteam.nxaccountswitcher.account.SessionManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.regex.Pattern;

public class OfflineAccountScreen extends Screen {
    private static final int BUTTON_ADD = 0;
    private static final int BUTTON_CANCEL = 1;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private final AccountListScreen parent;
    private TextFieldWidget usernameField;
    private ButtonWidget addButton;
    private String error = "";

    public OfflineAccountScreen(AccountListScreen parent) {
        this.parent = parent;
    }

    @Override
    public void init() {
        Keyboard.enableRepeatEvents(true);
        String previousText = usernameField == null ? "" : usernameField.getText();
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        usernameField = new TextFieldWidget(10, this.textRenderer, centerX - 100, centerY - 20, 200, 20);
        usernameField.setMaxLength(16);
        usernameField.setText(previousText);
        usernameField.setFocused(true);
        addButton = new ButtonWidget(BUTTON_ADD, centerX - 100, centerY + 20, 98, 20, "Add & Login");
        this.buttons.add(addButton);
        this.buttons.add(new ButtonWidget(BUTTON_CANCEL, centerX + 2, centerY + 20, 98, 20, "Cancel"));
        updateButtons();
    }

    private void updateButtons() {
        addButton.active = USERNAME_PATTERN.matcher(usernameField.getText()).matches();
    }

    @Override
    public void removed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void tick() {
        usernameField.tick();
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (!button.active) {
            return;
        }
        if (button.id == BUTTON_ADD) {
            addAccount();
        } else if (button.id == BUTTON_CANCEL) {
            this.client.setScreen(parent);
        }
    }

    private void addAccount() {
        String username = usernameField.getText().trim();
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            error = "Name must be 3-16 characters: letters, numbers or _";
            return;
        }
        Account account = Account.offline(username);
        AccountStorage storage = NXAccountSwitcher.getStorage();
        storage.addOrReplace(account);
        storage.save();
        SessionManager.applyOffline(username);
        parent.setStatus("Logged in as " + username + " (offline)", 0x55FF55);
        this.client.setScreen(parent);
    }

    @Override
    protected void keyPressed(char character, int keyCode) throws IOException {
        if (keyCode == 1) {
            this.client.setScreen(parent);
            return;
        }
        if (keyCode == 28 || keyCode == 156) {
            if (addButton.active) {
                addAccount();
            }
            return;
        }
        usernameField.keyPressed(character, keyCode);
        error = "";
        updateButtons();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        super.mouseClicked(mouseX, mouseY, button);
        usernameField.method_920(mouseX, mouseY, button);
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        this.renderBackground();
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        drawCenteredString(this.textRenderer, "Add Offline Account", centerX, centerY - 60, 0xFFFFFF);
        drawWithShadow(this.textRenderer, "Username", centerX - 100, centerY - 32, 0xA0A0A0);
        usernameField.render();
        if (!error.isEmpty()) {
            drawCenteredString(this.textRenderer, error, centerX, centerY + 4, 0xFF5555);
        }
        drawCenteredString(this.textRenderer, "Works only on offline-mode (cracked) servers.", centerX, centerY + 50, 0x808080);
        super.render(mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }
}
