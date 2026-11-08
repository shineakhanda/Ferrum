package dev.ferrum.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.ferrum.client.ui.FerrumConfigScreen;

/** Mod Menu "Configure" button. Only loaded when Mod Menu is installed. */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return FerrumConfigScreen::new;
    }
}
