# 🎨 PANDUAN PENGGUNAAN MODEL 3D & RESOURCE PACK - HYROCOSMETICS

Panduan resmi untuk Server Owner mengenai struktur folder, cara menambahkan model 3D Blockbench, pengaturan CustomModelData, dan distribusi Resource Pack.

---

## 📁 1. Struktur Folder Khusus Server Owner

HyroCosmetics telah menyediakan folder khusus yang terstruktur rapi di dalam `plugins/HyroCosmetics/`:

```
plugins/HyroCosmetics/
├── config.yml                         <-- Pengaturan umum plugin
├── cosmetics.yml                      <-- File katalog kosmetik terpusat
├── menugui.yml                        <-- Pengaturan tampilan GUI Wardrobe
│
├── models/                            <-- [FOLDER KHUSUS MODEL SERVER OWNER]
│   ├── bbmodels/                      <-- Tempat arsip file mentah .bbmodel (Blockbench)
│   │   ├── hats/                      <-- pirate_hat, cyber_visor, dll.
│   │   ├── backpacks/                 <-- cyber_jetpack, treasure_chest, dll.
│   │   ├── wings/                     <-- angel-wing, demon-wing, dragon-wing, dll.
│   │   ├── balloons/                  <-- bs_balloon_fox, bs_balloon_sniffer, dll.
│   │   └── hand/                      <-- cutlass, cyber_blade, egg_staff, dll.
│   │
│   └── custom_json/                   <-- Tempat file model JSON siap pakai Minecraft
│       ├── hats/                      <-- chicken_hat.json, diving_helmet.json, dll.
│       ├── backpacks/                 <-- backpack_lvl1.json s/d lvl5.json, dll.
│       ├── wings/                     <-- angel_wings.json, demon_wings.json, dll.
│       ├── balloons/                  <-- bs_balloon_bison.json, dll.
│       └── hand/                      <-- ethereal_witch_broom.json, dll.
│
├── resourcepack/                      <-- [FOLDER RESOURCE PACK SIAP PAKAI]
│   ├── pack.mcmeta                    <-- Metadata pack format Minecraft (1.21.x)
│   └── assets/
│       ├── minecraft/
│       │   ├── models/item/           <-- Override paper.json & brick.json
│       │   └── textures/custom/       <-- Tekstur balon (FarmBloons)
│       ├── ax_free_hats_pack/         <-- Model & tekstur Topi Artillex
│       ├── ax_wings_pack/             <-- Model & tekstur Sayap 3D Artillex
│       ├── adventure_backpacks/       <-- Model & tekstur Tas Petualang
│       ├── witch_pack/                <-- Model & tekstur Topi & Sapu Sihir
│       └── ero_feral_fantasy_set_v2/  <-- Model & tekstur Bando Hewan & Ekor
│
└── cosmetics/                         <-- [FOLDER KONFIGURASI YML MODULAR]
    ├── hats/hats.yml
    ├── backpacks/backpacks.yml
    ├── wings/wings.yml
    ├── balloons/balloons.yml
    ├── hand/hand.yml
    ├── foot/foot.yml
    ├── trails/trails.yml
    └── kill_effects/kill_effects.yml
```

---

## 🛠️ 2. Cara Menambahkan Model Kosmetik Baru dari Blockbench

1. **Buka Blockbench**:
   - Buat model atau buka model yang sudah ada (`.bbmodel`).
   - Simpan file `.bbmodel` ke `plugins/HyroCosmetics/models/bbmodels/<kategori>/` agar Anda selalu memiliki arsip aslinya jika ingin mengedit di masa depan.

2. **Ekspor ke Minecraft JSON**:
   - Di Blockbench, klik menu `File` -> `Export` -> `Export Minecraft Block/Item Model`.
   - Simpan file `.json` ke:
     1. `plugins/HyroCosmetics/models/custom_json/<kategori>/` (untuk arsip plugin).
     2. `plugins/HyroCosmetics/resourcepack/assets/<namespace_anda>/models/<nama_model>.json`.

3. **Simpan Tekstur**:
   - Ekspor atau simpan file tekstur `.png` ke:
     `plugins/HyroCosmetics/resourcepack/assets/<namespace_anda>/textures/<nama_tekstur>.png`.

---

## 🔢 3. Cara Mengatur CustomModelData

1. **Daftarkan di `paper.json`** (atau item dasar lainnya):
   Buka `plugins/HyroCosmetics/resourcepack/assets/minecraft/models/item/paper.json`.
   Tambahkan baris predicate baru di dalam array `"overrides"`:
   ```json
   { "predicate": { "custom_model_data": 12001 }, "model": "my_pack:super_hat" }
   ```

2. **Daftarkan di Konfigurasi Plugin**:
   Buka `plugins/HyroCosmetics/cosmetics/hats/hats.yml` (atau `cosmetics.yml`), lalu tambahkan entri kosmetik:
   ```yaml
   cosmetics:
     super_hat:
       type: HAT
       display_name: "<gradient:#ff9900:#ff5500><bold>Topi Super Keren 3D</bold></gradient>"
       material: "PAPER"
       custom_model_data: 12001
       lore:
         - "<gray>Topi 3D eksklusif edisi terbatas!</gray>"
       price: 5000.0
       permission: "hyrocosmetics.hat.super"
   ```

3. **Reload Plugin**:
   Ketik perintah di server:
   ```
   /cosmetics reload
   ```
   Kosmetik baru akan otomatis muncul di menu `/wardrobe` atau `/cosmetics`!

---

## 🔌 4. Integrasi ItemsAdder & Oraxen (Opsional)

Jika server Anda telah menggunakan plugin **ItemsAdder** atau **Oraxen**, HyroCosmetics dapat langsung memanggil item tersebut secara otomatis!

### Menggunakan ItemsAdder:
```yaml
  chicken_hat:
    type: HAT
    display_name: "<yellow><bold>Topi Ayam 3D</bold></yellow>"
    material: "PAPER"
    itemsadder_item: "ax_free_hats_pack:chicken_hat"
```

### Menggunakan Oraxen:
```yaml
  adventure_backpack:
    type: BACKPACK
    display_name: "<gold><bold>Tas Petualang</bold></gold>"
    material: "PAPER"
    itemsadder_item: "oraxen:backpack_lvl1"
```
*(Cukup tambahkan awalan `oraxen:` atau `ox:`)*

---

## 📦 5. Cara Mengompres & Membagikan Resource Pack ke Pemain

1. Buka folder `plugins/HyroCosmetics/resourcepack/`.
2. Pilih file `pack.mcmeta` dan folder `assets/`.
3. Klik kanan -> `Compress to ZIP` (atau gunakan 7-Zip/WinRAR).
4. Beri nama file, misalnya `HyroCosmetics_Pack.zip`.
5. Anda dapat mengunggah file zip tersebut ke:
   - **MCPacks** (https://mc-packs.net/) -> Anda akan mendapatkan link unduhan langsung dan hash SHA-1.
   - Masukkan link tersebut ke file `server.properties` server Minecraft Anda pada baris:
     ```properties
     resource-pack=https://download.mc-packs.net/pack/xxxxx.zip
     resource-pack-sha1=xxxxxx
     require-resource-pack=true
     ```
6. Saat pemain bergabung ke server, mereka akan otomatis mengunduh resource pack dan melihat seluruh model 3D (Topi, Sayap, Ransel, Balon, dan Senjata) dengan sempurna!
