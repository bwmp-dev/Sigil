package dev.bwmp.sigil.config;

import dev.bwmp.sigil.api.ability.AbilityConfig;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

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
}
