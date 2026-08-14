package dev.bwmp.sigil.api.ability;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only parameters for an ability declared in YAML.
 * <p>
 * Deliberately not a Bukkit {@code ConfigurationSection}: this is a published
 * API surface, and narrowing it to typed getters with fallbacks means an
 * ability never has to handle a missing or mistyped key.
 * <p>
 * The nested accessors ({@link #getSection}, {@link #getSections},
 * {@link #getKeys}) are {@code default} rather than abstract so that adding
 * them could not break an existing implementation. Anything Sigil hands an
 * ability overrides them; the fallbacks here are what {@link #EMPTY} wants
 * anyway.
 */
public interface AbilityConfig {

    /** Always returns something, so callers need no null checks. */
    AbilityConfig EMPTY = new AbilityConfig() {
        @Override
        public String getString(String key, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String key, int fallback) {
            return fallback;
        }

        @Override
        public double getDouble(String key, double fallback) {
            return fallback;
        }

        @Override
        public boolean getBoolean(String key, boolean fallback) {
            return fallback;
        }

        @Override
        public List<String> getStringList(String key) {
            return List.of();
        }

        @Override
        public boolean contains(String key) {
            return false;
        }
    };

    String getString(String key, String fallback);

    int getInt(String key, int fallback);

    double getDouble(String key, double fallback);

    boolean getBoolean(String key, boolean fallback);

    List<String> getStringList(String key);

    boolean contains(String key);

    /**
     * Read through {@link #getDouble} so an implementation predating this
     * method still answers correctly. Exact for magnitudes below 2^53, which
     * every duration, tick count and cost in practice is.
     */
    default long getLong(String key, long fallback) {
        return (long) getDouble(key, fallback);
    }

    /**
     * A nested map, for a type whose parameters are structured rather than
     * flat — a deployable's stats, a spell's modifiers, a set's bonus tiers.
     */
    default Optional<AbilityConfig> getSection(String key) {
        return Optional.empty();
    }

    /**
     * A list of nested maps, for a type that takes several of something.
     * <p>
     * Returns an empty list when the key is absent <em>or</em> holds something
     * that is not a list of maps, so a malformed entry degrades to "none
     * configured" rather than throwing out of a config read.
     */
    default List<AbilityConfig> getSections(String key) {
        return List.of();
    }

    /** The keys present at this level, for types that iterate their config. */
    default Set<String> getKeys() {
        return Set.of();
    }
}
