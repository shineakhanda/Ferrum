# Ferrum on Android and low-end devices

## Which jar

| Launcher profile | Jar |
|---|---|
| Minecraft 1.21.11 | `ferrum-1.0.0+1.21.11.jar` (Java 21) |
| Minecraft 26.3 | `ferrum-1.0.0+26.3.jar` (Java 25 — your launcher must ship a Java 25 runtime) |

Both need Fabric Loader 0.20.1+ and the matching Fabric API.

**26.3 on Android:** Minecraft 26.x opens its window through SDL3 (`org.lwjgl.sdl`) instead of GLFW. Zalith 1.4.1.x and Pojav replace LWJGL with their own GLFW-only build and drop `lwjgl-sdl`, so vanilla 26.3 crashes at "Loading library SDL" before any mod loads, whatever the renderer (MobileGlues, GL4ES, Zink, VirGL). That is a launcher limitation; Ferrum cannot fix it. Use **1.21.11** on Android until your launcher ships LWJGL 3.4 with SDL for Android.

## Renderer (GL layer) choice on Android

Ferrum detects the layer from the GL renderer string and environment and reports it in F3 ("Path").

| Layer | Notes |
|---|---|
| **MobileGlues** | Recommended on Adreno/Mali with GLES 3.2. Ferrum picks the MOBILE preset and turns OIT off. |
| **GL4ES** | Works on older devices; slower with many draw calls. Use POTATO or MOBILE and render scale 60-70%. |
| **VirGL / Zink** | Zink needs a good Vulkan driver (Adreno 6xx+ with Turnip). Detected as a GL translation layer. |
| **ANGLE** | Detected; treated like other translation layers. |

Ferrum makes no direct desktop OpenGL calls; every hook goes through Minecraft's own GPU abstraction, so it runs on whatever GL layer Minecraft itself runs on.

## What to set first

1. Preset **MOBILE** (or **POTATO** on 3 GB devices / Unisoc / Helio G-series).
2. **Render scale** 60-75% — the largest single FPS gain on phones, whose screens are 1080p-1440p.
3. Render distance 6-8, simulation distance 4-6.
4. Keep **Animate only visible textures** and **Entity tick culling** on.
5. Details page: clouds off and distance fog on (fog hides chunk loading at short render distance).

## JVM arguments

A mod cannot change JVM settings after the game has started; set these in the launcher. Keep `-Xmx` at least 1-1.5 GB below the device's RAM so Android does not kill the app.

| Device RAM | Arguments |
|---|---|
| 3 GB | `-Xms1G -Xmx1536M -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=8M` |
| 4 GB | `-Xms1536M -Xmx2G -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=16M` |
| 6 GB | `-Xms2G -Xmx3G -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=16M` |
| 8 GB+ | `-Xms2G -Xmx4G -XX:+UseG1GC -XX:MaxGCPauseMillis=40` |

Low-end PCs: same table by system RAM. Do not set `-Xmx` larger than needed — a bigger heap means longer garbage collections on slow CPUs.

## Chip notes

| Chip family | Starting preset | Render scale |
|---|---|---|
| Snapdragon 8 Gen 1+ / 7+ Gen 2 | MOBILE | 75-85% |
| Snapdragon 6xx/7xx, Dimensity 7xxx-8xxx | MOBILE | 65-75% |
| Exynos (Xclipse) | MOBILE | 65-75% |
| Helio G / Dimensity 6xxx, Unisoc T6xx | POTATO | 55-65% |
| Apple Silicon (desktop) | BALANCED | 100% |
| Intel HD/UHD, Vega APUs | LOW_END | 75-85% |

These are starting points; the F3 "Ferrum Renderer" block shows the active render scale resolution.
