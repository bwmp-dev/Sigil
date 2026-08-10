package dev.bwmp.sigil.api.ability;

import java.util.List;

/**
 * Read-only parameters for an ability declared in YAML.
 * <p>
 * Deliberately not a Bukkit {@code ConfigurationSection}: this is a published
 * API surface, and narrowing it to typed getters with fallbacks means an
 * ability never has to handle a missing or mistyped key.
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
}
