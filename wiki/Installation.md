# Installation

## Which file

| Your Minecraft | File | Java |
|---|---|---|
| 26.3 | `ferrum-<version>+26.3.jar` | 25 |
| 1.21.11 | `ferrum-<version>+1.21.11.jar` | 21 |

Both need **Fabric Loader 0.20.1 or newer** and **Fabric API** for the same Minecraft version.

## PC (Windows, macOS, Linux)

1. Install Fabric with the official installer, or create a Fabric instance in Prism Launcher,
   MultiMC, Modrinth App or CurseForge App.
2. Download Fabric API and Ferrum for your Minecraft version.
3. Put both jars in the instance's `mods` folder.
4. Start the game. `/ferrum status` or the F3 screen confirms Ferrum is running.

## Android

Minecraft 26.x does not start on current Android launchers because they do not include SDL3.
Use **1.21.11**.

1. In Zalith, Pojav, MJ or a similar launcher, install Minecraft 1.21.11 with Fabric.
2. Add Fabric API and `ferrum-<version>+1.21.11.jar` to the mods folder.
3. Choose a renderer: **MobileGlues** is recommended on most devices; GL4ES on older ones.
4. Set the memory allocation (see the JVM table in the [[Performance Guide]]).
5. Start the game. Ferrum detects Android and selects the Mobile or Potato preset.

## Updating

Replace the old Ferrum jar with the new one. Your settings in `config/ferrum.json` are kept.

## Removing

Delete the jar. Ferrum only changes vanilla settings that it lowered (render distance, particles,
leaves...); you can raise them again in Video Settings.
