# Contributing to Ferrum

Thanks for helping. Bug reports, compatibility reports from real devices, and code are all welcome.

## Reporting bugs

Open an issue with:

1. Minecraft version, Ferrum version and Fabric Loader / Fabric API versions.
2. Your device: CPU, GPU, RAM. On Android also the launcher and GL layer (MobileGlues, GL4ES, Zink...).
3. The full `latest.log` (and the crash report, if there is one). Please upload it to
   [mclo.gs](https://mclo.gs) rather than pasting it.
4. A screenshot of the F3 screen if the game starts. The "Ferrum Renderer" block on the right shows
   the active features and the mods Ferrum detected.
5. Your other mods. Try once with only Fabric API and Ferrum before reporting.

Rendering glitches: say which setting makes them go away (Performance and Render pages), and press
F3+A after changing terrain settings so chunks rebuild.

## Performance reports

FPS numbers are only comparable with the same world, position, render distance and settings.
Please include: FPS with Ferrum off (remove the jar) and on, the preset, render scale, and whether
Sodium or other performance mods were installed.

## Development setup

- JDK 25 (needed for the 26.3 build; also builds 1.21.11)
- Any IDE with Gradle support (IntelliJ IDEA recommended)

```bash
./gradlew build                 # 26.3
./gradlew -p mc1.21.11 build    # 1.21.11
./gradlew runClient             # start 26.3 in development
./gradlew -p mc1.21.11 runClient
```

## Project layout

| Path | Contents |
|---|---|
| `common/src/client/java` | Code shared by both versions. **No Minecraft or Mojang imports** (config, presets, state, culling engine, face sorter, compatibility rules) |
| `src/` | Minecraft 26.3 build: mixins, UI, version-specific adapters |
| `mc1.21.11/src/` | Minecraft 1.21.11 build: the same features against the 1.21.11 API |
| `docs/` | Technical documentation |
| `wiki/` | User documentation (mirrors the GitHub wiki) |

A feature normally needs a change in `common/` plus one adapter or mixin in each version directory.
If you add a feature to one version only, say so in the pull request.

## Code guidelines

- Write your own code. Do not copy code from Sodium, Embeddium or other mods with incompatible
  licenses; studying how they work is fine.
- Hot paths (anything called per frame, per entity or per block) must not allocate, use reflection,
  touch the config file or take locks.
- Every user-visible feature needs a config key, a row in the settings screen with a description,
  and an entry in `/ferrum set` tab completion.
- Settings must only make things cheaper by default; never raise a user's own setting.
- If a feature overlaps with another popular mod, add a rule to `CompatRules` so only one runs.
- Mixins: an `@Inject` handler takes either all target arguments or none, and every target must exist
  in the version you build against (`defaultRequire` is 1).
- Keep comments to explaining *why* something is done; the code says *what*.

## Pull requests

1. One topic per pull request.
2. Build both versions before opening it.
3. Describe what you tested and on which hardware.
4. Update `CHANGELOG.md` under "Unreleased", and the docs or wiki if behavior changed.

By contributing you agree that your contribution is licensed under the MIT license.
