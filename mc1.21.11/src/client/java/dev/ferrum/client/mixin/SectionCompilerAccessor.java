package dev.ferrum.client.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import java.util.Map;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SectionCompiler.class)
public interface SectionCompilerAccessor {
    @Invoker("getOrBeginLayer")
    BufferBuilder ferrum$getOrBeginLayer(Map<ChunkSectionLayer, BufferBuilder> layers,
                                         SectionBufferBuilderPack buffers, ChunkSectionLayer layer);
}
