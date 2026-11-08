package dev.ferrum.client.mixin;

import dev.ferrum.client.access.EntityVisibility;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public abstract class EntityMixin implements EntityVisibility {
    @Unique
    private int ferrum$lastVisibleTick = Integer.MIN_VALUE / 2;

    @Override
    public int ferrum$lastVisibleTick() {
        return this.ferrum$lastVisibleTick;
    }

    @Override
    public void ferrum$setLastVisibleTick(int tick) {
        this.ferrum$lastVisibleTick = tick;
    }
}
