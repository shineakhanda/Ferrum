package dev.ferrum.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** In 1.21.11 the main framebuffer lives on Minecraft (it moved to GameRenderer in 26.x). */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("mainRenderTarget")
    RenderTarget ferrum$getMainRenderTarget();

    @Mutable
    @Accessor("mainRenderTarget")
    void ferrum$setMainRenderTarget(RenderTarget target);
}
