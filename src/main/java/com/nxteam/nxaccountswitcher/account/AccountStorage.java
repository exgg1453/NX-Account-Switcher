package com.nxteam.nxaccountswitcher.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class AccountStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File file;
    private final List<Account> accounts = new ArrayList<Account>();

    public AccountStorage(File file) {
        this.file = file;
    }

    public synchronized void load() {
        accounts.clear();
        if (!file.isFile()) {
            return;
        }
        try (Reader reader = new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8)) {
            JsonElement root = new JsonParser().parse(reader);
            if (root == null || !root.isJsonObject()) {
                return;
            }
            JsonObject object = root.getAsJsonObject();
            if (!object.has("accounts") || !object.get("accounts").isJsonArray()) {
                return;
            }
            for (JsonElement element : object.getAsJsonArray("accounts")) {
                try {
                    Account account = GSON.fromJson(element, Account.class);
                    if (account != null && !account.getUsername().isEmpty()) {
                        accounts.add(account);
                    }
                } catch (RuntimeException ignored) {
                }
            }
        } catch (IOException | RuntimeException exception) {
            System.err.println("[NX Account Switcher] Failed to load accounts: " + exception);
        }
    }

    public synchronized void save() {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            parent.mkdirs();
        }
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        for (Account account : accounts) {
            array.add(GSON.toJsonTree(account));
        }
        root.add("accounts", array);
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        } catch (IOException exception) {
            System.err.println("[NX Account Switcher] Failed to save accounts: " + exception);
        }
    }

    public synchronized List<Account> getAccounts() {
        return new ArrayList<Account>(accounts);
    }

    public synchronized Account addOrReplace(Account account) {
        for (int index = 0; index < accounts.size(); index++) {
            Account existing = accounts.get(index);
            if (existing == account) {
                return account;
            }
            if (existing.isSameAccount(account)) {
                accounts.set(index, account);
                return account;
            }
        }
        accounts.add(account);
        return account;
    }

    public synchronized void remove(Account account) {
        Iterator<Account> iterator = accounts.iterator();
        while (iterator.hasNext()) {
            Account existing = iterator.next();
            if (existing == account || existing.isSameAccount(account)) {
                iterator.remove();
            }
        }
    }
}
