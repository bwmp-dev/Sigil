package dev.bwmp.sigil.config;

import dev.bwmp.sigil.api.ability.AbilityConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Adapts a Bukkit configuration section to the published {@link AbilityConfig}. */
public final class SectionAbilityConfig implements AbilityConfig {

    private final ConfigurationSection section;

    public SectionAbilityConfig(ConfigurationSection section) {
        this.section = section;
    }

    @Override
    public String getString(String key, String fallback) {
        return section.getString(key, fallback);
    }

    @Override
    public int getInt(String key, int fallback) {
        return section.getInt(key, fallback);
    }

    @Override
    public double getDouble(String key, double fallback) {
        return section.getDouble(key, fallback);
    }

    @Override
    public boolean getBoolean(String key, boolean fallback) {
        return section.getBoolean(key, fallback);
    }

    @Override
    public List<String> getStringList(String key) {
        return section.getStringList(key);
    }

    @Override
    public boolean contains(String key) {
        return section.contains(key);
    }

    @Override
    public long getLong(String key, long fallback) {
        return section.getLong(key, fallback);
    }

    @Override
    public Optional<AbilityConfig> getSection(String key) {
        ConfigurationSection nested = section.getConfigurationSection(key);
        return nested == null ? Optional.empty() : Optional.of(new SectionAbilityConfig(nested));
    }

    @Override
    public List<AbilityConfig> getSections(String key) {
        // getMapList is what a YAML list-of-maps actually parses to; the
        // entries are plain Maps rather than ConfigurationSections, so each has
        // to be wrapped in one to be readable through this interface.
        List<Map<?, ?>> raw = section.getMapList(key);
        if (raw.isEmpty()) {
            return List.of();
        }

        List<AbilityConfig> nested = new ArrayList<>(raw.size());
        for (Map<?, ?> entry : raw) {
            MemoryConfiguration holder = new MemoryConfiguration();
            entry.forEach((mapKey, value) -> holder.set(String.valueOf(mapKey), value));
            nested.add(new SectionAbilityConfig(holder));
        }
        return List.copyOf(nested);
    }

    @Override
    public Set<String> getKeys() {
        return section.getKeys(false);
    }
}
