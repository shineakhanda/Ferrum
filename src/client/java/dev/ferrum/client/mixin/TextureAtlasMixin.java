package dev.ferrum.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import dev.ferrum.client.render.AnimatedSpriteTracker;
import dev.ferrum.client.state.FerrumState;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skips redrawing animated block textures that no visible chunk section uses. */
@Mixin(TextureAtlas.class)
public abstract class TextureAtlasMixin {
    @Shadow
    @Final
    private Identifier location;

    @Unique
    private Map<SpriteContents.AnimationState, TextureAtlasSprite> ferrum$spriteByState;

    @Unique
    private Map<SpriteContents.AnimationState, TextureAtlasSprite> ferrum$sprites() {
        if (this.ferrum$spriteByState == null) {
            this.ferrum$spriteByState = new IdentityHashMap<>();
        }
        return this.ferrum$spriteByState;
    }

    @Unique
    private boolean ferrum$cullThisUpload;

    @Unique
    private boolean ferrum$isBlockAtlas;

    @Unique
    private int ferrum$skipped;

    @Inject(method = "upload", at = @At("HEAD"))
    private void ferrum$resetMap(CallbackInfo ci) {
        this.ferrum$sprites().clear();
    }

    @WrapOperation(
        method = "upload",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;createAnimationState(Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;I)Lnet/minecraft/client/renderer/texture/SpriteContents$AnimationState;")
    )
    private SpriteContents.AnimationState ferrum$rememberSprite(TextureAtlasSprite sprite, GpuBufferSlice slice, int size,
                                                               Operation<SpriteContents.AnimationState> original) {
        SpriteContents.AnimationState state = original.call(sprite, slice, size);
        if (state != null) {
            this.ferrum$sprites().put(state, sprite);
        }
        return state;
    }

    @Inject(method = "uploadAnimationFrames", at = @At("HEAD"))
    private void ferrum$computeVisible(CallbackInfo ci) {
        this.ferrum$isBlockAtlas = this.location.equals(TextureAtlas.LOCATION_BLOCKS);
        this.ferrum$cullThisUpload = FerrumState.animateOnlyVisible
            && this.location.equals(TextureAtlas.LOCATION_BLOCKS)
            && AnimatedSpriteTracker.refresh(Minecraft.getInstance());
        if (this.location.equals(TextureAtlas.LOCATION_BLOCKS)) {
            AnimatedSpriteTracker.lastSkipped = this.ferrum$skipped;
        }
        this.ferrum$skipped = 0;
    }

    @WrapOperation(
        method = "uploadAnimationFrames",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/SpriteContents$AnimationState;needsToDraw()Z")
    )
    private boolean ferrum$onlyVisible(SpriteContents.AnimationState state, Operation<Boolean> original) {
        if (!original.call(state)) {
            return false;
        }
        TextureAtlasSprite sprite = this.ferrum$sprites().get(state);
        if (sprite != null && this.ferrum$isBlockAtlas && !AnimatedSpriteTracker.typeEnabled(sprite)) {
            this.ferrum$skipped++;
            return false;
        }
        if (!this.ferrum$cullThisUpload) {
            return true;
        }
        if (sprite == null || AnimatedSpriteTracker.isVisible(sprite)) {
            return true;
        }
        this.ferrum$skipped++;
        return false;
    }
}
