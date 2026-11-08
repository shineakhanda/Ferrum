package dev.ferrum.client.bench;

import dev.ferrum.client.FerrumClient;
import dev.ferrum.client.compat.ModCompat;
import dev.ferrum.client.hardware.HardwareProfile;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Repeatable FPS benchmark so every optimization can be measured instead of guessed.
 *
 * <p>Automatic mode: start the client with {@code -Dferrum.benchmark=<world folder>} (Gradle:
 * {@code ./gradlew runClient -Pbench=FerrumTest}). Ferrum opens the world from the title screen, waits
 * for chunks to load, slowly turns the camera 360 degrees while recording every frame, writes a report
 * to {@code ferrum-bench/} and quits. In-game: {@code /ferrum bench [seconds]}.
 */
public final class Benchmark implements HudElement {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("ferrum", "benchmark");
    public static final Benchmark INSTANCE = new Benchmark();

    private static final String AUTO_WORLD = System.getProperty("ferrum.benchmark");
    private static final int WARMUP_SECONDS = Integer.getInteger("ferrum.benchmark.warmup", 25);
    private static final int AUTO_SECONDS = Integer.getInteger("ferrum.benchmark.seconds", 60);
    private static final String LABEL = System.getProperty("ferrum.benchmark.label", "");

    private enum Phase { IDLE, OPENING, WARMUP, RUNNING, QUITTING }

    private int quitTicks;

    private Phase phase = Phase.IDLE;
    private boolean auto;
    private long phaseStartNs;
    private int durationSeconds;
    private float startYaw;

    private long[] frames = new long[1 << 16];
    private int frameCount;
    private long lastFrameNs;
    private long heapMax;
    private long heapSum;
    private int heapSamples;
    private long gcCountStart;
    private long gcTimeStart;
    private int savedFpsLimit = -1;
    private Boolean savedVsync;
    private int cleanTicks;
    private boolean interrupted;

    private Benchmark() {
    }

    public static boolean autoRequested() {
        return AUTO_WORLD != null && !AUTO_WORLD.isBlank();
    }

    /** Called every client tick from {@link FerrumClient}. */
    public void tick(Minecraft mc) {
        if (this.phase == Phase.IDLE && this.auto == false && autoRequested() && mc.gui.screen() instanceof TitleScreen) {
            this.auto = true;
            this.phase = Phase.OPENING;
            FerrumClient.LOGGER.info("Ferrum benchmark: opening world '{}'", AUTO_WORLD);
            mc.createWorldOpenFlows().openWorld(AUTO_WORLD, () -> {
                FerrumClient.LOGGER.error("Ferrum benchmark: could not open world '{}'", AUTO_WORLD);
                mc.stop();
            });
            return;
        }

        switch (this.phase) {
            case OPENING -> {
                // The loading screen can flicker away for a tick; require 2s of a clean in-world view.
                if (inWorldView(mc)) {
                    if (++this.cleanTicks >= 40) {
                        this.startWarmup(mc, AUTO_SECONDS);
                    }
                } else {
                    this.cleanTicks = 0;
                }
            }
            case WARMUP -> {
                if (!inWorldView(mc)) {
                    // A screen came back (loading, pause...). Restart the warmup clock.
                    this.phaseStartNs = System.nanoTime();
                    return;
                }
                boolean chunksReady = mc.levelRenderer.hasRenderedAllSections();
                if (elapsedSeconds() >= WARMUP_SECONDS && (chunksReady || elapsedSeconds() >= WARMUP_SECONDS * 3)) {
                    this.startRunning(mc);
                }
            }
            case RUNNING -> {
                if (mc.player == null) {
                    this.phase = Phase.IDLE;
                    return;
                }
                if (!inWorldView(mc)) {
                    this.interrupted = true;
                }
                double progress = elapsedSeconds() / this.durationSeconds;
                mc.player.setYRot(this.startYaw + (float) (progress * 360.0));
                mc.player.setXRot(0.0F);
                Runtime rt = Runtime.getRuntime();
                long used = rt.totalMemory() - rt.freeMemory();
                this.heapMax = Math.max(this.heapMax, used);
                this.heapSum += used;
                this.heapSamples++;
                if (progress >= 1.0) {
                    this.finish(mc);
                }
            }
            case QUITTING -> {
                if (--this.quitTicks <= 0) {
                    mc.stop();
                }
            }
            default -> {
            }
        }
    }

    /** In-game start from the command. */
    public void start(Minecraft mc, int seconds) {
        if (this.phase != Phase.IDLE || mc.player == null) {
            return;
        }
        this.startWarmup(mc, seconds);
    }

    public boolean running() {
        return this.phase != Phase.IDLE;
    }

    private void startWarmup(Minecraft mc, int seconds) {
        this.durationSeconds = Math.max(5, seconds);
        this.phase = Phase.WARMUP;
        this.phaseStartNs = System.nanoTime();
        // Measure the real frame rate, not the cap.
        this.savedFpsLimit = mc.options.framerateLimit().get();
        this.savedVsync = mc.options.enableVsync().get();
        mc.options.framerateLimit().set(260);
        mc.options.enableVsync().set(false);
        message(mc, "Ferrum benchmark: warming up " + (this.auto ? WARMUP_SECONDS : 5) + "s, then "
            + this.durationSeconds + "s run. Don't touch the mouse.");
        if (!this.auto) {
            // Skip most of the warmup for in-game runs; chunks are already loaded.
            this.phaseStartNs -= (long) Math.max(0, WARMUP_SECONDS - 5) * 1_000_000_000L;
        }
    }

    private void startRunning(Minecraft mc) {
        this.phase = Phase.RUNNING;
        this.phaseStartNs = System.nanoTime();
        this.frameCount = 0;
        this.lastFrameNs = 0;
        this.heapMax = 0;
        this.heapSum = 0;
        this.heapSamples = 0;
        this.startYaw = mc.player.getYRot();
        this.interrupted = false;
        this.gcCountStart = gcCount();
        this.gcTimeStart = gcTime();
    }

    private static boolean inWorldView(Minecraft mc) {
        return mc.level != null && mc.player != null && mc.gui.screen() == null && mc.gui.overlay() == null;
    }

    private double elapsedSeconds() {
        return (System.nanoTime() - this.phaseStartNs) / 1.0E9;
    }

    /** Called once per rendered frame (HUD pass). */
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (this.phase != Phase.RUNNING) {
            return;
        }
        long now = System.nanoTime();
        if (this.lastFrameNs != 0) {
            if (this.frameCount == this.frames.length) {
                this.frames = Arrays.copyOf(this.frames, this.frames.length * 2);
            }
            this.frames[this.frameCount++] = now - this.lastFrameNs;
        }
        this.lastFrameNs = now;
        Minecraft mc = Minecraft.getInstance();
        graphics.text(mc.font, "Ferrum benchmark " + (int) elapsedSeconds() + "/" + this.durationSeconds + "s",
            4, mc.getWindow().getGuiScaledHeight() - 12, 0xFFFFE070, true);
    }

    private void finish(Minecraft mc) {
        this.phase = Phase.IDLE;
        if (this.savedFpsLimit >= 0) {
            mc.options.framerateLimit().set(this.savedFpsLimit);
        }
        if (this.savedVsync != null) {
            mc.options.enableVsync().set(this.savedVsync);
        }

        String report = this.buildReport(mc);
        FerrumClient.LOGGER.info("Ferrum benchmark result:\n{}", report);
        Path dir = FabricLoader.getInstance().getGameDir().resolve("ferrum-bench");
        try {
            Files.createDirectories(dir);
            String name = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + (LABEL.isBlank() ? "" : "-" + LABEL.replaceAll("[^A-Za-z0-9_.-]", "_")) + ".txt";
            Files.writeString(dir.resolve(name), report, StandardCharsets.UTF_8);
            message(mc, "Ferrum benchmark saved to ferrum-bench/" + name);
        } catch (IOException e) {
            FerrumClient.LOGGER.error("Could not write benchmark report", e);
        }
        String first = report.lines().filter(l -> l.startsWith("Average FPS")).findFirst().orElse("");
        message(mc, first);

        if (this.auto) {
            // Screenshot so rendering changes can be checked for correctness, not just speed.
            String shot = "ferrum-bench-" + (LABEL.isBlank() ? "run" : LABEL.replaceAll("[^A-Za-z0-9_.-]", "_")) + ".png";
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, shot, mc.gameRenderer.mainRenderTarget(), 1,
                c -> FerrumClient.LOGGER.info("Ferrum benchmark screenshot: {}", c.getString()));
            if (!Boolean.getBoolean("ferrum.benchmark.stay")) {
                this.phase = Phase.QUITTING;
                this.quitTicks = 60;
            }
        }
    }

    private String buildReport(Minecraft mc) {
        long[] sorted = Arrays.copyOf(this.frames, this.frameCount);
        Arrays.sort(sorted);
        long total = 0;
        for (long f : sorted) {
            total += f;
        }
        int n = Math.max(1, sorted.length);
        double avgMs = total / 1.0E6 / n;
        double p99 = sorted.length == 0 ? 0 : sorted[Math.min(n - 1, (int) (n * 0.99))] / 1.0E6;
        double p999 = sorted.length == 0 ? 0 : sorted[Math.min(n - 1, (int) (n * 0.999))] / 1.0E6;
        double max = sorted.length == 0 ? 0 : sorted[n - 1] / 1.0E6;
        HardwareProfile profile = FerrumClient.PROFILE;

        StringBuilder sb = new StringBuilder();
        sb.append("Ferrum benchmark ").append(LocalDateTime.now()).append('\n');
        if (this.interrupted) {
            sb.append("WARNING: a screen or loading overlay was open during the run; result is not valid").append('\n');
        }
        if (!LABEL.isBlank()) {
            sb.append("Label: ").append(LABEL).append('\n');
        }
        sb.append(String.format(Locale.ROOT, "Average FPS: %.1f   (avg frame %.2f ms)%n", 1000.0 / avgMs, avgMs));
        sb.append(String.format(Locale.ROOT, "1%% low FPS: %.1f   (p99 frame %.2f ms)%n", 1000.0 / Math.max(0.001, p99), p99));
        sb.append(String.format(Locale.ROOT, "0.1%% low FPS: %.1f   (p99.9 frame %.2f ms)%n", 1000.0 / Math.max(0.001, p999), p999));
        sb.append(String.format(Locale.ROOT, "Worst frame: %.1f ms%n", max));
        sb.append("Frames: ").append(this.frameCount).append(" over ").append(this.durationSeconds).append("s\n");
        sb.append(String.format(Locale.ROOT, "Heap used: avg %d MB, peak %d MB, max %d MB%n",
            this.heapSamples == 0 ? 0 : this.heapSum / this.heapSamples / 1048576L,
            this.heapMax / 1048576L, Runtime.getRuntime().maxMemory() / 1048576L));
        sb.append("GC: ").append(gcCount() - this.gcCountStart).append(" collections, ")
            .append(gcTime() - this.gcTimeStart).append(" ms\n");
        sb.append("Render distance: ").append(mc.options.renderDistance().get())
            .append(", simulation distance: ").append(mc.options.simulationDistance().get()).append('\n');
        sb.append("Ferrum preset: ").append(FerrumClient.presetDisplayName()).append('\n');
        sb.append("Terrain multi-draw: ").append(dev.ferrum.client.state.FerrumState.multiDrawActive ? "on" : "off")
            .append(dev.ferrum.client.state.FerrumState.multiDrawVanilla ? " (vanilla)" : dev.ferrum.client.state.FerrumState.forceMultiDraw ? " (forced by Ferrum)" : "")
            .append(", device supports: ").append(dev.ferrum.client.state.FerrumState.multiDrawSupported).append("\n");
        sb.append("Sodium: ").append(ModCompat.SODIUM).append(", Iris: ").append(ModCompat.IRIS).append('\n');
        if (profile != null) {
            sb.append("Hardware: ").append(profile.shortSummary()).append('\n');
        }
        return sb.toString();
    }

    private static long gcCount() {
        long sum = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            sum += Math.max(0, bean.getCollectionCount());
        }
        return sum;
    }

    private static long gcTime() {
        long sum = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            sum += Math.max(0, bean.getCollectionTime());
        }
        return sum;
    }

    private static void message(Minecraft mc, String text) {
        FerrumClient.LOGGER.info(text);
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(text));
        }
    }
}
