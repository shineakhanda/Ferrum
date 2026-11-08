package dev.ferrum.client.compat;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Skips Ferrum mixins whose job is already done by another installed mod (see {@link CompatRules}).
 * Runs while mixin configs load, before any Minecraft class is loaded, so a skipped mixin never
 * touches its target and cannot collide with the other mod's patches.
 */
public final class FerrumMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("Ferrum");

    @Override
    public void onLoad(String mixinPackage) {
        CompatRules.disabledFeatures().forEach((feature, mod) ->
            LOGGER.info("Ferrum: {} disabled, {} provides its own", feature, mod));
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        return CompatRules.replacementFor(simpleName) == null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
