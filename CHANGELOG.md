# Changelog

All notable changes to Ferrum are listed here. Versions follow `MAJOR.MINOR.PATCH+MINECRAFT`.

## Unreleased

## 1.0.0 – first public release

Builds for Minecraft 26.3 and 1.21.11 (Fabric).

### Rendering
- Block face culling: opaque chunk meshes are sorted by face direction when they are built, and
  groups of faces pointing away from the camera are not drawn. Works with 26.3's multi-draw path.
- Render pass optimization: cutout faces with fully opaque textures are drawn in the solid pass.
- Occlusion culling for entities and block entities, using ray tracing on a background thread.
- Render scale (50–100%) with an FSR 1-style upscaler (EASU + RCAS) or bilinear/nearest upscaling.
- Animated block textures are only updated when a visible chunk uses them.
- Lightmap cache: the lightmap is only redrawn when lighting changes.
- Detail toggles: distance fog, clouds, weather, sky, stars, sun, moon, sunrise/sunset.

### Game
- Entity tick culling for far, off-screen mobs (client side only).
- Entity and block entity distance culling, particle limiter, ambient block effect density.
- Dynamic lights for held items, dropped items, burning and luminous mobs.
- Adaptive quality that lowers Ferrum's budgets under sustained low FPS and restores them afterwards.

### Settings and interface
- Options screen that replaces Video Settings: General, Quality, Performance, Details, Render,
  Dynamic Lights and Advanced pages, staged changes with Undo / Apply / Done.
- Render page with per-type animation, particle and entity toggles.
- Hardware presets with automatic detection of Android, GL translation layers and desktop GPUs.
  Mobile presets also apply mobile-friendly vanilla settings (opaque leaves, no anisotropy, smaller
  weather radius). Presets never limit the frame rate.
- F3 report, `/ferrum` commands, debug HUD, Mod Menu integration.

### Compatibility
- Automatic hand-over to Sodium, VulkanMod, BadOptimizations and EntityCulling where features overlap.
- Render scale pauses while an Iris shader pack is active.
- No overlapping features with Lithium, FerriteCore, ModernFix, ImmediatelyFast or More Culling.
