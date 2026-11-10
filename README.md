<div align="center">

# ScriptCraft

**Skript-style scripts for Minecraft Forge: commands, events, screens, HUD, blocks and items, without plugins or a hybrid server.**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-62B47A?style=for-the-badge)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.x-DFA86A?style=for-the-badge)](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html)
[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge)](https://adoptium.net/temurin/releases/?version=17)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)
[![Status](https://img.shields.io/badge/status-alpha-orange?style=for-the-badge)](#status)
[![Docs](https://img.shields.io/badge/docs-elgerelg.github.io-blue?style=for-the-badge)](https://elgerelg.github.io/ScriptCraft/)

[Documentation](https://elgerelg.github.io/ScriptCraft/) · [Examples](https://elgerelg.github.io/ScriptCraft/examples.html) · [Getting started](https://elgerelg.github.io/ScriptCraft/start.html) · [Technical notes](docs/TECHNICAL.md)

</div>

---

## What ScriptCraft is

ScriptCraft is a **Forge 1.20.1** mod with a small scripting language in the spirit of [Skript](https://github.com/SkriptLang/Skript).
Write a `.sc` text file in `scriptcraft/scripts/`, run `/sc reload`, and the server gains commands, join messages, menus, and custom blocks. No Java plugin, no Bukkit, no hybrid core. It runs on Forge, in singleplayer and on a dedicated server.

The same jar is required on the **server and on every client**. Screens, custom blocks, and texture sync only work when both sides have the mod, and both sides must be the same ScriptCraft version.

## Features

| | Feature | In short |
|---|---|---|
| Events | Player and world | join, quit, first join, death, respawn, chat, break, place, left and right click, drop, pickup, **item received**, damage, world change, gamemode, load and unload, **key press**, **vanilla menu open** (can be cancelled), `every`, `wait` |
| Commands | Your own | permission, aliases, who may run it, typed arguments (`text`, `player`, `integer`, `item`, and more) |
| Effects | On players | messages, titles, sounds, items, teleport, heal, damage, gamemode, flight, potions, experience, kick, weather and time, lightning, explosion, velocity, spawn point |
| Language | Expressions | player name, UUID, online count, command arguments, `health of player`, random numbers, event placeholders, `&` color codes |
| Variables | Stored and temporary | saved across restarts (with backups) or kept until the next restart. A HUD label bound to `{variable}` updates when the variable is written |
| Conditions | Branching | comparisons, `if` / `else if` / `else`, `and` / `or`, player state, item in hand, gamemode and world |
| Targets | Who it applies to | all players, a player by name, players in a radius, the player you look at, vanilla `@a` / `@p` / `@r` / `@s`, `loop` |
| Screens | Custom GUI | text, buttons, images, item slots, progress bars, layers, anchors, a **vertical or horizontal list**, and **click hitboxes** on text, images and slots |
| HUD | On the game view | hotbar, health and hunger bars, images and text, layers, hide parts of the vanilla HUD. The HUD comes back when the player rejoins |
| Blocks and items | Declared in the script | hardness, sound, light, rarity, facing, transparent and 3D models |
| Namespaces | Your own ids | `example:ruby` instead of a generic mod id |
| Resources | No manual resource pack | the server sends textures, models and translations to players |
| Chat effects | One line in chat | a `?` token runs a script effect, gated by permission |
| Reload | No restart | `/sc reload` rereads scripts and reports the file and line of an error |
| Studio | In-game editor | F3 + `` ` `` (Ё on a Russian layout) opens the mod window for operators: home, journal, and script editing |
| Languages | Mod messages | English or Russian, set in `scriptcraft/config.toml` |

The full syntax, with an example on every entry, is on the [documentation site](https://elgerelg.github.io/ScriptCraft/). The site opens in English. The **RU** button switches it to Russian.

## Install

**Players and servers**

1. Install **Minecraft 1.20.1** and **Forge 47.x**.
2. Put the same `scriptcraft-<version>.jar` in `mods/` on the server **and** on every client.
3. Start the game. The mod creates `scriptcraft/` and copies example scripts disabled (`-welcome.sc`, `-ruby.sc`, `-menu.sc`, `-hud.sc`, `-bag.sc`). A file that is already there is left as it is. Enable one with `/sc enable welcome`. `hud.sc` changes the vanilla HUD, and `bag.sc` replaces the E inventory.

**Build from source (Windows)**

1. Install **JDK 17** ([Eclipse Temurin](https://adoptium.net/temurin/releases/?version=17)) and check `java -version`.
2. From the project folder:

```bat
gradlew.bat build
```

3. The jar is `build\libs\scriptcraft-<version>.jar`.

The first build is slow (about 10–30 minutes): Gradle downloads Minecraft and Forge.

**Development**

```bat
gradlew.bat runClient
gradlew.bat runServer
```

Before the first dedicated server start, set `eula=true` in `run\eula.txt`. In the dev workspace the folder is `run\scriptcraft\`. On Linux and macOS use `./gradlew`.

## The `scriptcraft/` folder

Created next to `mods/` (on a server, next to `server.properties`):

```text
scriptcraft/
├── config.toml           mod settings
├── scripts/              your *.sc scripts (subfolders are loaded; "-name.sc" is disabled)
├── variables/
│   ├── variables.json    saved variables
│   └── variables-*.json  backups, written at each server start
├── resources/
│   ├── assets/<ns>/      textures, models, translations (the server sends these)
│   └── data/<ns>/        loot tables, tags, recipes (server only)
└── cache/                client-side copies downloaded from servers
```

## Commands

`/scriptcraft`, `/sc` and `/script` are the same command. They need **operator level 2**. Script names complete with Tab.

| Command | What it does |
|---|---|
| `/sc help` | short help |
| F3 + `` ` `` | studio: home, journal, and editing server scripts (operators only; the key is not in Controls) |
| `/sc reload` | reload every script |
| `/sc reload <script>` | reload one script |
| `/sc enable <script>` | enable a disabled script |
| `/sc disable <script>` | disable a script (the file is renamed to `-name.sc`) |
| `/sc list` | scripts, commands, variables, and errors |
| `/sc config reload` | reread `scriptcraft/config.toml` without reloading scripts |
| `/sc resources reload` | reread `resources/assets` and send changes to online players |
| `/sc ids` | which blocks and items have a real id, and which are still in a temporary slot |
| `/sc give <players> <id> [count]` | give a script block or item by its id |

Custom blocks and items that exist at startup are registered under their real id (`example:ruby_block`: vanilla `/give`, F3, and `facing`). A block added with `/sc reload` shows up immediately in a temporary slot (`scriptcraft:dyn_block_N` / `dyn_item_N`) and moves to its real id, together with already placed copies, after the next server and client restart. Clients do not need the script files.

## Documentation

- **Site:** https://elgerelg.github.io/ScriptCraft/ — search, categories, and an example on every syntax line. Built from [`docs/`](docs) with GitHub Pages (Settings, Pages, Deploy from a branch, `main` / `docs`).
- **Examples:** https://elgerelg.github.io/ScriptCraft/examples.html
- **Getting started:** https://elgerelg.github.io/ScriptCraft/start.html
- **Source examples:** [`examples/`](examples)

## Links

| | |
|---|---|
| Repository | https://github.com/ElgerElg/ScriptCraft |
| Issues | https://github.com/ElgerElg/ScriptCraft/issues |
| Documentation | https://elgerelg.github.io/ScriptCraft/ |
| License | [MIT](LICENSE) |

## Status

**Alpha (0.3.7).** The core works, and it has not been fully proven in every world, so bugs and syntax changes are still possible. If something breaks, [open an issue](https://github.com/ElgerElg/ScriptCraft/issues) and include the relevant log.

A few limits worth knowing before you build on it:

- Screen slots and list rows are not a real chest. A click runs a command. The vanilla hotbar stays.
- `on key` observes a key. It does not swallow it. To stop E from opening the inventory, use `on open inventory` with `cancel event`.
- If `cancel event` appears anywhere in an `on open` handler, including inside `if`, the vanilla screen is always blocked.
- Client and server jars must match. The network protocol is part of the version.

## Roadmap

- Real container slots (`AbstractContainerMenu`), text fields, and server-side button callbacks
- Effects and targets for mobs and blocks
- Animations (PlayerAnimator / GeckoLib)
- Generated models and blockstates, without writing JSON by hand
- A script catalog on the site
- A public addon API, and a second engine (JavaScript or Lua)

## For developers

Architecture, the parser, networking, resource sync, the config, and how to add a documentation entry are in **[docs/TECHNICAL.md](docs/TECHNICAL.md)** (that file is still in Russian).

---

<div align="center">
<sub>Made for Minecraft Forge 1.20.1 · ScriptCraft is not affiliated with Mojang, Microsoft, or SkriptLang</sub>
</div>
