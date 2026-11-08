Ferrum makes Minecraft run better on weak hardware. It targets integrated graphics, older laptops,
and Android phones playing through Zalith, Pojav or MJ with MobileGlues or GL4ES. It speeds up the
vanilla renderer, skips work you cannot see, and gives you a complete video settings screen.

Client-side only: it works on any server, and nobody else needs it.

## What it does

**Rendering**
- **Block face culling**: faces of blocks that point away from you are never sent to the GPU.
- **Occlusion culling**: mobs, items, chests, signs and other block entities hidden behind walls or
  underground are not drawn.
- **Render scale with FSR**: draw the world at 50–100% resolution and upscale it with an FSR 1-style
  upscaler. The HUD stays sharp.
- **Render pass optimization**: fully opaque cutout faces, such as grass, use the faster solid pass.
- **Animated textures** are only updated when you can see them.
- **Lightmap cache**: lighting is only recalculated when it changes.

**Game**
- Far, off-screen mobs update less often on your side.
- Entity and block entity distance culling, particle limits.
- Dynamic lights for torches, glowing items and burning mobs.
- Adaptive quality when FPS drops.

**Settings**
- Replaces Video Settings with a full options screen: General, Quality, Performance, Details,
  Render, Dynamic Lights and Advanced.
- Render page: per-type toggles for animations, particles, item frames, armor stands, paintings,
  name tags, beacon beams and more.
- Presets that detect your hardware (Android, Intel, AMD, NVIDIA, Apple) and never limit FPS.
- Ferrum status on the F3 screen.

## Compatibility

Ferrum detects other performance mods and turns off its own overlapping features:

- **Sodium**: Ferrum's terrain features step aside; the rest keeps working.
- **Iris**: render scale pauses while a shader pack is active.
- **VulkanMod, BadOptimizations, EntityCulling**: overlapping features hand over automatically.
- **Lithium, FerriteCore, ModernFix, ImmediatelyFast, More Culling**: no overlapping features.
- **Mod Menu**: Configure button.

## Versions

- Minecraft **26.3** (Java 25)
- Minecraft **1.21.11** (Java 21). Use this one on Android.

Requires Fabric API.

## Settings

Options → Video Settings, **F6**, Mod Menu, or `/ferrum config`.
