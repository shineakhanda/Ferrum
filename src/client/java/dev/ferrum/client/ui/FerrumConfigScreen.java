package dev.ferrum.client.ui;

import dev.ferrum.client.FerrumClient;
import dev.ferrum.client.config.FerrumConfig;
import dev.ferrum.client.config.Preset;
import dev.ferrum.client.hardware.HardwareProfile;
import dev.ferrum.client.hardware.VulkanSupport;
import dev.ferrum.client.tuning.VanillaOptionsApplier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.PreferredGraphicsApi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import dev.ferrum.client.mixin.OptionsSubScreenAccessor;
import dev.ferrum.client.state.FerrumState;
import net.minecraft.network.chat.Component;

/**
 * Sodium-style options screen: page tabs on the left, option rows in the middle, a description
 * panel on the right (or a tooltip on narrow/phone screens), and Undo / Apply / Done at the bottom.
 *
 * <p>Ferrum options are edited on a pending copy and only take effect on Apply/Done. Vanilla options
 * (the "General" and "Quality" pages) use Minecraft's own widgets and apply immediately, like vanilla.
 */
public final class FerrumConfigScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int TOP = 32;
    private static final int BOTTOM_BAR = 30;

    private static final int COLOR_PANEL = 0xC0101010;
    private static final int COLOR_ROW = 0x90000000;
    private static final int COLOR_ROW_HOVER = 0xB0303030;
    private static final int COLOR_ACCENT = 0xFF8FD3FF;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_MUTED = 0xFFAAAAAA;
    private static final int COLOR_CHANGED = 0xFFFFE070;

    private static final Preset[] PRESETS = {
        Preset.AUTO, Preset.POTATO, Preset.MOBILE, Preset.LOW_END, Preset.BALANCED, Preset.QUALITY
    };
    private static final String[] GRAPHICS_MODES = {"AUTO", "DEFAULT", "OPENGL", "VULKAN"};

    private enum Page {
        GENERAL("General"),
        QUALITY("Quality"),
        PERFORMANCE("Performance"),
        DETAILS("Details"),
        RENDER("Render"),
        LIGHTS("Dynamic Lights"),
        ADVANCED("Advanced");

        final String title;

        Page(String title) {
            this.title = title;
        }
    }

    private record Row(String name, String description, AbstractWidget widget, boolean fullWidth,
                       Predicate<FerrumConfig> changed) {
    }


    private static Page lastPage = Page.GENERAL;

    /** Set while Ferrum itself opens the vanilla Video Settings screen, so it is not replaced. */
    private static boolean openingVanilla;

    /**
     * Called for every screen change: returns Ferrum's screen in place of vanilla Video Settings
     * (Sodium-style), unless the user turned the replacement off or asked for the vanilla screen.
     */
    public static Screen replaceVideoSettings(Screen screen) {
        if (openingVanilla) {
            openingVanilla = false;
            return screen;
        }
        if (screen instanceof VideoSettingsScreen video && FerrumState.replaceVideoSettings && FerrumClient.CONFIG != null) {
            return new FerrumConfigScreen(((OptionsSubScreenAccessor) video).ferrum$lastScreen());
        }
        return screen;
    }

    private final Screen parent;
    private final int oldMipmaps;
    private final int oldAnisotropy;
    private final Object oldFiltering;
    private FerrumConfig pending;
    private Page page = lastPage;
    private int scroll;

    private final List<Row> rows = new ArrayList<>();
    private Button applyButton;
    private Button undoButton;

    private int sideWidth;
    private int contentX;
    private int contentWidth;
    private int descX;
    private int descWidth;
    private int visibleRows;

    public FerrumConfigScreen() {
        this(Minecraft.getInstance().gui.screen());
    }

    public FerrumConfigScreen(Screen parent) {
        super(Component.literal("Ferrum Options"));
        this.parent = parent;
        this.pending = FerrumClient.CONFIG.copy();
        Options o = Minecraft.getInstance().options;
        this.oldMipmaps = o.mipmapLevels().get();
        this.oldAnisotropy = o.maxAnisotropyBit().get();
        this.oldFiltering = o.textureFiltering().get();
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        this.rows.clear();

        this.sideWidth = Math.max(80, Math.min(120, this.width / 5));
        this.contentX = this.sideWidth + 16;
        boolean wide = this.width >= 560;
        this.descWidth = wide ? Math.min(200, this.width / 4) : 0;
        int available = this.width - this.contentX - 8 - (wide ? this.descWidth + 8 : 0);
        this.contentWidth = Math.max(140, Math.min(340, available));
        this.descX = this.contentX + this.contentWidth + 8;
        this.visibleRows = Math.max(1, (this.height - TOP - BOTTOM_BAR - 4) / ROW_HEIGHT);

        // Page tabs
        int tabY = TOP;
        for (Page p : Page.values()) {
            Button tab = Button.builder(Component.literal(p == this.page ? "> " + p.title : p.title), b -> {
                this.page = p;
                lastPage = p;
                this.scroll = 0;
                this.rebuildWidgets();
            }).bounds(8, tabY, this.sideWidth, 20).build();
            this.addRenderableWidget(tab);
            tabY += 22;
        }

        // Option rows
        List<RowSpec> specs = this.buildPage(this.page);
        int maxScroll = Math.max(0, specs.size() - this.visibleRows);
        this.scroll = Math.max(0, Math.min(this.scroll, maxScroll));
        int y = TOP;
        for (int i = this.scroll; i < specs.size() && i < this.scroll + this.visibleRows; i++) {
            RowSpec spec = specs.get(i);
            AbstractWidget widget = spec.factory.create(this, y);
            this.addRenderableWidget(widget);
            this.rows.add(new Row(spec.name, spec.description, widget, spec.fullWidth, spec.changed));
            y += ROW_HEIGHT;
        }

        // Bottom bar
        int by = this.height - 26;
        int bw = 70;
        int right = this.width - 8;
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
            .bounds(right - bw, by, bw, 20).build());
        this.applyButton = this.addRenderableWidget(Button.builder(Component.literal("Apply"), b -> this.apply())
            .bounds(right - bw * 2 - 4, by, bw, 20).build());
        this.undoButton = this.addRenderableWidget(Button.builder(Component.literal("Undo"), b -> {
            this.pending = FerrumClient.CONFIG.copy();
            this.rebuildWidgets();
        }).bounds(right - bw * 3 - 8, by, bw, 20).build());
        this.updateDirtyButtons();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= this.contentX && mouseX <= this.contentX + this.contentWidth) {
            int before = this.scroll;
            this.scroll += scrollY > 0 ? -1 : 1;
            int total = this.buildPage(this.page).size();
            this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, total - this.visibleRows)));
            if (before != this.scroll) {
                this.rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ------------------------------------------------------------------ pages

    private List<RowSpec> buildPage(Page page) {
        List<RowSpec> list = new ArrayList<>();
        Options o = Minecraft.getInstance().options;
        switch (page) {
            case GENERAL -> {
                list.add(cycle("Ferrum Preset",
                    "Picks culling, particle and light budgets for your hardware. AUTO detects the "
                        + "device (Android/Pojav, Apple, Intel, AMD, NVIDIA, Mali/Adreno/PowerVR) and "
                        + "chooses for you. Low-end presets also lower render distance and particles on Apply.",
                    c -> indexOf(PRESETS, c.preset()), c -> PRESETS.length,
                    (c, i) -> {
                        Preset p = PRESETS[i];
                        if (p == Preset.AUTO) {
                            c.preset = Preset.AUTO.id();
                            HardwareProfile profile = FerrumClient.PROFILE;
                            c.applyPresetValues(profile == null ? Preset.BALANCED : profile.recommendedPreset());
                        } else {
                            c.setPreset(p);
                        }
                    },
                    i -> PRESETS[i] == Preset.AUTO && FerrumClient.PROFILE != null
                        ? "AUTO (" + FerrumClient.PROFILE.recommendedPreset().displayName() + ")"
                        : PRESETS[i].displayName(),
                    c -> !c.preset.equals(FerrumClient.CONFIG.preset)));
                list.add(slider("Render Scale", "Draws the 3D world at this percentage of your screen resolution and "
                        + "upscales it; the GUI and text stay sharp. The biggest FPS setting on phones and laptop iGPUs, "
                        + "which are limited by pixels, not draw calls. 100% = off.",
                    50, 100, 5, c -> c.renderScale, (c, v) -> c.renderScale = v, v -> v >= 100 ? "Off" : v + "%"));
                list.add(toggle("FSR Upscaling", "AMD FidelityFX Super Resolution 1 style upscaling for render scale: "
                        + "edge-adaptive upsampling (EASU) plus contrast-adaptive sharpening (RCAS). Much sharper than "
                        + "bilinear at 60-85% scale, for two cheap full-screen passes. Falls back to bilinear if the "
                        + "shaders fail on your GL layer.",
                    c -> c.fsr, (c, v) -> c.fsr = v));
                list.add(slider("FSR Sharpness", "RCAS sharpening strength. Lower if edges look crunchy.",
                    0, 100, 5, c -> c.fsrSharpness, (c, v) -> c.fsrSharpness = v, v -> v + "%"));
                list.add(toggle("Sharp Upscale", "Pixel-sharp (nearest) instead of smooth (bilinear) upscaling for render scale.",
                    c -> c.renderScaleSharp, (c, v) -> c.renderScaleSharp = v));
                vanilla(list, o.renderDistance(), "Render Distance",
                    "How many chunks are drawn around you. The single biggest FPS and RAM setting.");
                vanilla(list, o.simulationDistance(), "Simulation Distance",
                    "How far away entities and blocks tick. Lower helps CPU-bound phones and old laptops.");
                vanilla(list, o.framerateLimit(), "Max Framerate",
                    "Capping FPS saves battery and heat on laptops and phones.");
                vanilla(list, o.enableVsync(), "VSync", "Syncs frames to the display refresh rate.");
                vanilla(list, o.inactivityFpsLimit(), "Inactivity FPS Limit",
                    "Lowers FPS when the window is unfocused or you are AFK.");
                vanilla(list, o.gamma(), "Brightness", "Vanilla gamma.");
                vanilla(list, o.guiScale(), "GUI Scale", "Size of menus and the HUD.");
                vanilla(list, o.fullscreen(), "Fullscreen", "Exclusive or borderless fullscreen on desktop.");
                vanilla(list, o.bobView(), "View Bobbing", "Camera sway while walking.");
                vanilla(list, o.attackIndicator(), "Attack Indicator", "Attack cooldown indicator.");
                vanilla(list, o.fovEffectScale(), "FOV Effects", "Field-of-view changes from speed effects and sprinting.");
                vanilla(list, o.screenEffectScale(), "Distortion Effects", "Nausea and portal screen warping.");
                vanilla(list, o.menuBackgroundBlurriness(), "Menu Background Blur",
                    "Blur behind menus. A full-screen blur pass; 0 is cheapest on phones.");
            }
            case QUALITY -> {
                vanilla(list, o.graphicsPreset(), "Graphics", "Vanilla graphics preset.");
                vanilla(list, o.cloudStatus(), "Clouds", "Vanilla cloud mode. Ferrum can also hide clouds on the Details page.");
                vanilla(list, o.cloudRange(), "Cloud Range", "How far clouds are drawn.");
                vanilla(list, o.weatherRadius(), "Weather Radius", "How far rain and snow columns are drawn.");
                vanilla(list, o.particles(), "Particles", "Vanilla particle amount.");
                vanilla(list, o.ambientOcclusion(), "Smooth Lighting", "Soft block shadows. Costs chunk-build time.");
                vanilla(list, o.biomeBlendRadius(), "Biome Blend", "Higher values make chunk rebuilds much slower. 1x-3x is fine for weak CPUs.");
                vanilla(list, o.entityDistanceScaling(), "Entity Distance", "Vanilla entity render distance scale.");
                vanilla(list, o.entityShadows(), "Entity Shadows", "Blob shadows under mobs.");
                vanilla(list, o.mipmapLevels(), "Mipmap Levels", "Lower uses less VRAM, useful on 2-3 GB devices.");
                vanilla(list, o.maxAnisotropyBit(), "Anisotropic Filtering", "Sharper textures at grazing angles. Costs texture bandwidth; off on phones.");
                vanilla(list, o.textureFiltering(), "Texture Filtering", "How block textures are sampled.");
                vanilla(list, o.cutoutLeaves(), "Cutout Leaves", "Transparent leaves. Off is faster.");
                vanilla(list, o.improvedTransparency(), "Improved Transparency", "Correct translucency sorting. Off is faster on weak GPUs.");
                vanilla(list, o.vignette(), "Vignette", "Darkened screen edges.");
                vanilla(list, o.chunkSectionFadeInTime(), "Chunk Fade-In", "Fade time for newly loaded chunks.");
            }
            case PERFORMANCE -> {
                list.add(toggle("Entity Culling", "Skips drawing mobs and items beyond the entity distance. Bosses and the local player are never culled.",
                    c -> c.entityCulling, (c, v) -> c.entityCulling = v));
                list.add(slider("Entity Distance", "Blocks beyond which entities are skipped.",
                    16, 256, 8, c -> (int) c.entityDistance, (c, v) -> c.entityDistance = v, v -> v + " blocks"));
                list.add(toggle("Cull Players", "Also distance-cull other players. Off by default for PvP.",
                    c -> c.cullPlayers, (c, v) -> c.cullPlayers = v));
                list.add(toggle("Block Entity Culling", "Skips chests, signs, banners etc. far away. Beacon beams are never culled.",
                    c -> c.blockEntityCulling, (c, v) -> c.blockEntityCulling = v));
                list.add(slider("Block Entity Distance", "Blocks beyond which block entities are skipped.",
                    16, 256, 8, c -> (int) c.blockEntityDistance, (c, v) -> c.blockEntityDistance = v, v -> v + " blocks"));
                list.add(toggle("Particle Limiter", "Caps the particle count and drops far-away particles.",
                    c -> c.particleLimiting, (c, v) -> c.particleLimiting = v));
                list.add(slider("Max Particles", "Hard cap on live particles.",
                    100, 20000, 100, c -> c.maxParticles, (c, v) -> c.maxParticles = v, String::valueOf));
                list.add(slider("Particle Distance", "Particles spawned further than this are dropped.",
                    8, 256, 8, c -> (int) c.particleDistance, (c, v) -> c.particleDistance = v, v -> v + " blocks"));
                list.add(toggle("Keep Important Particles", "When limiting, still allow particles near you.",
                    c -> c.keepLimitedParticles, (c, v) -> c.keepLimitedParticles = v));
                list.add(toggle("Animate Only Visible Textures", "Only redraw animated block textures (fire, portals, sculk, "
                        + "prismarine, etc.) that a visible chunk actually uses. Water and lava always animate.",
                    c -> c.animateOnlyVisible, (c, v) -> c.animateOnlyVisible = v));
                list.add(toggle("Lightmap Cache", "Minecraft redraws the lightmap texture 20 times a second even when "
                        + "lighting did not change. Skips those redraws; each is a separate render pass, which is "
                        + "expensive on phone GPUs and GL translation layers.",
                    c -> c.lightmapCache, (c, v) -> c.lightmapCache = v));
                list.add(toggle("Render Pass Optimization", "Moves block faces whose texture has no transparent pixel from the "
                        + "cutout pass to the solid pass (grass block tops and sides, many modded blocks). Cutout shading can "
                        + "discard pixels, which turns off hidden-surface removal on phone GPUs. No visual change. Reload "
                        + "chunks (F3+A) after changing.",
                    c -> c.renderPassOptimization, (c, v) -> c.renderPassOptimization = v));
                list.add(toggle("Block Face Culling", "Ferrum terrain path: chunk meshes are stored grouped by face direction, "
                        + "and faces pointing away from the camera are not submitted at all (often a third to half of the "
                        + "terrain). Back faces were invisible anyway, so the image is identical. Reload chunks (F3+A) after changing.",
                    c -> c.faceCulling, (c, v) -> c.faceCulling = v));
                list.add(toggle("Occlusion Culling", "Ray-traces from the camera on a background thread and skips mobs, "
                        + "items and other entities hidden behind walls, terrain or underground. Vanilla only skips what is "
                        + "outside the view cone, so in bases, villages and caves most entities are drawn for nothing.",
                    c -> c.occlusionCulling, (c, v) -> c.occlusionCulling = v));
                list.add(toggle("Occlusion: Block Entities", "Also skip hidden chests, signs, banners, skulls, shulker boxes, "
                        + "beds etc. Large storage rooms and builds behind walls cost nothing to draw.",
                    c -> c.occlusionBlockEntities, (c, v) -> c.occlusionBlockEntities = v));
                list.add(toggle("Entity Tick Culling", "Mobs far away and off screen are updated on your side less often. "
                        + "The server still runs their real AI, so nothing changes in the world.",
                    c -> c.entityTickCulling, (c, v) -> c.entityTickCulling = v));
                list.add(slider("Ambient Block Effects", "How many random blocks per tick may show ambient effects "
                        + "(torch smoke, drips, leaf particles). Lower is cheaper on slow CPUs.",
                    10, 100, 5, c -> c.ambientParticleDensity, (c, v) -> c.ambientParticleDensity = v, v -> v + "%"));
                list.add(toggle("Text Width Cache", "Caches font width measurements. Helps on slow CPUs with busy HUDs.",
                    c -> c.textWidthCache, (c, v) -> c.textWidthCache = v));
                list.add(toggle("Adaptive Quality", "Temporarily lowers Ferrum budgets when FPS stays under target, then recovers.",
                    c -> c.adaptiveQuality, (c, v) -> c.adaptiveQuality = v));
                list.add(slider("Target FPS", "FPS the adaptive tuner tries to hold.",
                    10, 240, 5, c -> c.targetFps, (c, v) -> c.targetFps = v, v -> v + " FPS"));
                vanilla(list, o.prioritizeChunkUpdates(), "Chunk Builder", "Vanilla chunk update priority. 'Nearby' reduces holes but costs FPS.");
            }
            case DETAILS -> {
                list.add(toggle("Distance Fog", "Render-distance fog. Water, lava, blindness and darkness fog are kept.",
                    c -> c.fog, (c, v) -> c.fog = v));
                list.add(toggle("Clouds", "Skip the whole cloud pass, including the translucent (OIT) pass.",
                    c -> c.clouds, (c, v) -> c.clouds = v));
                list.add(toggle("Weather", "Rain and snow columns. Off skips building their mesh entirely.",
                    c -> c.weather, (c, v) -> c.weather = v));
                list.add(toggle("Sky", "Sky disc, void disc and End sky.",
                    c -> c.sky, (c, v) -> c.sky = v));
                list.add(toggle("Stars", "Night-time stars.", c -> c.stars, (c, v) -> c.stars = v));
                list.add(toggle("Sun", "The sun.", c -> c.sun, (c, v) -> c.sun = v));
                list.add(toggle("Moon", "The moon.", c -> c.moon, (c, v) -> c.moon = v));
                list.add(toggle("Sunrise / Sunset", "Colored sunrise and sunset glow.",
                    c -> c.sunriseSunset, (c, v) -> c.sunriseSunset = v));
            }
            case RENDER -> {
                list.add(toggle("Water Animation", "Animated water texture. Off freezes the current frame.",
                    c -> c.animWater, (c, v) -> c.animWater = v));
                list.add(toggle("Lava Animation", "Animated lava texture.", c -> c.animLava, (c, v) -> c.animLava = v));
                list.add(toggle("Fire Animation", "Fire, soul fire and campfire flames.", c -> c.animFire, (c, v) -> c.animFire = v));
                list.add(toggle("Portal Animation", "Nether portal texture.", c -> c.animPortal, (c, v) -> c.animPortal = v));
                list.add(toggle("Block Animations", "All other animated blocks: sculk, prismarine, kelp, magma, sea lanterns...",
                    c -> c.animBlocks, (c, v) -> c.animBlocks = v));
                list.add(toggle("Block Break Particles", "Debris when blocks are broken or walked on while sprinting.",
                    c -> c.particleBlockBreak, (c, v) -> c.particleBlockBreak = v));
                list.add(toggle("Rain Splash & Drips", "Rain splashes and dripping water/lava/honey.",
                    c -> c.particleRain, (c, v) -> c.particleRain = v));
                list.add(toggle("Explosion Particles", "Explosion clouds and smoke. Large TNT blasts spawn hundreds.",
                    c -> c.particleExplosions, (c, v) -> c.particleExplosions = v));
                list.add(toggle("Campfire Smoke", "Tall campfire smoke columns; each lives for many seconds.",
                    c -> c.particleCampfireSmoke, (c, v) -> c.particleCampfireSmoke = v));
                list.add(toggle("Potion Particles", "Swirls from potion effects, beacons and witches.",
                    c -> c.particlePotion, (c, v) -> c.particlePotion = v));
                list.add(toggle("Falling Leaves", "Leaf particles under leaves (cherry, pale oak, etc.).",
                    c -> c.particleLeaves, (c, v) -> c.particleLeaves = v));
                list.add(toggle("Torch Flames & Smoke", "Flame and smoke particles from torches, furnaces and candles.",
                    c -> c.particleFlames, (c, v) -> c.particleFlames = v));
                list.add(toggle("Item Frames", "Draw item frames and their items.",
                    c -> c.renderItemFrames, (c, v) -> c.renderItemFrames = v));
                list.add(toggle("Armor Stands", "Draw armor stands.", c -> c.renderArmorStands, (c, v) -> c.renderArmorStands = v));
                list.add(toggle("Paintings", "Draw paintings.", c -> c.renderPaintings, (c, v) -> c.renderPaintings = v));
                list.add(toggle("Name Tags", "Custom names over mobs and armor stands. Player names always show.",
                    c -> c.renderNameTags, (c, v) -> c.renderNameTags = v));
                list.add(toggle("Beacon Beams", "Beacon beams. They are drawn from any distance.",
                    c -> c.renderBeaconBeams, (c, v) -> c.renderBeaconBeams = v));
                list.add(toggle("Enchanting Table Book", "The floating book above enchanting tables.",
                    c -> c.renderEnchantBook, (c, v) -> c.renderEnchantBook = v));
            }
            case LIGHTS -> {
                list.add(toggle("Dynamic Lights", "Held torches, glowing items, burning and luminous mobs light up the world. "
                        + "Pauses automatically if LambDynamicLights / Sodium Dynamic Lights is installed.",
                    c -> c.dynamicLights, (c, v) -> c.dynamicLights = v));
                list.add(slider("Light Radius", "Max reach of a dynamic light. Lower is cheaper.",
                    0, 15, 1, c -> c.dynamicLightRadius, (c, v) -> c.dynamicLightRadius = v, v -> v + " blocks"));
                list.add(slider("Max Light Sources", "Only the closest N sources are lit.",
                    1, 64, 1, c -> c.dynamicLightMaxSources, (c, v) -> c.dynamicLightMaxSources = v, String::valueOf));
                list.add(toggle("Entity Lights", "Burning mobs, blazes, glow squids, etc.",
                    c -> c.dynamicLightEntities, (c, v) -> c.dynamicLightEntities = v));
                list.add(toggle("Dropped Item Lights", "Dropped torches, lava buckets, glowstone, etc.",
                    c -> c.dynamicLightItems, (c, v) -> c.dynamicLightItems = v));
            }
            case ADVANCED -> {
                list.add(toggle("Replace Video Settings", "Open this screen instead of vanilla Video Settings, like Sodium.",
                    c -> c.replaceVideoSettings, (c, v) -> c.replaceVideoSettings = v));
                list.add(new RowSpec("Vanilla Video Settings", "Open Minecraft's own Video Settings screen.", false, (s, y) ->
                    Button.builder(Component.literal("Open..."), b -> {
                        Minecraft mc = Minecraft.getInstance();
                        openingVanilla = true;
                        mc.gui.setScreen(new VideoSettingsScreen(s, mc, mc.options));
                    }).bounds(s.controlX(), y + 1, s.controlWidth(), 20).build(), c -> false));
                list.add(cycle("Graphics API",
                    "AUTO lets Ferrum pick: Vulkan on capable desktop GPUs, OpenGL on Android (MobileGlues, "
                        + "GL4ES, VirGL/Zink, ANGLE), Apple and old Intel iGPUs. Needs a restart. If a Vulkan start "
                        + "fails, Minecraft and Ferrum fall back to OpenGL.",
                    c -> indexOf(GRAPHICS_MODES, c.vulkanMode), c -> GRAPHICS_MODES.length,
                    (c, i) -> c.vulkanMode = GRAPHICS_MODES[i],
                    i -> GRAPHICS_MODES[i],
                    c -> !c.vulkanMode.equals(FerrumClient.CONFIG.vulkanMode)));
                list.add(slider("FPS Cap", "Ferrum's own FPS cap. 0 = no cap.",
                    0, 240, 5, c -> c.fpsCap, (c, v) -> c.fpsCap = v, v -> v == 0 ? "Off" : v + " FPS"));
                list.add(toggle("Force Terrain Multi-Draw", "EXPERIMENTAL. Use Minecraft's batched terrain draw path on GPUs "
                        + "where Mojang disabled it (all non-Arc Intel). Renders correctly on Intel UHD 615 but was not "
                        + "faster there; may help other drivers. Turn off if you see flickering or missing chunks.",
                    c -> c.forceMultiDraw, (c, v) -> c.forceMultiDraw = v));
                list.add(toggle("Iris Safe Mode", "Pause dynamic lights while an Iris shader pack is active.",
                    c -> c.irisSafeMode, (c, v) -> c.irisSafeMode = v));
                list.add(toggle("Tune Vanilla Options", "Let presets lower render distance, particles and clouds (never caps FPS).",
                    c -> c.autoApplyVanillaOptions, (c, v) -> c.autoApplyVanillaOptions = v));
                list.add(toggle("Debug HUD", "FPS, frame time, cull counts, particles and GPU in the top-left corner.",
                    c -> c.debugHud, (c, v) -> c.debugHud = v));
            }
        }
        return list;
    }

    // ------------------------------------------------------------------ row factories

    @FunctionalInterface
    private interface WidgetFactory {
        AbstractWidget create(FerrumConfigScreen screen, int y);
    }

    @FunctionalInterface
    private interface BoolSetter {
        void set(FerrumConfig config, boolean value);
    }

    @FunctionalInterface
    private interface IntSetter {
        void set(FerrumConfig config, int value);
    }

    private record RowSpec(String name, String description, boolean fullWidth, WidgetFactory factory,
                           Predicate<FerrumConfig> changed) {
    }

    private int controlWidth() {
        return Math.max(70, Math.min(120, this.contentWidth / 2 - 4));
    }

    private int controlX() {
        return this.contentX + this.contentWidth - this.controlWidth() - 2;
    }

    private RowSpec toggle(String name, String description, Predicate<FerrumConfig> getter, BoolSetter setter) {
        return new RowSpec(name, description, false, (s, y) -> {
            Button[] holder = new Button[1];
            holder[0] = Button.builder(onOff(getter.test(s.pending)), b -> {
                setter.set(s.pending, !getter.test(s.pending));
                b.setMessage(onOff(getter.test(s.pending)));
                s.updateDirtyButtons();
            }).bounds(s.controlX(), y + 1, s.controlWidth(), 20).build();
            return holder[0];
        }, c -> getter.test(c) != getter.test(FerrumClient.CONFIG));
    }

    private RowSpec cycle(String name, String description, ToIntFunction<FerrumConfig> index,
                          ToIntFunction<FerrumConfig> count, CycleSetter setter, IntFunction<String> label,
                          Predicate<FerrumConfig> changed) {
        return new RowSpec(name, description, false, (s, y) ->
            Button.builder(Component.literal(label.apply(Math.max(0, index.applyAsInt(s.pending)))), b -> {
                int next = (Math.max(0, index.applyAsInt(s.pending)) + 1) % count.applyAsInt(s.pending);
                setter.set(s.pending, next);
                b.setMessage(Component.literal(label.apply(next)));
                s.updateDirtyButtons();
                // Presets change many values at once; refresh the other rows.
                s.rebuildWidgets();
            }).bounds(s.controlX(), y + 1, s.controlWidth(), 20).build(), changed);
    }

    @FunctionalInterface
    private interface CycleSetter {
        void set(FerrumConfig config, int index);
    }

    private RowSpec slider(String name, String description, int min, int max, int step,
                           ToIntFunction<FerrumConfig> getter, IntSetter setter, IntFunction<String> format) {
        return new RowSpec(name, description, false, (s, y) ->
            new IntSlider(s.controlX(), y + 1, s.controlWidth(), min, max, step,
                () -> getter.applyAsInt(s.pending),
                v -> {
                    setter.set(s.pending, v);
                    s.updateDirtyButtons();
                }, format),
            c -> getter.applyAsInt(c) != getter.applyAsInt(FerrumClient.CONFIG));
    }

    private void vanilla(List<RowSpec> list, OptionInstance<?> option, String name, String description) {
        if (option == null) {
            return;
        }
        list.add(new RowSpec(name, description + " (vanilla option, applies immediately)", true, (s, y) ->
            option.createButton(Minecraft.getInstance().options, s.contentX + 2, y + 1, s.contentWidth - 4),
            c -> false));
    }

    private static final class IntSlider extends AbstractSliderButton {
        private final int min;
        private final int max;
        private final int step;
        private final Consumer<Integer> setter;
        private final IntFunction<String> format;

        IntSlider(int x, int y, int width, int min, int max, int step,
                  java.util.function.IntSupplier getter, Consumer<Integer> setter, IntFunction<String> format) {
            super(x, y, width, 20, Component.empty(), toSlider(getter.getAsInt(), min, max));
            this.min = min;
            this.max = max;
            this.step = step;
            this.setter = setter;
            this.format = format;
            this.updateMessage();
        }

        private static double toSlider(int value, int min, int max) {
            return max == min ? 0.0 : (Math.max(min, Math.min(max, value)) - min) / (double) (max - min);
        }

        private int current() {
            int raw = (int) Math.round(this.min + this.value * (this.max - this.min));
            int snapped = this.min + Math.round((raw - this.min) / (float) this.step) * this.step;
            return Math.max(this.min, Math.min(this.max, snapped));
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(this.format.apply(this.current())));
        }

        @Override
        protected void applyValue() {
            this.setter.accept(this.current());
        }
    }

    // ------------------------------------------------------------------ apply / dirty

    private boolean isDirty() {
        return !this.pending.toJson().equals(FerrumClient.CONFIG.toJson());
    }

    private void updateDirtyButtons() {
        boolean dirty = this.isDirty();
        if (this.applyButton != null) {
            this.applyButton.active = dirty;
        }
        if (this.undoButton != null) {
            this.undoButton.active = dirty;
        }
    }

    private void apply() {
        FerrumConfig old = FerrumClient.CONFIG;
        FerrumConfig next = this.pending.copy();
        next.configPath = old.configPath;
        next.sanitize();

        boolean presetChanged = !next.preset.equals(old.preset);
        boolean apiChanged = !next.vulkanMode.equals(old.vulkanMode);

        FerrumClient.CONFIG = next;
        if (apiChanged) {
            next.vulkanApplied = next.vulkanMode.equals("VULKAN");
            if (!next.vulkanMode.equals("AUTO")) {
                VulkanSupport.apply(PreferredGraphicsApi.valueOf(next.vulkanMode));
            }
        }
        FerrumClient.applyRuntimeConfig();

        if (presetChanged) {
            Preset resolved = next.preset() == Preset.AUTO
                ? (FerrumClient.PROFILE == null ? Preset.BALANCED : FerrumClient.PROFILE.recommendedPreset())
                : next.preset();
            VanillaOptionsApplier.apply(Minecraft.getInstance(), FerrumClient.PROFILE, resolved);
        }

        this.pending = next.copy();
        this.rebuildWidgets();
    }

    @Override
    public void onClose() {
        if (this.isDirty()) {
            this.apply();
        }
        Minecraft.getInstance().options.save();
        this.applyTextureAndWindowChanges();
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    /** What vanilla Video Settings does when it closes: reload textures for mipmap/filtering changes, apply fullscreen. */
    private void applyTextureAndWindowChanges() {
        Minecraft mc = Minecraft.getInstance();
        Options o = mc.options;
        if (o.mipmapLevels().get() != this.oldMipmaps || o.maxAnisotropyBit().get() != this.oldAnisotropy
                || o.textureFiltering().get() != this.oldFiltering) {
            mc.updateMaxMipLevel(o.mipmapLevels().get());
            mc.delayTextureReload();
        }
        mc.getWindow().changeFullscreenVideoMode();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        // Sidebar and content panels
        graphics.fill(4, TOP - 4, this.sideWidth + 12, TOP + Page.values().length * 22 + 2, COLOR_PANEL);
        graphics.fill(this.contentX - 4, TOP - 4, this.contentX + this.contentWidth + 4,
            this.height - BOTTOM_BAR, COLOR_PANEL);

        for (Row row : this.rows) {
            int y = row.widget.getY() - 1;
            boolean hovered = mouseX >= this.contentX && mouseX < this.contentX + this.contentWidth
                && mouseY >= y && mouseY < y + ROW_HEIGHT;
            graphics.fill(this.contentX, y, this.contentX + this.contentWidth, y + ROW_HEIGHT - 1,
                hovered ? COLOR_ROW_HOVER : COLOR_ROW);
            if (!row.fullWidth) {
                boolean changed = row.changed.test(this.pending);
                graphics.text(this.font, (changed ? "* " : "") + row.name, this.contentX + 6, y + 7,
                    changed ? COLOR_CHANGED : COLOR_TEXT, true);
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.text(this.font, "Ferrum " + FerrumClient.VERSION, 8, 12, COLOR_ACCENT, true);
        graphics.text(this.font, this.page.title, this.contentX, 12, COLOR_TEXT, true);

        int total = this.buildPageCount();
        if (total > this.visibleRows) {
            String s = (this.scroll + 1) + "-" + Math.min(total, this.scroll + this.visibleRows) + " / " + total + "  (scroll)";
            graphics.text(this.font, s, this.contentX + this.contentWidth - this.font.width(s), 12, COLOR_MUTED, true);
        }

        Row hovered = null;
        for (Row row : this.rows) {
            int y = row.widget.getY() - 1;
            if (mouseX >= this.contentX && mouseX < this.contentX + this.contentWidth
                && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                hovered = row;
                break;
            }
        }

        if (this.descWidth > 0) {
            int x = this.descX;
            graphics.fill(x - 4, TOP - 4, x + this.descWidth, this.height - BOTTOM_BAR, COLOR_PANEL);
            if (hovered != null) {
                graphics.text(this.font, hovered.name, x + 2, TOP + 2, COLOR_ACCENT, true);
                graphics.textWithWordWrap(this.font, Component.literal(hovered.description),
                    x + 2, TOP + 16, this.descWidth - 8, COLOR_MUTED);
            } else {
                this.drawHardware(graphics, x + 2, TOP + 2, this.descWidth - 8);
            }
        } else if (hovered != null) {
            graphics.setTooltipForNextFrame(this.font, Component.literal(hovered.description), mouseX, mouseY);
        }

        if (this.isDirty()) {
            graphics.text(this.font, "Unsaved changes", 8, this.height - 20, COLOR_CHANGED, true);
        }
    }

    private int buildPageCount() {
        return this.buildPage(this.page).size();
    }

    private void drawHardware(GuiGraphicsExtractor graphics, int x, int y, int width) {
        HardwareProfile profile = FerrumClient.PROFILE;
        graphics.text(this.font, "This device", x, y, COLOR_ACCENT, true);
        if (profile == null) {
            graphics.text(this.font, "Detecting...", x, y + 14, COLOR_MUTED, true);
            return;
        }
        String info = "GPU: " + profile.gpuName()
            + "\nBackend: " + profile.backend() + " (" + VulkanSupport.activeBackend() + ")"
            + "\nRecommended: " + profile.recommendedPreset().displayName()
            + (profile.android() ? "\nAndroid launcher" : "")
            + (profile.glTranslationLayer() ? "\nGL translation layer (MobileGlues / GL4ES / VirGL / Zink / ANGLE)" : "")
            + (profile.softwareGpu() ? "\nSoftware renderer" : "")
            + "\n\nHover an option to see what it does.";
        graphics.textWithWordWrap(this.font, Component.literal(info), x, y + 14, width, COLOR_MUTED);
    }

    // ------------------------------------------------------------------ helpers

    private static Component onOff(boolean value) {
        return Component.literal(value ? "On" : "Off");
    }

    private static <T> int indexOf(T[] values, T value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                return i;
            }
        }
        return 0;
    }

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equalsIgnoreCase(value)) {
                return i;
            }
        }
        return 0;
    }
}
