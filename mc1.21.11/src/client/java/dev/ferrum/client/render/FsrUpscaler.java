package dev.ferrum.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.OptionalInt;
import net.minecraft.resources.Identifier;

/**
 * FSR 1 upscaling for render scale (1.21.11). Same two passes as the 26.3 build: EASU into a
 * full-resolution intermediate, then RCAS sharpening into the real framebuffer. Pipelines are
 * compiled by the GL device on first use; sharpness steps are separate cached pipelines.
 */
public final class FsrUpscaler {
    private static final RenderPipeline EASU = base("pipeline/fsr_easu", "core/fsr_easu").build();
    private static final Int2ObjectOpenHashMap<RenderPipeline> RCAS = new Int2ObjectOpenHashMap<>();

    private TextureTarget intermediate;

    private static RenderPipeline.Builder base(String location, String fragment) {
        return RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("ferrum", location))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Identifier.fromNamespaceAndPath("ferrum", fragment))
            .withSampler("InSampler")
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES);
    }

    /** Sharpness 0-100 % mapped to stops (100 % = 0 stops = strongest, 0 % = 2 stops = mild). */
    private static RenderPipeline rcas(int sharpnessPercent) {
        int step = Math.max(0, Math.min(20, Math.round(sharpnessPercent / 5.0F)));
        return RCAS.computeIfAbsent(step, s -> base("pipeline/fsr_rcas_" + s, "core/fsr_rcas")
            .withShaderDefine("FSR_SHARPNESS", 2.0F * (1.0F - s / 20.0F))
            .build());
    }

    public void upscale(RenderTarget source, RenderTarget destination, int sharpnessPercent) {
        int w = destination.width;
        int h = destination.height;
        if (this.intermediate == null) {
            this.intermediate = new TextureTarget("ferrum_fsr_easu", w, h, false);
        } else if (this.intermediate.width != w || this.intermediate.height != h) {
            this.intermediate.resize(w, h);
        }
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

        try (RenderPass pass = encoder.createRenderPass(() -> "Ferrum FSR EASU",
            this.intermediate.getColorTextureView(), OptionalInt.empty())) {
            pass.setPipeline(EASU);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("InSampler", source.getColorTextureView(), sampler);
            pass.draw(0, 3);
        }

        try (RenderPass pass = encoder.createRenderPass(() -> "Ferrum FSR RCAS",
            destination.getColorTextureView(), OptionalInt.empty())) {
            pass.setPipeline(rcas(sharpnessPercent));
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("InSampler", this.intermediate.getColorTextureView(), sampler);
            pass.draw(0, 3);
        }
    }

    public void close() {
        if (this.intermediate != null) {
            this.intermediate.destroyBuffers();
            this.intermediate = null;
        }
    }
}
