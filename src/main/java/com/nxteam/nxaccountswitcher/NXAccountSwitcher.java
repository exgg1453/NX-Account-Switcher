package com.nxteam.nxaccountswitcher;

import com.nxteam.nxaccountswitcher.account.AccountStorage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class NXAccountSwitcher implements ClientModInitializer {
    public static final String MOD_ID = "nxaccountswitcher";

    private static AccountStorage storage;

    @Override
    public void onInitializeClient() {
        getStorage();
    }

    public static synchronized AccountStorage getStorage() {
        if (storage == null) {
            storage = new AccountStorage(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json").toFile());
            storage.load();
        }
        return storage;
    }
}
