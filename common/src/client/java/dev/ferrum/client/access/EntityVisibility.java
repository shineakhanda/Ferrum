package dev.ferrum.client.access;

/** Added to every Entity by EntityMixin: the client tick on which the entity was last drawn. */
public interface EntityVisibility {
    int ferrum$lastVisibleTick();

    void ferrum$setLastVisibleTick(int tick);
}
