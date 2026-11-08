package dev.ferrum.client.access;

import java.util.Set;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Added to SectionCompiler.Results and CompiledSectionMesh: animated sprites used by that section. */
public interface AnimatedSpriteHolder {
    Set<TextureAtlasSprite> ferrum$animatedSprites();

    void ferrum$setAnimatedSprites(Set<TextureAtlasSprite> sprites);
}
