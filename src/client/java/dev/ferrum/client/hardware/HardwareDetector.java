package dev.ferrum.client.hardware;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.device.DeviceLimits;
import com.mojang.renderpearl.api.device.GpuDevice;
import dev.ferrum.client.FerrumClient;
import dev.ferrum.client.compat.ModCompat;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

public final class HardwareDetector {
    private HardwareDetector() {
    }

    public static HardwareProfile detect() {
        String osName = System.getProperty("os.name", "unknown");
        String architecture = System.getProperty("os.arch", "unknown");
        int processors = Runtime.getRuntime().availableProcessors();
        long maxMemoryMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        long systemMemoryMb = systemMemoryMb(maxMemoryMb);

        DeviceInfo info = null;
        try {
            GpuDevice device = RenderSystem.tryGetDevice();
            if (device != null) {
                info = device.getDeviceInfo();
            }
        } catch (Throwable t) {
            FerrumClient.LOGGER.debug("Graphics device information is not available yet", t);
        }

        String gpuName = info == null ? "" : safe(info.name());
        String gpuVendor = info == null ? "" : safe(info.vendorName());
        String gpuDriver = info == null ? "" : safe(info.driverInfo());
        String backend = info == null ? "unknown" : safe(info.backendName());
        String gpuType = info == null ? "UNKNOWN" : info.type().name();
        DeviceLimits limits = info == null ? null : info.limits();
        int maxTextureSize = limits == null ? 16384 : limits.maxTextureSize();
        int maxAnisotropy = limits == null ? 1 : limits.maxAnisotropy();

        String combined = (gpuName + " " + gpuVendor + " " + gpuDriver + " " + backend).toLowerCase(Locale.ROOT);
        String env = environmentText();

        boolean android = osName.toLowerCase(Locale.ROOT).contains("android")
            || System.getProperty("os.version", "").toLowerCase(Locale.ROOT).contains("android")
            || System.getProperty("pojav.path.minecraft") != null
            || env.contains("pojav_renderer") || env.contains("pojav_nativedir")
            || env.contains("mobileglues") || env.contains("mg_dir_path")
            || Files.isDirectory(Path.of("/system/app"))
            || env.contains("android_root")
            || env.contains("android_data")
            || env.contains("pojav_launcher")
            || classExists("android.os.Build");
        boolean pojav = env.contains("pojav") || env.contains("zalith") || env.contains("foldcraft")
            || classPath().contains("pojav") || classPath().contains("zalith") || classPath().contains("foldcraft");
        boolean glTranslationLayer = containsAny(combined,
            "gl4es", "mobileglues", "mobile glues", "angle", "zink", "virgl", "swiftshader",
            "llvmpipe", "virtgl", "d3d12", "opengl es")
            || env.contains("libgl_es") || env.contains("libgl_name");
        boolean softwareGpu = containsAny(combined,
            "llvmpipe", "swiftshader", "softpipe", "software rasterizer", "mesa offscreen");
        boolean mobileGpu = android || containsAny(combined,
            "adreno", "mali", "powervr", "immortalis", "xclipse", "videocore", "qualcomm", "imagination")
            || (containsAny(gpuVendor, "arm") && !osName.toLowerCase(Locale.ROOT).contains("mac"));
        boolean vulkanActive = combined.contains("vulkan");

        return new HardwareProfile(
            osName,
            architecture,
            processors,
            maxMemoryMb,
            systemMemoryMb,
            cpuName(),
            gpuName.isEmpty() ? "Unknown GPU" : gpuName,
            gpuVendor.isEmpty() ? "Unknown" : gpuVendor,
            gpuDriver,
            backend,
            gpuType,
            maxTextureSize,
            maxAnisotropy,
            android,
            pojav,
            glTranslationLayer,
            softwareGpu,
            mobileGpu,
            vulkanActive,
            ModCompat.VULKAN_MOD,
            ModCompat.SODIUM,
            ModCompat.IRIS,
            ModCompat.OTHER_DYNAMIC_LIGHTS
        );
    }

    private static long systemMemoryMb(long fallbackHeapMb) {
        try {
            java.lang.management.OperatingSystemMXBean bean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
                long total = sunBean.getTotalMemorySize();
                if (total > 0L) {
                    return total / (1024L * 1024L);
                }
            }
        } catch (Throwable ignored) {
        }
        return fallbackHeapMb;
    }

    private static String cpuName() {
        String identifier = System.getProperty("PROCESSOR_IDENTIFIER");
        if (identifier != null && !identifier.isBlank()) {
            return identifier;
        }

        Path cpuInfo = Path.of("/proc/cpuinfo");
        if (Files.isReadable(cpuInfo)) {
            try (BufferedReader reader = Files.newBufferedReader(cpuInfo, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String lower = line.toLowerCase(Locale.ROOT);
                    if (lower.startsWith("model name") || lower.startsWith("hardware")) {
                        int colon = line.indexOf(':');
                        if (colon >= 0) {
                            return line.substring(colon + 1).trim();
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        String arch = System.getProperty("os.arch", "unknown");
        return arch.contains("aarch64") || arch.contains("arm") ? "ARM " + arch : arch;
    }

    private static String environmentText() {
        StringBuilder builder = new StringBuilder();
        try {
            for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
                builder.append(entry.getKey().toLowerCase(Locale.ROOT)).append('=')
                    .append(entry.getValue() == null ? "" : entry.getValue().toLowerCase(Locale.ROOT)).append(' ');
            }
        } catch (Throwable ignored) {
        }
        return builder.toString();
    }

    private static String classPath() {
        String classPath = System.getProperty("java.class.path", "");
        return classPath.toLowerCase(Locale.ROOT);
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, HardwareDetector.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
