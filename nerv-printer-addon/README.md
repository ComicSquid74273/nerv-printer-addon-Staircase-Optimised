
<div align="center">
  <!-- Logo and Title -->
  <img src="/src/main/resources/assets/nerv/icon.png" alt="logo" width="20%"/>
  <h1>Nerv Printer</h1>
  <p>Nerv Printer is an addon for the Meteor Client allowing you to build mapart from NBT files. It works 100% autonomously and supports both carpet, fullblock, and staircasing. Its main focus is reliability and compatibility with strict anti-cheat servers.</p>

  <!-- Shields -->
[![Release](https://img.shields.io/github/v/release/Julflips/nerv-printer-addon)](https://github.com/Julflips/nerv-printer-addon/releases)
[![Last Commit](https://img.shields.io/github/last-commit/Julflips/nerv-printer-addon)](https://github.com/Julflips/nerv-printer-addon/commits)
[![Issues](https://img.shields.io/github/issues/Julflips/nerv-printer-addon)](https://github.com/Julflips/nerv-printer-addon/issues)
[![Downloads](https://img.shields.io/github/downloads/Julflips/nerv-printer-addon/total)](https://github.com/Julflips/nerv-printer-addon/releases)
[![Stars](https://img.shields.io/github/stars/Julflips/nerv-printer-addon)](https://github.com/Julflips/nerv-printer-addon/stargazers)
<br><br>
[![Discord](https://img.shields.io/badge/Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/UeDVMhTT4A)
</div>

## Carpet (Flat) Printer
The Carpet Printer prints the map line-by-line and does not reuse carpet items, making it only suited for servers where carpet duping is enabled.
You can find the full documentation [here](Documentation/CarpetGuide.md).

## Fullblock Printer
The Fullblock Printer builds flat & staircased fullblock maps line by line.
Printing-only mode is the default: it auto-detects combined single-NBT grids of `128x128` map tiles (including exact `2x2`, `5x5`, and larger footprints), derives the whole map area from a player-built `128x1` north cobblestone anchor occupying one canonical Minecraft map tile, builds the missing outer walkway and one-block-back access supports beyond that platform section, automatically registers nearby shulker supplies, saves and live-revalidates that setup for later maps, adds verified suspended end-rod lighting, and leaves the completed structure in place. Every two-column pair uses a complete U traversal only when its full material plan and the carried repair pickaxe fit the usable inventory; otherwise it uses separately restocked single lanes. A completed single lane returns through `/kill` and the registered bed, while a full U exits normally at the north walkway. Axe shulkers are ignored. The module widget includes **Reset Printing Config** for starting a new platform setup. The legacy `1x1` map-item handoff and teardown cycle remains available when printing-only is disabled.
This module **will not work on servers where placing blocks in the air is disabled**.
You can find the full documentation [here](Documentation/StaircasedGuide.md).

## Map Namer
Semi-automatically names unnamed map items in inventory. Pauses on anvil break and insufficient xp and can be resumed.

[![Map Namer](https://img.youtube.com/vi/3karXgUGU8U/0.jpg)](https://www.youtube.com/watch?v=3karXgUGU8U)

## Verified on Servers
- Contantiam (Folia with Grim anti-cheat)
- 6b6t
- 8b8t
- 9b9t
- EndCrystal
- MineTexas
- 2B2FR
- FBFT

## Mapart Gallery
A collection of maps printed with this addon:

<div style="overflow-x: auto; white-space: nowrap;">

  <img src="Documentation/Gallery/TheObservatory.png" alt="The Observatory" height="200">
  <img src="Documentation/Gallery/02.png" alt="02" height="200">
  <img src="Documentation/Gallery/TarotCards.png" alt="Tarot Cards" height="200">
  <img src="Documentation/Gallery/IdiotSandwich.png" alt="Idiot Sandwich" height="200">
  <img src="Documentation/Gallery/WelcomeToHell.png" alt="Welcome To Hell" height="200">
  <img src="Documentation/Gallery/AsukaCollage.png" alt="Asuka Collage" height="200">
  <img src="Documentation/Gallery/CC&Lelouch.png" alt="CC & Lelouch" height="200">
  <img src="Documentation/Gallery/HoloAtDawn.png" alt="Holo At Dawn" height="200">
  <img src="Documentation/Gallery/JulflipsMazeGame.png" alt="Juflips Maze Game" height="200">
  <img src="Documentation/Gallery/MakimasEyes.png" alt="Makima's Eyes" height="200">
  <img src="Documentation/Gallery/MapOfJapan.png" alt="Map Of Japan" height="200">
  <img src="Documentation/Gallery/MeAndTheBoysInTheEnd.png" alt="Me And The Boys In The End" height="200">
  <img src="Documentation/Gallery/Money.png" alt="Money" height="200">
  <img src="Documentation/Gallery/Nosferatu.png" alt="Nosferatu" height="200">
  <img src="Documentation/Gallery/Restraint.png" alt="Restraint" height="200">
  <img src="Documentation/Gallery/TheFirstDate.png" alt="The First Date" height="200">
  <img src="Documentation/Gallery/Toradora!.png" alt="Toradora!" height="200">
  <img src="Documentation/Gallery/BigOwO.png" height="200">

</div>
