# 👑 HyroCosmetics — Advanced 3D Cosmetics Engine

**HyroCosmetics** is a 3D cosmetics plugin for Minecraft servers using the Paper API. The current build targets **Paper 1.21** and **Java 21**. Compatibility with other server/API versions has not been verified.

---

## 🌐 Compatibility
* **Paper API**: 1.21 (`paper-api:1.21-R0.1-SNAPSHOT`).
* **Java**: 21.
* Other Paper/Purpur or Minecraft versions may work, but are not covered by the current build configuration.

---

## ✨ Features

### 🎒 1. Real-Time 3D Motion Tracking & Dynamic Poses
* **Smooth Client-Side Tracking**: Backpacks, wings, and balloons are dynamically attached with zero trailing lag.
* **Natural Sneak & Head Tilting**: Cosmetics dynamically tilt when players sneak or change pitch, matching their body orientation naturally.
* **Horizontal Pose Synchronization (1.21+)**: Automatically detects **Swimming**, **Crawling**, **Elytra Gliding**, and **Trident Spin Attack** — rotating backpacks and wings 90° horizontally to naturally follow the player's spine.
* **Seat & Mount Stability**: Built-in listeners for **GSit / Chairs** and vehicles (`VehicleEnter/Exit`, `EntityMount/Dismount`) ensure models never detach or float away when sitting or riding horses/boats.

### 🎩 2. Non-Destructive Wearable System
* Players can equip custom 3D hats without losing their armor!
* Netherite, Diamond, and enchanted helmets can still be worn with full protection while displaying custom cosmetic models.

### 👁️ 3. Self-View Controls
* The server cannot read whether a player's client is in first- or third-person camera mode.
* Cosmetics can be hidden from the wearer or shown using `/cosmetics viewself`. Angle-based visibility settings are configurable, but are not a reliable substitute for camera-mode detection.

### 📦 4. Universal Custom Model & 1.21.2+ Component Support
* **Multi-Plugin Support**: Out-of-the-box compatibility with **ItemsAdder**, **Nexo**, **Oraxen**, and standard vanilla **CustomModelData**.
* **Modern Vanilla Item Models (1.21.2+)**: Native support for Minecraft's data-driven item model format (`item_model: "namespace:model_path"`). Run custom 3D models without strict integer IDs!
* **Hex Armor Tinting (`color: "#HEX"`)**: Freely tint leather cosmetics (hats, footwear, robes) to any hex color code in config files without creating separate texture files.
* **Fine-Tuned Offsets**: Easily configure custom X/Y/Z offsets, rotations, scales, and passenger coordinates for each individual model.

### 💰 5. Interactive Wardrobe GUI, Economy & Vouchers
* **Multi-Page Pagination**: Browse hundreds of cosmetics effortlessly with intuitive page buttons (`« Previous Page`, `Page X of Y`, `Next Page »`).
* **Active Profile Slot**: Preview all currently equipped cosmetics directly at slot 4 of the main wardrobe GUI.
* **Economy Wardrobe Shop**: Players can preview and unlock cosmetics using in-game currency via Vault.
* **Physical Vouchers**: Convert any cosmetic into a tradeable physical item voucher with persistent NBT/PDC data via `/cosmetics convert`.
* **High-Performance Storage**: Fast, thread-safe player data storage powered by SQLite with HikariCP connection pooling.

---

## 🎭 Supported Cosmetic Types

| Category | Description |
| :--- | :--- |
| **Hats & Masks** | 3D head accessories that sync seamlessly with player pitch and yaw without removing helmets. |
| **Backpacks** | 3D backpacks and jetpacks mounted cleanly behind the player with sneak and swimming tilt. |
| **Wings** | Animated wings placed across the upper shoulder blades. |
| **Balloons** | Cute 3D companions floating above and behind the player with invisible tethers. |
| **Hand Items** | Custom off-hand accessories, floating handheld props, and walking canes. |
| **Footwear** | 3D boots, sneakers, and step auras. |
| **Trails & Auras** | Dynamic particle effects (including 1.21 Breeze Gust, Cherry Blossom, Trial Omen, and Vault Runes). |
| **Kill Effects** | Visceral PvP elimination effects (including 1.21 Wind Burst, Mace Ground Slam, Ominous Mist, and Blood Explosions). |

---

## ⌨️ Commands & Permissions

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/cosmetics` | `hyrocosmetics.use` | Opens the 3D Cosmetics Wardrobe GUI |
| `/cosmetics list [category]` | `hyrocosmetics.use` | Lists all loaded cosmetics and active models in chat |
| `/cosmetics info <id>` | `hyrocosmetics.use` | Displays technical details for a specific cosmetic |
| `/cosmetics viewself` | `hyrocosmetics.use` | Toggles 3D cosmetic visibility for first-person / F5 |
| `/cosmetics unequip [category]` | `hyrocosmetics.use` | Unequips your current active cosmetic or category |
| `/cosmetics convert <id>` | `hyrocosmetics.use` | Converts an owned cosmetic into a tradeable voucher item |
| `/cosmetics equip <id> [player]` | `hyrocosmetics.admin` | Forces equipping a cosmetic to a player |
| `/cosmetics reload` | `hyrocosmetics.admin` | Reloads all configurations, menus, and model caches |

*Aliases*: `/hc`, `/hcosmetics`, `/wardrobe`, `/topi`, `/pakaian`, `/cosmetic`

---

## ⚙️ Requirements & Installation

### Requirements:
* **Server Software**: Paper-compatible server using the Paper 1.21 API.
* **Java Version**: **Java 21**
* **Build dependency**: HyroCore API **1.1.0** must be installed in the local Maven repository. `build.bat` builds and installs it automatically when a sibling `..\HyroCore` project is present.
* **Optional Integrations**:
  * [Vault](https://www.spigotmc.org/resources/vault.34315/) (For in-game currency economy purchases)
  * [PlaceholderAPI](https://placeholderapi.com/) (For scoreboard and tablist cosmetic placeholders)
  * [GSit](https://www.spigotmc.org/resources/gsit.62325/) (For sitting and pose compatibility)
  * [ItemsAdder](https://itemsadder.devs.beer/) / [Nexo](https://polycode.dev/) / [Oraxen](https://oraxen.com/) (For custom 3D model packs)

### Build from Source:
1. Install Java 21 and Maven.
2. Ensure HyroCore API 1.1.0 is installed locally (or place the HyroCore project beside this repository as `..\HyroCore`).
3. Run `build.bat` on Windows, or run `mvn clean package` after installing the HyroCore dependency.
4. The plugin JAR is created in `target/`. Copy it to the server's `plugins/` directory and restart the server.
