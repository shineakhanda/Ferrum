package dev.ferrum.client.mixin;

import dev.ferrum.client.access.AnimatedSpriteHolder;
import java.util.Set;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SectionCompiler.Results.class)
public abstract class SectionCompilerResultsMixin implements AnimatedSpriteHolder, dev.ferrum.client.access.FaceRangesHolder {
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

    @Override
    public Set<TextureAtlasSprite> ferrum$animatedSprites() {
        return this.ferrum$sprites;
    }

    @Override
    public void ferrum$setAnimatedSprites(Set<TextureAtlasSprite> sprites) {
        this.ferrum$sprites = sprites;
    }
}
