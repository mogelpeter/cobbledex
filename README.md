# CobbleDex

A NeoForge 1.21.1 mod for Cobblemon multiplayer servers. Embeds a lightweight HTTP server that serves a live Pokédex website showing caught and seen Pokémon for every online player, with real-time updates pushed to the browser via SSE.

---

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x |
| Cobblemon | 1.7.3+ |

Server-side only — players do not need this mod installed on their clients.

---

## Installation

1. Download `cobbledex-1.0.0.jar`.
2. Drop it into the server's `mods/` folder.
3. Start (or restart) the server.

That's it. The web server starts automatically when Minecraft's server starts.

---

## Accessing the Pokédex

Open a browser on any machine that can reach the server and navigate to:

```
http://<server-ip>:8080
```

If the server is running locally, use:

```
http://localhost:8080
```

### What you'll see

- **One tab per online player** — tabs show player name and number of species caught.
- **Green cells** = caught Pokémon (includes sprite). Click for full details.
- **Blue silhouette cells** = seen but not yet caught.
- **Live updates** — the page updates automatically when a player logs in, logs out, or catches a new Pokémon. No refresh needed.

### Pokémon details (click any caught cell)

- Official artwork sprite (with shiny variant if applicable)
- Type badges
- All individual Pokémon of that species the player owns, each showing:
  - Nickname, level, nature, gender
  - Location (Party or PC box name)
  - Moves (up to 4)
  - IVs with color coding (green = 30–31, red = 0–5)

---

## Configuration

On first start NeoForge writes a config file at:

```
config/cobbledex-server.toml
```

```toml
[server]
  # TCP port the web server listens on
  port = 8080

  # Bind address. Use "0.0.0.0" to listen on all interfaces,
  # or "127.0.0.1" to restrict to localhost only.
  host = "0.0.0.0"
```

Edit the file and restart the server to apply changes.

### Firewall / router

If players outside the local network need access, open and forward the configured port (default 8080) to the server machine. This is separate from the Minecraft game port (25565).

### Security note

The web server has no authentication. Anyone who can reach the port can view Pokémon data for all online players. Options:

- Set `host = "127.0.0.1"` to restrict access to the machine running the server.
- Place a reverse proxy (e.g., nginx) in front with HTTP basic auth.

---

## Building from Source

### Prerequisites

- Java 21 (Eclipse Adoptium or equivalent)
- Cobblemon 1.7.3 NeoForge JAR — update the path in `build.gradle` line 23 to match your installation.

### Steps

```bash
git clone https://github.com/mogelpeter/cobbledex.git
cd cobbledex

# Windows
.\gradlew.bat build -x test

# Linux / macOS
./gradlew build -x test
```

Output JAR: `build/libs/cobbledex-1.0.0.jar`

### Running unit tests (no Minecraft required)

```bash
.\gradlew.bat unitTest   # Windows
./gradlew unitTest        # Linux / macOS
```

The standard `test` task is intentionally skipped because NeoForge's test runner requires a full Minecraft environment. `unitTest` runs the same tests against an isolated classpath that excludes NeoForge entirely.

---

## How it works

```
Minecraft server
  └─ CobbleDex mod (NeoForge)
       ├─ Cobblemon events → DataCache (in-memory, per-player snapshot)
       ├─ SseManager       → push JSON updates to open browser tabs
       └─ HttpServerManager (com.sun.net.httpserver)
            ├─ GET /            → index.html
            ├─ GET /static/*    → style.css, app.js
            ├─ GET /api/data    → full JSON snapshot of all online players
            └─ GET /events      → SSE stream (playerUpdate / playerLeft events)
```

Sprites are loaded client-side from [PokeAPI's open sprite repository](https://github.com/PokeAPI/sprites) — no data is sent to third parties from the server.

---

## License

[MIT](LICENSE)
