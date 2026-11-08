# Building Ferrum

Two independent Gradle builds share `common/src/client/java`:

| Version | Directory | JDK | Command | Output |
|---|---|---|---|---|
| 26.3 | repo root | 25 | `./gradlew build` | `build/libs/ferrum-1.0.0+26.3.jar` |
| 1.21.11 | `mc1.21.11/` | 21+ (25 works) | `./gradlew -p mc1.21.11 build` or `cd mc1.21.11 && ./gradlew build` | `mc1.21.11/build/libs/ferrum-1.0.0+1.21.11.jar` |

On Windows use `gradlew.bat`. The first build downloads Minecraft, mappings and Fabric API.

## Layout

```
common/src/client/java/   config, presets, runtime state, hardware profile (no Minecraft imports)
src/                      26.3 code: mixins, UI, hardware detection, benchmark
mc1.21.11/src/            1.21.11 code: same features ported to 1.21.11 APIs
tools/make-icon.ps1       regenerates the icon in both builds
wiki/                     user documentation (GitHub wiki pages)
```

Code in `common/` must not import `net.minecraft` or `com.mojang` classes, since names differ between the two versions.

## Running in development

```bash
./gradlew runClient                 # 26.3
./gradlew -p mc1.21.11 runClient    # 1.21.11
```

26.3 benchmark run (opens a world, measures, writes `run/ferrum-bench/*.txt`, quits):

```bash
./gradlew runClient -Pbench=MyWorld -PbenchLabel=test
```
