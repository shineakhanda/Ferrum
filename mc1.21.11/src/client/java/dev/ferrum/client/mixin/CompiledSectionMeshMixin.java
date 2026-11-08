package dev.ferrum.client.mixin;

import dev.ferrum.client.access.AnimatedSpriteHolder;
import java.util.Set;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.TranslucencyPointOfView;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CompiledSectionMesh.class)
public abstract class CompiledSectionMeshMixin implements AnimatedSpriteHolder, dev.ferrum.client.access.FaceRangesHolder {
    @Unique
    private dev.ferrum.client.render.terrain.FaceRanges[] ferrum$faces;

    @Override
    public dev.ferrum.client.render.terrain.FaceRanges[] ferrum$faceRanges() {
        return this.ferrum$faces;
    }

    @Override
    public void ferrum$setFaceRanges(dev.ferrum.client.render.terrain.FaceRanges[] ranges) {
        this.ferrum$faces = ranges;
    }

    @Unique
    private Set<TextureAtlasSprite> ferrum$sprites = Set.of();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ferrum$copySprites(TranslucencyPointOfView pov, SectionCompiler.Results results, CallbackInfo ci) {
        this.ferrum$sprites = ((AnimatedSpriteHolder) (Object) results).ferrum$animatedSprites();
        this.ferrum$faces = ((dev.ferrum.client.access.FaceRangesHolder) (Object) results).ferrum$faceRanges();
    }

    @Override
    public Set<TextureAtlasSprite> ferrum$animatedSprites() {
        return this.ferrum$sprites;
    }

    @Override
    public void ferrum$setAnimatedSprites(Set<TextureAtlasSprite> sprites) {
        this.ferrum$sprites = sprites;
    }
}
