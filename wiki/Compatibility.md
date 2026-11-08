# Compatibility

Ferrum turns off its own features when another installed mod already does the same job. You do not
need to change anything: the F3 screen ("Compat" line) and `/ferrum status` show what was handed over.

| Mod | What Ferrum does |
|---|---|
| Sodium | Turns off its terrain features and Video Settings replacement. Occlusion culling, render scale, particles, entity features and dynamic lights keep working. Use F6 or Mod Menu for Ferrum's settings. |
| Iris | Pauses render scale and dynamic lights while a shader pack is active. |
| VulkanMod | Turns off terrain features, render scale and the lightmap cache. |
| BadOptimizations | Turns off its lightmap cache. |
| EntityCulling | Turns off occlusion culling and entity tick culling. |
| LambDynamicLights / Sodium Dynamic Lights | Pauses its dynamic lights. |
| Lithium, FerriteCore, ModernFix, ImmediatelyFast, More Culling, C2ME, Krypton, Dynamic FPS | No overlapping features. |
| Mod Menu | Adds a Configure button. |

Hardware and Android GL layer details: [docs/COMPATIBILITY.md](https://github.com/shineakhanda/Ferrum/blob/HEAD/docs/COMPATIBILITY.md).

Found a conflict? Open an issue with both mods' versions and your `latest.log`.
