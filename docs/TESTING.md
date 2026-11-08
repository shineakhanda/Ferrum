# Testing and verification

## Automated checks

Build both artifacts:

```bash
gradle build
gradle -p legacy build
```

Expected outputs:

- `build/libs/ferrum-1.0.0.jar`
- `legacy/build/libs/ferrum-legacy-1.21.8-1.0.0+1.21.8.jar`

The build must finish with `BUILD SUCCESSFUL`. Compile errors, missing mixin targets and refmap errors are release blockers.

## Runtime smoke test

1. Launch the 26.3 dev client (`gradle runClient`).
2. Confirm the log contains `Ferrum 1.0.0 initialized`, the hardware profile, the chosen backend and `Vulkan runtime`.
3. Confirm there are no `Mixin apply failed`, `InjectionError`, `ClassNotFoundException` or Ferrum stack traces.
4. Open a world and run `/ferrum status`. It should print hardware, backend, compatibility flags, preset, live culling counters and the config path.
5. Press `F6`; the Ferrum settings screen should open and save changes.
6. Toggle `Debug HUD`; the HUD should appear at the top-left and update.
7. Hold a torch/lantern at night with dynamic lights on; nearby terrain should receive client-side light. Drop the item; the light should move.
8. Enable an Iris shader pack; dynamic lights should pause and restore the affected sections.
9. Run `/ferrum preset potato`, `/ferrum preset auto` and `/ferrum reload`; each should complete without errors.
10. Repeat the launch smoke test in the 1.21.8 legacy dev client (`gradle -p legacy runClient`).

## In-world checks

- Entity culling: `/ferrum set entityDistance 24`, then move away from mobs; distant mobs should stop submitting. Restore with `/ferrum set entityDistance 64`.
- Block entity culling: place several chests/signs beyond 24 blocks; they should disappear only beyond the configured distance. Beacons must remain visible because off-screen block-entity renderers are exempt.
- Particle limiting: use a farm or explosion-heavy scene; the particle count should stay at or below the live cap.
- Adaptive quality: set `/ferrum set targetFps 120` and watch `tune` change to `degraded` under sustained low FPS, then recover when FPS is high.
- Text cache: open chat or the debug screen; text rendering must remain correct after resource reload.
- Mobile/GLES: launch through Pojav/Zalith with MobileGlues and confirm the profile reports `glTranslation`/`mobileGpu`, the MOBILE/POTATO preset is selected, and no desktop-GL crash occurs.
- Vulkan: on a supported discrete GPU, `/ferrum vulkan status` should report a ready runtime. `/ferrum vulkan on` writes the preference; restart and confirm the active backend is Vulkan. Unsupported drivers must report the missing capability instead of crashing.

## Regression checklist

- [ ] 26.3 jar loads with Fabric API only.
- [ ] 1.21.8 jar loads with Fabric API only.
- [ ] No mixin failures in either client.
- [ ] Dynamic lights restore when disabled, on world change and on shutdown.
- [ ] Iris shader packs pause Ferrum lights.
- [ ] Another dynamic-light mod pauses Ferrum lights.
- [ ] Renderers declaring off-screen rendering are not culled.
- [ ] Ender Dragon and Wither are not distance-culled.
- [ ] Config survives reload and invalid JSON is recovered.
- [ ] Very low memory falls back to POTATO without an OOM loop.
