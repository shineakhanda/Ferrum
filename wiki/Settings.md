# Settings

Open with **Options → Video Settings**, **F6**, Mod Menu, or `/ferrum config`. Ferrum options are
staged until you press **Apply** or **Done**; **Undo** discards them. Vanilla options (marked in
their description) apply immediately, like in vanilla. Every option can also be set with
`/ferrum set <key> <value>`.

Options that change how chunks are built (block face culling, render pass optimization) need a
chunk reload: press **F3 + A**.

## General

| Option | Key | Notes |
|---|---|---|
| Ferrum Preset | – | Auto picks one for your hardware. Command: `/ferrum preset <name>`. |
| Render Scale | `renderScale` | 50–100%. The world renders at this resolution; the HUD stays sharp. |
| FSR Upscaling | `fsr` | Sharper upscaling for render scale. |
| FSR Sharpness | `fsrSharpness` | 0–100%. Lower it if edges look harsh. |
| Sharp Upscale | `renderScaleSharp` | Nearest-neighbour upscaling instead of bilinear/FSR. |
| Render distance, simulation distance, max framerate, VSync, brightness, GUI scale, fullscreen, view bobbing, FOV and distortion effects, menu blur | – | Vanilla options. |

## Quality

Vanilla graphics options: graphics preset, clouds, cloud range, weather radius, particles, smooth
lighting, biome blend, entity distance, entity shadows, mipmaps, anisotropic filtering, texture
filtering, cutout leaves, improved transparency, vignette, chunk fade-in.

## Performance

| Option | Key | What it does |
|---|---|---|
| Entity Culling | `entityCulling` | Skips entities beyond Entity Distance. Bosses and you are never culled. |
| Cull Players | `cullPlayers` | Also distance-cull other players. Off by default. |
| Block Entity Culling | `blockEntityCulling` | Skips far chests, signs, banners... Beacon beams are exempt. |
| Particle Limiter | `particleLimiting` | Caps the particle count and drops far particles. |
| Animate Only Visible Textures | `animateOnlyVisible` | Animated block textures update only when visible. |
| Entity Tick Culling | `entityTickCulling` | Far, off-screen mobs update less often on your side. |
| Ambient Block Effects | `ambientParticleDensity` | Torch smoke, drips and leaf particles density. |
| Text Width Cache | `textWidthCache` | Caches font measurements. |
| Adaptive Quality | `adaptiveQuality` | Lowers Ferrum's budgets under sustained low FPS. |
| Lightmap Cache | `lightmapCache` | Redraws the lightmap only when lighting changes. |
| Render Pass Optimization | `renderPassOptimization` | Opaque cutout faces use the solid pass. |
| Block Face Culling | `faceCulling` | Faces pointing away from the camera are not drawn. |
| Occlusion Culling | `occlusionCulling` | Entities hidden behind blocks are not drawn. |
| Occlusion: Block Entities | `occlusionBlockEntities` | Same for chests, signs, banners... |

## Details

Distance fog, clouds, weather, sky, stars, sun, moon, sunrise/sunset. Turning a detail off skips
its whole render pass.

## Render

Per-type toggles: water, lava, fire, portal and other block animations; block break, rain and drip,
explosion, campfire smoke, potion, falling leaf and torch flame particles; item frames, armor stands,
paintings, mob name tags, beacon beams, enchanting table book.

## Dynamic Lights

Light radius, maximum light sources, entity lights and dropped item lights. Dynamic lights pause
automatically when another dynamic light mod is installed or an Iris shader pack is active.

## Advanced

| Option | Key | Notes |
|---|---|---|
| Replace Video Settings | `replaceVideoSettings` | Off brings back the vanilla screen. |
| Vanilla Video Settings | – | Opens the vanilla screen once. |
| FPS Cap | `fpsCap` | Ferrum's own cap; 0 = off. |
| Iris Safe Mode | `irisSafeMode` | Pause dynamic lights while a shader pack is active. |
| Tune Vanilla Options | `autoApplyVanillaOptions` | Let presets lower vanilla settings. |
| Debug HUD | `debugHud` | FPS, frame time and counters in the top-left corner. |
| Graphics API (26.3) | `vulkanMode` | Auto, Vulkan or OpenGL. Needs a restart. |
| Force Terrain Multi-Draw (26.3) | `forceMultiDraw` | Experimental. Batched terrain drawing on GPUs where Mojang disabled it. |
