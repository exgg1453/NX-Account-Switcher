package com.nxteam.nxaccountswitcher.mixin;

import com.nxteam.nxaccountswitcher.account.SessionManager;
import com.nxteam.nxaccountswitcher.gui.AccountListScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Unique
    private static final int NX_ACCOUNT_BUTTON_ID = 1453_0001;

    @Shadow
    protected List<ButtonWidget> buttons;

    @Inject(method = "init(Lnet/minecraft/client/MinecraftClient;II)V", at = @At("TAIL"))
    private void nxAccountSwitcherAddButton(MinecraftClient client, int width, int height, CallbackInfo callbackInfo) {
        if (!nxAccountSwitcherIsSupportedScreen()) {
            return;
        }
        String username = SessionManager.getCurrentUsername();
        String label = "Account: " + username;
        if (label.length() > 24) {
            label = label.substring(0, 23) + "...";
        }
        buttons.add(new ButtonWidget(NX_ACCOUNT_BUTTON_ID, 4, 4, 130, 20, label));
    }

    @Inject(method = "mouseClicked(III)V", at = @At("HEAD"), cancellable = true)
    private void nxAccountSwitcherMouseClicked(int mouseX, int mouseY, int button, CallbackInfo callbackInfo) {
        if (button != 0 || !nxAccountSwitcherIsSupportedScreen()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        for (ButtonWidget widget : buttons) {
            if (widget.id == NX_ACCOUNT_BUTTON_ID && widget.visible && widget.active && widget.isMouseOver(client, mouseX, mouseY)) {
                widget.playDownSound(client.getSoundManager());
                client.setScreen(new AccountListScreen((Screen) (Object) this));
                callbackInfo.cancel();
                return;
            }
        }
    }

    @Unique
    private boolean nxAccountSwitcherIsSupportedScreen() {
        Object self = this;
        return self instanceof TitleScreen || self instanceof MultiplayerScreen;
    }
}
