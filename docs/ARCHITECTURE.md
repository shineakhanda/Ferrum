# Architecture

Ferrum has two builds (26.3 in `src/`, 1.21.11 in `mc1.21.11/src/`) and one shared core (`common/`).

## Shared core (`common/src/client/java`)

- `config.FerrumConfig` / `Preset` — JSON model, preset values, clamping, `copy()` for the staged settings screen.
- `state.FerrumState` — hot-path values read by mixins (volatile fields, no allocation, no I/O) plus live counters for F3/HUD.
- `hardware.HardwareProfile` — detected CPU/RAM/GPU/backend/launcher facts and preset recommendation.
- `access.EntityVisibility`, `compat.ModCompat`.

## Frame pipeline hooks

### Render scale — `mixin.GameRendererMixin`
Around `GameRenderer.renderLevel` the main framebuffer reference is swapped for Ferrum's smaller `TextureTarget`, so every vanilla world pass (sky, terrain, entities, particles, weather, hand) renders at `renderScale`%. After `renderLevel` the real target is restored and the scaled image is drawn into it with a full-screen triangle (`TRACY_BLIT` pipeline: `blit_screen` shader, no blending) using a bilinear or nearest sampler. Post effects, entity outlines, screenshots and the GUI run afterwards on the full-size target.

The level renderer's own screen-sized targets (entity outline, translucency) and, on 26.3, the 3D-HUD depth target share passes with the main target, so they are resized to the scaled size while scaling is active and restored to window size when it is turned off. Window resizes re-trigger this.

| | 26.3 | 1.21.11 |
|---|---|---|
| Main target owner | `GameRenderer.mainRenderTarget` | `Minecraft.mainRenderTarget` (via `MinecraftAccessor`) |
| Depth clear | 0.0 (reverse-Z) | 1.0 |
| Extra target | `hud3DTarget` resized too | none |

### Animate only visible textures — `render.AnimatedSpriteTracker`
1. `SectionCompilerMixin` opens a thread-local set at the start of `SectionCompiler.compile` (chunk-builder threads).
2. `ModelBlockRendererMixin` adds the sprite of every quad whose texture is animated (`putQuadWithTint` on 26.3, `putQuadData` on 1.21.11).
3. At the end of `compile` the set is stored on `SectionCompiler.Results`, and `CompiledSectionMeshMixin` copies it onto the section's mesh.
4. `TextureAtlasMixin` maps each `AnimationState` to its sprite when the atlas is uploaded. Each tick, before `uploadAnimationFrames`, the sprites of all visible sections are unioned; `needsToDraw()` is wrapped to return false for animated block sprites no visible section uses.

Fluids are meshed by a different path and always animate. No culling happens while a screen is open (inventories show block items) or before any section is visible.

### Terrain multi-draw (26.3 only) — `mixin.LevelRendererMixin`
26.3 has a multi-draw-indirect terrain path that it disables when `HintsAndWorkarounds.multiDrawIndirectHasKnownIssues()` is true (all non-Arc Intel). With `forceMultiDraw` and a device that reports the needed extensions, Ferrum re-enables it each frame. 1.21.11 has no such path.

### F3 — `mixin.DebugEntrySystemSpecsMixin`
Appends a "Ferrum Renderer" block to the system-specs column: version and preset, GL path (desktop / Android / translation layer), active render-scale resolution, terrain draw mode, visible/skipped animated textures, culled entities/block entities/ticks per second.

### Culling and detail hooks
- `EntityRenderDispatcher.shouldRender` HEAD: distance culling; RETURN: records the tick an entity was last drawn (for tick culling).
- `BlockEntityRenderDispatcher.tryExtractRenderState` HEAD: distance culling. 26.3 uses the passed "globally rendered" flag; 1.21.11 checks `BlockEntityRenderer.shouldRenderOffScreen()`.
- `ParticleEngine.add` / `tick`: cap and spawn distance.
- `ClientLevel.tickNonPassenger` HEAD: entity tick culling; `animateTick` constant 667: ambient effect density.
- Cloud/weather/sky HEAD cancellations; `FogRenderer.setupFog`: pushes render-distance fog out (26.3 at RETURN on the returned `FogData`; 1.21.11 before the fog buffer is mapped, via `@Local FogData`).
- `Font.width(String)`: cache.

### Block face culling — `render/terrain/*`, `TerrainDrawMixin`
At build time `SectionCompilerMixin` wraps the `results.renderedLayers.put(layer, mesh)` call and `FaceSorter` reorders each SOLID/CUTOUT mesh in place into the groups -X, -Y, -Z, unassigned, +X, +Y, +Z (classified from quad geometry and counter-clockwise winding), recording each group's extreme plane. At draw time `TerrainDrawMixin` wraps the `List.add` of each section draw (26.3: `IndexedDraw` in `extractSectionDrawGroups`, used by both direct and indirect drawing; 1.21.11: `RenderPass.Draw` in `prepareChunkRenders`) and replaces it with one draw per contiguous run of groups that can face the camera. See docs/SODIUM-ANALYSIS.md.

### Render pass optimization — `RenderPassOptimizer`, `SpriteContentsMixin`
Sprites are scanned once for transparent pixels. During a section compile, CUTOUT quads with fully opaque sprites are written into the section's SOLID builder (26.3: wrapping `BlockQuadOutput.put`; 1.21.11: wrapping `VertexConsumer.putBulkData` when the consumer is the CUTOUT builder).

### Occlusion culling — `culling/OcclusionCuller` (common), `LevelOpacity`, `EntityCulling`
The render thread registers frustum-visible entities and block entities and reads the last result; a background thread ray-traces (3D DDA, 9 rays per box, per-pass opacity cache) through `LevelChunkSection` data. Results are only trusted while camera and target stay within 1.25 blocks of where they were computed.

### FSR — `render/FsrUpscaler`, `assets/ferrum/shaders/core/fsr_*.fsh`
When render scale is active, upscaling runs EASU into a full-size intermediate target, then RCAS into the main target. Sharpness is a shader define (one cached pipeline per 5% step). Any failure switches to bilinear for the session.

### Compatibility — `compat/CompatRules`, `FerrumMixinPlugin`, `ModCompat`
`CompatRules` maps features to their mixins and to the mods that replace them. `FerrumMixinPlugin.shouldApplyMixin` skips those mixins when such a mod is installed; runtime rules (EntityCulling, Iris shader packs) are applied in `FerrumState.apply` and `GameRendererMixin`.

## Other components
- `hardware.HardwareDetector` — 26.3 reads `DeviceInfo`; 1.21.11 reads `GpuDevice` renderer/vendor/version strings and guesses the device type.
- `hardware.VulkanSupport` — 26.3 selects Minecraft's Vulkan backend; 1.21.11 reports OpenGL / VulkanMod only.
- `tuning.VanillaOptionsApplier` — preset-driven vanilla options plus mobile/translation-layer tuning (OIT off, mipmaps, biome blend, vignette, fade-in, simulation distance). Only lowers values.
- `tuning.AdaptiveTuner`, `dynamic.DynamicLights`, `ui.*` (settings screen, HUD), `command.FerrumCommand`.
- `bench.Benchmark` (26.3 only, development tool).

## Safety rules
- Hot paths avoid config I/O, reflection and allocation (the per-tick visible-sprite set is the one exception: one small set per tick).
- The local player, other players (default), bosses and off-screen renderers are never distance-culled.
- Settings are only lowered by presets, never raised.
- Every feature has a config toggle and a GUI row.
