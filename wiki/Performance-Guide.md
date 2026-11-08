# Performance Guide

Start with the preset Ferrum picks (Auto), then change one thing at a time and watch the FPS on the
F3 screen.

## Is your device limited by the GPU or the CPU?

- Lower **Render Scale** to 70%. If FPS rises a lot, the GPU is the limit: keep render scale low
  (FSR keeps it sharp) and turn off clouds and weather on the Details page.
- If FPS barely changes, the CPU is the limit: lower **Render Distance** and **Simulation
  Distance**, keep **Entity Tick Culling** and **Occlusion Culling** on, and lower particles.

## Starting points

| Device | Preset | Render scale | Render distance |
|---|---|---|---|
| Intel HD / UHD laptop | Low-End | 75–85% | 8–10 |
| AMD Vega APU | Low-End or Balanced | 85–100% | 10–12 |
| Snapdragon 8 Gen 1+ / 7+ Gen 2 | Mobile | 75–85% | 8 |
| Snapdragon 6xx/7xx, Dimensity 7000–8000 | Mobile | 65–75% | 6–8 |
| Exynos (Xclipse) | Mobile | 65–75% | 6–8 |
| Helio G / Dimensity 6000, Unisoc | Potato | 55–65% | 5–6 |

## Android memory (JVM arguments)

Set these in the launcher. Leave at least 1–1.5 GB of the phone's RAM free so Android does not
close the game.

| Device RAM | Arguments |
|---|---|
| 3 GB | `-Xms1G -Xmx1536M -XX:+UseG1GC -XX:MaxGCPauseMillis=50` |
| 4 GB | `-Xms1536M -Xmx2G -XX:+UseG1GC -XX:MaxGCPauseMillis=50` |
| 6 GB | `-Xms2G -Xmx3G -XX:+UseG1GC -XX:MaxGCPauseMillis=50` |
| 8 GB+ | `-Xms2G -Xmx4G -XX:+UseG1GC -XX:MaxGCPauseMillis=40` |

A larger heap is not faster: on slow CPUs it means longer garbage collection pauses.

## Settings that cost the most on phones

- Cutout (transparent) leaves: turn off. Opaque leaves let the GPU skip hidden pixels.
- Improved Transparency: off.
- Clouds: off, or Fast.
- Menu background blur: 0.
- Anisotropic filtering: off.

The Mobile and Potato presets set these for you.

## Other mods worth adding

Lithium (game logic), FerriteCore (memory) and ModernFix (loading and memory) work alongside
Ferrum. See [[Compatibility]].
