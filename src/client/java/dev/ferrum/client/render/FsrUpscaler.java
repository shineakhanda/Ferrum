package dev.ferrum.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * FSR 1 upscaling for render scale (26.3). Two full-screen passes:
 * <ol>
 *   <li>EASU: edge-adaptive upsample from the scaled world image into a full-resolution
 *       intermediate target ({@code assets/ferrum/shaders/core/fsr_easu.fsh}).</li>
 *   <li>RCAS: contrast-adaptive sharpening from the intermediate into the real framebuffer
 *       ({@code fsr_rcas.fsh}). Sharpness is a shader define, so each sharpness step is its own
 *       pipeline, compiled on first use and cached.</li>
 * </ol>
 * The intermediate is colour-only RGBA8 and is freed when render scale goes back to 100%.
 */
public final class FsrUpscaler {
    private static final RenderPipeline EASU = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath("ferrum", "pipeline/fsr_easu"))
        .withVertexShader("core/screenquad")
        .withFragmentShader(Identifier.fromNamespaceAndPath("ferrum", "core/fsr_easu"))
        .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
        .withColorTargetState(ColorTargetState.DEFAULT)
        .build();

    private static final Int2ObjectOpenHashMap<RenderPipeline> RCAS = new Int2ObjectOpenHashMap<>();

    private TextureTarget intermediate;

    /** Sharpness 0-100 % mapped to stops (100 % = 0 stops = strongest, 0 % = 2 stops = mild). */
    private static RenderPipeline rcas(int sharpnessPercent) {
        int step = Math.max(0, Math.min(20, Math.round(sharpnessPercent / 5.0F)));
        return RCAS.computeIfAbsent(step, s -> RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("ferrum", "pipeline/fsr_rcas_" + s))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Identifier.fromNamespaceAndPath("ferrum", "core/fsr_rcas"))
            .withShaderDefine("FSR_SHARPNESS", 2.0F * (1.0F - s / 20.0F))
            .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build());
    }

    public void upscale(RenderTarget source, RenderTarget destination, int sharpnessPercent) {
        int w = destination.width;
        int h = destination.height;
        if (this.intermediate == null) {
            this.intermediate = new TextureTarget("ferrum_fsr_easu", w, h, GpuFormat.RGBA8_UNORM, null);
        } else if (this.intermediate.width != w || this.intermediate.height != h) {
            this.intermediate.resize(w, h);
        }
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

        try (RenderPass pass = encoder.createRenderPass(() -> "Ferrum FSR EASU",
            this.intermediate.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(EASU));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", source.getColorTextureView(), sampler);
            pass.draw(3, 1, 0, 0);
        }

        try (RenderPass pass = encoder.createRenderPass(() -> "Ferrum FSR RCAS",
            destination.getColorTextureView(), Optional.empty(),
            destination.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(rcas(sharpnessPercent)));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", this.intermediate.getColorTextureView(), sampler);
            pass.draw(3, 1, 0, 0);
        }
    }

    public void close() {
        if (this.intermediate != null) {
            this.intermediate.destroyBuffers();
            this.intermediate = null;
        }
    }
}
