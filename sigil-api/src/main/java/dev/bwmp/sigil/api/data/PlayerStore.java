package dev.bwmp.sigil.api.data;

import java.util.UUID;

/**
 * Persistent per-player numbers and strings, scoped to one add-on.
 * <p>
 * Sigil itself keeps no player state — an item's charges live on the stack and
 * a cooldown lives in memory — but anything that models a <em>player</em>
 * resource rather than an item one needs somewhere to put it. A mana pool held
 * in memory refills on reconnect, which makes relogging the cheapest way to
 * cast.
 * <p>
 * Each plugin gets its own store, so two add-ons using the key {@code mana}
 * are not sharing a number. Values are flushed periodically and on shutdown;
 * {@link #save()} forces it for anything that must not be lost.
 */
public interface PlayerStore {

    double getDouble(UUID player, String key, double fallback);

    void setDouble(UUID player, String key, double value);

    int getInt(UUID player, String key, int fallback);

    void setInt(UUID player, String key, int value);

    String getString(UUID player, String key, String fallback);

    void setString(UUID player, String key, String value);

    boolean contains(UUID player, String key);

    /** Removes one key. Removes the player's whole record when {@code key} is null. */
    void clear(UUID player, String key);

    /**
     * Writes to disk now.
     * <p>
     * Rarely needed: the store flushes on its own and on shutdown. Worth
     * calling after something a player would be angry to lose to a crash.
     */
    void save();
}
