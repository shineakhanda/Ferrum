package dev.ferrum.client.hardware;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.ferrum.client.compat.ModCompat;

/**
 * 1.21.11 version. Vanilla 1.21.11 only ships the OpenGL backend (Vulkan arrived in 26.x), so this
 * only reports what is running: plain OpenGL, a GL translation layer, or VulkanMod if installed.
 * The API mirrors the 26.3 class where it makes sense so shared code reads the same.
 */
public final class VulkanSupport {
    private VulkanSupport() {
    }

    public record Status(boolean runtimeAvailable, String message, String activeBackend, String preferredBackend) {
    }

    public static Status status() {
        return new Status(false,
            ModCompat.VULKAN_MOD
                ? "VulkanMod is installed and replaces the renderer"
                : "Minecraft 1.21.11 has no built-in Vulkan backend (use the 26.3 build or VulkanMod)",
            activeBackend(), "OpenGL");
    }

    public static String activeBackend() {
        if (ModCompat.VULKAN_MOD) {
            return "VulkanMod";
        }
        try {
            GpuDevice device = RenderSystem.tryGetDevice();
            if (device != null) {
                return device.getBackendName();
            }
        } catch (Throwable ignored) {
        }
        return "OpenGL";
    }

    public static boolean isVulkanActive() {
        return ModCompat.VULKAN_MOD;
    }
}
