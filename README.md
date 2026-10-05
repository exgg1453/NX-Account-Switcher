# NX Account Switcher

Legacy Fabric mod for Minecraft 1.12.2 that lets you add accounts in-game and switch between them without restarting the game.

## Features

- **Microsoft (premium) accounts** — secure device-code login: open `microsoft.com/link`, enter the code, done. Your password never touches the game.
- **Offline (cracked) accounts** — just type a username (3-16 characters).
- Switch accounts from the **title screen** or the **multiplayer screen** (button in the top-left corner).
- Saved Microsoft logins are refreshed automatically when you switch.
- Double-click an account or press Enter to log in, arrow keys to move, mouse wheel to scroll.

## Installation

1. Install [Legacy Fabric](https://legacyfabric.net/) for Minecraft 1.12.2.
2. Put `NX-Account-Switcher-x.x.x.jar` into the `mods` folder.

## Data

Accounts are stored in `config/nxaccountswitcher.json`. Microsoft entries contain a refresh token, so do not share this file.

## Building

```
./gradlew build
```

The jar is created in `build/libs/`. GitHub Actions builds it automatically on every push.

## License

MIT — NX Team
