# Compatibility

## Other performance mods

Ferrum checks the installed mods when the game starts. If another mod already provides one of
Ferrum's optimizations, or replaces the Minecraft code Ferrum patches, Ferrum's version is not
loaded at all (its mixins are skipped by `FerrumMixinPlugin`). The rules live in one place:
`common/src/client/java/dev/ferrum/client/compat/CompatRules.java`.

| Mod | Ferrum features turned off | Why |
|---|---|---|
| **Sodium** | Block face culling, render pass optimization, animated texture culling, terrain multi-draw, Video Settings replacement | Sodium builds and draws chunks with its own renderer and has its own settings screen. Ferrum's occlusion culling, render scale/FSR, lightmap cache, particle and entity features keep working. Open Ferrum's settings with F6, Mod Menu or `/ferrum config`. |
| **Iris** | Render scale while a shader pack is active | Shader packs use their own render targets. Iris requires Sodium, so the Sodium row applies too. Dynamic lights pause while a pack is active (Iris safe mode). |
| **VulkanMod** | Everything Sodium turns off, plus render scale/FSR and the lightmap cache | VulkanMod replaces Minecraft's graphics backend. |
| **BadOptimizations** | Lightmap cache | BadOptimizations already skips unchanged lightmap updates. |
| **EntityCulling** | Occlusion culling, entity tick culling | EntityCulling does both; running two would double the work. |
| **LambDynamicLights, Sodium Dynamic Lights** | Dynamic lights pause | Only one dynamic light system should run. |
| Lithium | – | Game-logic optimizations; no overlap. |
| FerriteCore | – | Memory optimizations; no overlap. |
| ModernFix | – | Loading and memory; no overlap. |
| ImmediatelyFast | – | GUI and immediate-mode batching; no overlap. |
| More Culling | – | Culls block faces inside leaves and similar blocks at build time; complements Ferrum. |
| C2ME, Krypton, ScalableLux, Noisium | – | Server, networking or world generation; no overlap. |
| Dynamic FPS | – | Limits FPS when the window is unfocused; no overlap. |
| Distant Horizons | – | LOD rendering; no overlap known. |
| Mod Menu | – | Adds a Configure button for Ferrum. |

What Ferrum detected and handed over is shown on the F3 screen ("Compat" line), in
`/ferrum status`, and in the log at startup (`Ferrum compatibility: ...`).

If you find a conflict with another mod, please report it with both mods' versions and the log.

## Hardware

| Hardware | Status | Notes |
|---|---|---|
| Intel HD / UHD / Iris Xe (Windows) | Supported | OpenGL. Older Intel GPUs lack the features for 26.3's Vulkan backend; Minecraft falls back to OpenGL automatically. |
| AMD Radeon / Vega APUs | Supported | |
| NVIDIA | Supported | |
| Apple Silicon (macOS) | Supported | OpenGL through Apple's driver. |
| Android: Adreno, Mali, Xclipse, PowerVR, Unisoc | Supported on 1.21.11 | Through Zalith, Pojav, MJ and similar launchers with MobileGlues, GL4ES, Zink or VirGL. 26.x cannot start on current Android launchers (SDL3 missing). |
| Software rendering (llvmpipe, SwiftShader) | Works | Detected; POTATO preset. |

## Graphics layers on Android

| Layer | Status |
|---|---|
| MobileGlues | Recommended on GLES 3.2 devices |
| GL4ES | Works; use POTATO or MOBILE and render scale 60–70% |
| Zink (Turnip) | Works on Adreno 6xx+ with a good Vulkan driver |
| VirGL | Works, slow |

The FSR upscaler uses GLSL 3.30 features available on all of these. If its shaders fail on a
device, Ferrum logs the error and falls back to bilinear upscaling.
