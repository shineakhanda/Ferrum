package dev.ferrum.client.hardware;

import dev.ferrum.client.config.Preset;

public record HardwareProfile(
    String osName,
    String architecture,
    int processors,
    long maxMemoryMb,
    long systemMemoryMb,
    String cpu,
    String gpuName,
    String gpuVendor,
    String gpuDriver,
    String backend,
    String gpuType,
    int maxTextureSize,
    int maxAnisotropy,
    boolean android,
    boolean pojav,
    boolean glTranslationLayer,
    boolean softwareGpu,
    boolean mobileGpu,
    boolean vulkanActive,
    boolean vulkanMod,
    boolean sodium,
    boolean iris,
    boolean otherDynamicLights
) {
    public Preset recommendedPreset() {
        if (this.softwareGpu) {
            return Preset.POTATO;
        }
        if (this.android || this.mobileGpu) {
            return this.processors <= 2 || this.systemMemoryMb <= 1536 ? Preset.POTATO : Preset.MOBILE;
        }
        if ("CPU".equals(this.gpuType) || "VIRTUAL".equals(this.gpuType)) {
            return Preset.POTATO;
        }
        if (this.processors <= 2 || this.systemMemoryMb <= 2048) {
            return Preset.POTATO;
        }

        String name = lower(this.gpuName);
        String vendor = lower(this.gpuVendor);
        boolean oldIntelIgpu = vendor.contains("intel")
            && (name.contains("hd graphics") || name.contains("2500") || name.contains("3000")
                || name.contains("4000") || name.contains("4400") || name.contains("4600"))
            && !name.contains("uhd") && !name.contains("iris") && !name.contains("arc");
        if (oldIntelIgpu) {
            return Preset.LOW_END;
        }

        if (this.processors <= 4 || this.systemMemoryMb <= 4096) {
            return Preset.LOW_END;
        }
        if ("DISCRETE".equals(this.gpuType)) {
            return Preset.QUALITY;
        }
        return Preset.BALANCED;
    }

    public String shortSummary() {
        return this.osName + " / " + this.architecture + " / " + this.processors + " threads / "
            + this.systemMemoryMb + " MB RAM / " + this.maxMemoryMb + " MB heap / "
            + this.backend + " / " + this.gpuName;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }
}
