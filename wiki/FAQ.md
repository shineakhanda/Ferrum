# FAQ

**Is Ferrum a replacement for Sodium?**
Ferrum and Sodium solve the same problem in different ways. Sodium replaces the terrain renderer;
Ferrum speeds up the vanilla renderer and focuses on weak and mobile hardware. You can install both:
Ferrum then turns off its terrain features and keeps the rest.

**Does it work on servers?**
Yes. Ferrum is client-side only and works on any server. Other players do not need it.

**Does it work with Iris shaders?**
Iris needs Sodium, and Ferrum works alongside both. Render scale pauses while a shader pack is active.

**Does it work on Android?**
Yes, on Minecraft 1.21.11 through launchers like Zalith, Pojav or MJ, with MobileGlues or GL4ES.
Minecraft 26.x cannot start on current Android launchers yet.

**Does it change gameplay?**
No. Entity tick culling only affects how often far, off-screen mobs are updated on your screen; the
server still runs them normally.

**Will it change my settings?**
On first launch, the preset may lower render distance, particles and a few graphics options to suit
your hardware. It never raises a setting and never limits your frame rate. Turn off **Tune Vanilla
Options** (Advanced page) to prevent this.

**Why is render scale different from lowering the resolution?**
Only the 3D world is rendered at a lower resolution; the HUD, text and menus stay sharp.

**Is Ferrum open source?**
Yes, under the MIT license.
