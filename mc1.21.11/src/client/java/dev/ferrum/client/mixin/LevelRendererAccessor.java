package dev.ferrum.client.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.21.11 has no public getter for the visible section list (26.x added one). */
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
    @Accessor("visibleSections")
    ObjectArrayList<SectionRenderDispatcher.RenderSection> ferrum$visibleSections();
}
