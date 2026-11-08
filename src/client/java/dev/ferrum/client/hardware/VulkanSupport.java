package dev.ferrum.client.hardware;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.BackendCreationException;
import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanBackend;
import dev.ferrum.client.FerrumClient;
import dev.ferrum.client.compat.ModCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PreferredGraphicsApi;

public final class VulkanSupport {
    private static volatile Boolean runtimeAvailable;
    private static volatile String runtimeMessage = "not probed";

    private VulkanSupport() {
    }

    public record Status(boolean runtimeAvailable, String message, String activeBackend, String preferredBackend) {
    }

    public static Status status() {
        boolean available = probeRuntime();
        Minecraft minecraft = Minecraft.getInstance();
        String preferred = minecraft == null || minecraft.options == null
            ? "unknown"
            : minecraft.options.preferredGraphicsBackend().get().getSerializedName();
        return new Status(available, runtimeMessage, activeBackend(), preferred);
    }

    public static String activeBackend() {
        try {
            GpuDevice device = RenderSystem.tryGetDevice();
            if (device != null) {
                DeviceInfo info = device.getDeviceInfo();
                if (info != null) {
                    return info.backendName();
                }
            }
        } catch (Throwable ignored) {
        }
        return "unknown";
    }

    public static boolean isVulkanActive() {
        return activeBackend().toLowerCase(java.util.Locale.ROOT).contains("vulkan");
    }

    public static boolean probeRuntime() {
        Boolean cached = runtimeAvailable;
        if (cached != null) {
            return cached;
        }
        synchronized (VulkanSupport.class) {
            if (runtimeAvailable != null) {
                return runtimeAvailable;
            }
            try {
                BackendCreationException failure = VulkanBackend.checkBackendAvailable();
                if (failure == null) {
                    runtimeAvailable = true;
                    runtimeMessage = "Vulkan device is available";
                } else {
                    runtimeAvailable = false;
                    runtimeMessage = failure.getReason().displayName() + ": " + failure.getMessage();
                }
            } catch (Throwable t) {
                runtimeAvailable = false;
                runtimeMessage = "Vulkan probe failed: " + t.getClass().getSimpleName();
                FerrumClient.LOGGER.debug("Vulkan probe failed", t);
            }
            FerrumClient.LOGGER.info("Vulkan runtime: {} ({})", runtimeAvailable, runtimeMessage);
            return runtimeAvailable;
        }
    }

    public static PreferredGraphicsApi recommended(HardwareProfile profile) {
        if (ModCompat.IRIS || ModCompat.SODIUM) {
            return PreferredGraphicsApi.OPENGL;
        }
        if (profile.android() || profile.mobileGpu() || profile.glTranslationLayer() || profile.softwareGpu()) {
            return PreferredGraphicsApi.OPENGL;
        }
        if (!probeRuntime()) {
            return PreferredGraphicsApi.DEFAULT;
        }
        if ("DISCRETE".equals(profile.gpuType())) {
            String os = profile.osName().toLowerCase(java.util.Locale.ROOT);
            if (os.contains("windows") || os.contains("linux")) {
                return PreferredGraphicsApi.VULKAN;
            }
        }
        return PreferredGraphicsApi.DEFAULT;
    }

    public static PreferredGraphicsApi preferred() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null) {
            return PreferredGraphicsApi.DEFAULT;
        }
        return minecraft.options.preferredGraphicsBackend().get();
    }

    public static void apply(PreferredGraphicsApi api) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null) {
            return;
        }
        minecraft.options.preferredGraphicsBackend().set(api);
        minecraft.options.save();
        FerrumClient.LOGGER.info("Ferrum set preferred graphics API to {}. Restart required.", api.getSerializedName());
    }
}
