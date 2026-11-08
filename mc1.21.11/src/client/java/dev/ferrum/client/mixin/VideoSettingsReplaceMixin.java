package dev.ferrum.client.mixin;

import dev.ferrum.client.ui.FerrumConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Opens Ferrum's options screen wherever vanilla would open Video Settings (Sodium-style). */
@Mixin(Minecraft.class)
public abstract class VideoSettingsReplaceMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen ferrum$replaceVideoSettings(Screen screen) {
        return FerrumConfigScreen.replaceVideoSettings(screen);
    }
}
