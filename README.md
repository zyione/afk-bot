# AI Bot — Fabric Server-Side Mod for Minecraft 1.21.11

A server-side only Fabric mod that spawns a stationary fake-player bot which swings a sword in the exact direction you were looking when you ran the command. No client mod required — vanilla clients connect fine.

---

## 🎮 What It Does

1. **You** stand at your farm's kill spot and aim at where mobs arrive
2. Run `/ai left_click`
3. A bot spawns at your exact position, locked to your exact facing direction
4. The bot auto-grabs the best sword from the nearest chest
5. It swings on every full attack cooldown — full-charge hits with knockback, crits, and enchant effects
6. When the sword hits 10% durability, it deposits it in the nearest barrel and grabs the next one
7. When the chest is empty, the bot idles and notifies you
8. Run `/ai stop` to despawn

**Perfect for:** Mob grinders, spawner farms, XP farms — any setup where you position yourself at the kill spot.

---

## 📋 Commands

| Command | Description |
|---|---|
| `/ai left_click` | Spawn a bot at your position facing where you're looking |
| `/ai stop` | Despawn your bot (returns held item to chest) |
| `/ai status` | Show bot state, held item, durability, chest/barrel positions |

---

## 🔧 How It Works

### Bot Behavior
- **Stationary** — never moves, never turns, never pathfinds
- **Locked direction** — yaw/pitch captured at command time, locked forever
- **Full-charge attacks** — uses Minecraft's real cooldown system (waits for ≥95% charge)
- **3.5 block reach** — raycasts from eye position in locked direction
- **Vanilla damage** — knockback, critical hits, and enchantments all work normally

### Item Management
- Scans **16 blocks** in all directions on spawn for the nearest chest and barrel
- **Weapon priority**: Netherite Sword > Diamond > Iron > Stone > Golden > Wooden > Any Sword > Axes > Anything
- **Auto-swap** at 10% durability — deposits to barrel, takes next from chest
- **Barrel optional** — if no barrel found, damaged items are dropped on the ground

### Chunk Loading
- Registers a **forced chunk ticket** (5×5 chunk area) at spawn position
- Bot keeps working even if all real players leave the area
- Ticket removed on despawn

---

## 🏗️ Project Structure

```
afk-bot/
├── build.gradle                          ← Loom 1.14, MC 1.21.11
├── gradle.properties                     ← Version pins
├── settings.gradle                       ← Fabric Maven repo
└── src/main/
    ├── java/com/aibot/
    │   ├── AiBotMod.java                 ← Mod entrypoint, event hooks
    │   ├── BotPlayer.java                ← Fake player entity, tick/attack/swap logic
    │   ├── BotState.java                 ← IDLE / ATTACKING / SWAPPING enum
    │   ├── BotManager.java               ← Per-player registry, spawn/despawn, chunks
    │   ├── command/
    │   │   └── AiCommand.java            ← /ai left_click, /ai stop, /ai status
    │   └── mixin/
    │       └── ServerPlayerEntityAccessor.java  ← Network handler accessor
    └── resources/
        ├── fabric.mod.json               ← Mod metadata (server-side only)
        └── aibot.mixins.json             ← Mixin configuration
```

---

## 🚀 Building & Installing

### Prerequisites

- **Java 21** (JDK) — [Download from Adoptium](https://adoptium.net/)
- **Gradle** — included via wrapper (no manual install needed)

### Step 1: Install the Gradle Wrapper

You need the Gradle wrapper in the project. Run this once:

```bash
# If you have Gradle installed globally:
gradle wrapper --gradle-version 8.10

# OR download the wrapper files manually from a Fabric template
```

If you don't have Gradle installed globally, the easiest way is:
1. Go to https://fabricmc.net/develop/template/
2. Generate a template for MC 1.21.11
3. Copy the `gradle/` folder and `gradlew`/`gradlew.bat` files into this project

### Step 2: Build the Mod

```bash
# On Windows:
.\gradlew.bat build

# On Linux/Mac:
./gradlew build
```

The compiled `.jar` will be at:
```
build/libs/aibot-1.0.0.jar
```

### Step 3: Install on Your Server

1. Install **Fabric Loader** on your Minecraft 1.21.11 server
   - Download from https://fabricmc.net/use/installer/
   - Run: `java -jar fabric-installer.jar server -mcversion 1.21.11`

2. Download **Fabric API** (v0.141.3+1.21.11) and put it in the `mods/` folder
   - https://modrinth.com/mod/fabric-api

3. Copy `build/libs/aibot-1.0.0.jar` into the server's `mods/` folder

4. Start the server — the mod loads server-side only. Vanilla clients connect normally.

---

## ⚙️ Environment

| Property | Value |
|---|---|
| Minecraft | 1.21.11 (Java Edition) |
| Fabric Loader | ≥0.18.4 |
| Fabric API | 0.141.3+1.21.11 |
| Yarn Mappings | 1.21.11+build.1 |
| Java | 21 |
| Loom | 1.14-SNAPSHOT |
| Side | Server only |
| Client required | **No** — vanilla clients work |

---

## 📝 State Machine

```
         spawn
           │
           ▼
     [scan for chest + barrel]
           │
           ▼
    SWAPPING ──────────────────────────────────────────────┐
    (fetch item from chest)                                │
           │                                               │
           │  item acquired                                │
           ▼                                               │
    ATTACKING ──── every tick: cooldown >= 0.95? → swing  │
           │                                               │
           │  durability <= 10%                            │
           ▼                                               │
    SWAPPING ──── deposit to barrel → take next from chest ┘
           │
           │  chest empty
           ▼
    IDLE ──── notify owner, do nothing until /ai stop
```

---

## ⚠️ Intentional Design Decisions

- **No pathfinding** — bot never moves
- **No target tracking** — bot doesn't follow entities
- **No dynamic facing** — yaw/pitch locked at spawn
- **No navigation** — no A*, no movement input, no jump

The player's positioning before running the command IS the entire setup. The bot is a stationary sword-swinger for farms where mobs come to a fixed kill point.

---

## 📜 License

MIT