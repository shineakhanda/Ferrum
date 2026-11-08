package dev.ferrum.client.config;

import java.util.Locale;

public enum Preset {
    AUTO("auto", "Auto"),
    POTATO("potato", "Potato"),
    MOBILE("mobile", "Mobile"),
    LOW_END("low", "Low End"),
    BALANCED("balanced", "Balanced"),
    QUALITY("quality", "Quality");

    private final String id;
    private final String displayName;

    Preset(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return this.id;
    }

    public String displayName() {
        return this.displayName;
    }

    public static Preset byId(String value) {
        if (value != null) {
            String normalized = value.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
            for (Preset preset : values()) {
                if (preset.id.equals(normalized) || preset.name().toLowerCase(Locale.ROOT).replace("_", "").equals(normalized)) {
                    return preset;
                }
            }
            if (normalized.equals("lowend") || normalized.equals("low")) {
                return LOW_END;
            }
            if (normalized.equals("high") || normalized.equals("highquality")) {
                return QUALITY;
            }
        }
        return AUTO;
    }
}
