# Troubleshooting

## The game crashes at startup

- Check that the Ferrum file matches your Minecraft version (`+26.3` or `+1.21.11`) and that Fabric
  API for the same version is installed.
- 26.3 needs Java 25; 1.21.11 needs Java 21 or newer.
- On Android, 26.x crashes with "Loading library SDL": the launcher lacks SDL3. Use 1.21.11.
- Otherwise open an issue with the crash report.

## Some block faces are missing or flicker

1. Turn off **Block Face Culling** (Performance page), press **F3 + A**. If that fixes it, please
   report it with a screenshot, your coordinates and the block(s) involved.
2. Do the same with **Render Pass Optimization**.
3. With a resource pack: try without it.

## Mobs or chests pop in late when walking around a corner

Occlusion culling updates about 50 times per second, so pop-in should be at most a frame or two.
If it is worse on a slow CPU, turn off **Occlusion Culling** or **Occlusion: Block Entities**.

## The world looks blurry

Render scale is below 100%. Raise it, keep **FSR Upscaling** on, or raise **FSR Sharpness**.

## Black screen or broken image with render scale

Set render scale to 100% and report your GPU, driver and GL layer. On Android, try another GL layer.
If the FSR shaders fail, Ferrum switches to bilinear by itself and logs
`Ferrum FSR failed, using bilinear upscaling`.

## FPS did not improve

- Check the F3 screen: "Path" should show your device correctly (Android / GL translation).
- Check the preset. On a weak device, Auto should pick Low-End, Mobile or Potato.
- Check the **Compat** line on the F3 screen: with Sodium installed, Ferrum's terrain features are off.
- Make sure Max Framerate is not limiting you, and VSync is off if you want to measure.

## Where is the config?

`config/ferrum.json`. Delete it to reset all Ferrum settings.
