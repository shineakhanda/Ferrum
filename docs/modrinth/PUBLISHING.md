# Publishing checklist

## Before the first release

1. `mod_homepage`, `mod_sources` and `mod_issues` in both `gradle.properties` files point to
   https://github.com/shineakhanda/Ferrum. Change `mod_homepage` to the Modrinth page once it exists.
2. Optionally replace "Ferrum Contributors" in `src/main/resources/fabric.mod.json` and
   `mc1.21.11/src/main/resources/fabric.mod.json`, and the copyright holder in `LICENSE`.
3. Build both versions:
   ```bash
   ./gradlew build
   ./gradlew -p mc1.21.11 build
   ```
4. Test each jar once with only Fabric API installed, and once with Sodium installed.

## Modrinth project

| Field | Value |
|---|---|
| Project type | Mod |
| Environment | Client: required, Server: unsupported |
| Loaders | Fabric |
| Categories | Optimization, Utility |
| License | MIT |
| Icon | `src/main/resources/assets/ferrum/icon.png` |
| Description | `docs/modrinth/description.md` |

## Each version upload

| | 26.3 | 1.21.11 |
|---|---|---|
| File | `build/libs/ferrum-<version>+26.3.jar` | `mc1.21.11/build/libs/ferrum-<version>+1.21.11.jar` |
| Version number | `<version>+26.3` | `<version>+1.21.11` |
| Game versions | 26.3 | 1.21.11 |
| Loaders | Fabric | Fabric |
| Dependencies | Fabric API (required), Mod Menu (optional) | Fabric API (required), Mod Menu (optional) |
| Changelog | the version's section from `CHANGELOG.md` | same |

Do not upload the `-sources.jar` files as the primary file.

## GitHub release

Tag `v<version>`, title `Ferrum <version>`, body from `docs/releases/<version>.md`, and attach both jars.
