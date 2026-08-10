package dev.bwmp.sigil.config;

import dev.bwmp.keystone.config.ManagedConfig;
import dev.bwmp.sigil.api.item.Rarity;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * config.yml, parsed once into an immutable value.
 * <p>
 * Nothing reads the file at runtime; a reload builds a replacement and swaps
 * it in.
 */
public final class SigilSettings {

    private final List<String> loreTemplate;
    private final Map<String, Rarity> rarities;
    private final boolean builtinContent;
    private final int tickIntervalTicks;
    private final boolean cooldownActionBar;
    private final boolean vanillaCooldownOverlay;

    private final boolean defaultVanillaRecipes;
    private final boolean defaultAnvilRename;
    private final boolean defaultAnvilCombine;
    private final boolean defaultEnchanting;
    private final int anvilCombineCost;

    private SigilSettings(ManagedConfig config) {
        List<String> template = config.stringList("lore-template");
        this.loreTemplate = template.isEmpty()
                ? List.of("<description>", "", "<abilities>", "", "<uses>", "", "<rarity>")
                : List.copyOf(template);

        this.rarities = readRarities(config);
        this.builtinContent = config.bool("content.builtin", true);
        this.tickIntervalTicks = Math.max(1, config.integer("abilities.tick-interval", 4));
        this.cooldownActionBar = config.bool("abilities.cooldown-actionbar", true);
        this.vanillaCooldownOverlay = config.bool("abilities.vanilla-cooldown-overlay", true);

        this.defaultVanillaRecipes = config.bool("rules.vanilla-recipes", false);
        this.defaultAnvilRename = config.bool("rules.anvil-rename", false);
        this.defaultAnvilCombine = config.bool("rules.anvil-combine", true);
        this.defaultEnchanting = config.bool("rules.enchanting", false);
        this.anvilCombineCost = Math.max(0, config.integer("rules.anvil-combine-cost", 5));
    }

    public static SigilSettings load(ManagedConfig config) {
        return new SigilSettings(config);
    }

    private static Map<String, Rarity> readRarities(ManagedConfig config) {
        Map<String, Rarity> parsed = new LinkedHashMap<>();
        ConfigurationSection section = config.section("rarities");
        if (section == null) {
            return defaultRarities();
        }

        int implicitOrder = 0;
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            String key = id.toLowerCase(Locale.ROOT);
            parsed.put(key, new Rarity(
                    key,
                    entry.getString("display", id),
                    entry.getString("colour", entry.getString("color", "<gray>")),
                    entry.getInt("order", implicitOrder++),
                    entry.getBoolean("glow", false)));
        }
        return parsed.isEmpty() ? defaultRarities() : parsed;
    }

    private static Map<String, Rarity> defaultRarities() {
        Map<String, Rarity> defaults = new LinkedHashMap<>();
        defaults.put("common", new Rarity("common", "Common", "<#04ff00>", 0, false));
        defaults.put("uncommon", new Rarity("uncommon", "Uncommon", "<#009b02>", 1, false));
        defaults.put("rare", new Rarity("rare", "Rare", "<#00b0ff>", 2, false));
        defaults.put("epic", new Rarity("epic", "Epic", "<#9b00ff>", 3, true));
        defaults.put("legendary", new Rarity("legendary", "Legendary", "<#ffbe39>", 4, true));
        return defaults;
    }

    public List<String> loreTemplate() {
        return loreTemplate;
    }

    public Map<String, Rarity> rarities() {
        return rarities;
    }

    public List<Rarity> rarityList() {
        return new ArrayList<>(rarities.values());
    }

    public boolean builtinContent() {
        return builtinContent;
    }

    public int tickIntervalTicks() {
        return tickIntervalTicks;
    }

    public boolean cooldownActionBar() {
        return cooldownActionBar;
    }

    public boolean vanillaCooldownOverlay() {
        return vanillaCooldownOverlay;
    }

    public boolean defaultVanillaRecipes() {
        return defaultVanillaRecipes;
    }

    public boolean defaultAnvilRename() {
        return defaultAnvilRename;
    }

    public boolean defaultAnvilCombine() {
        return defaultAnvilCombine;
    }

    public boolean defaultEnchanting() {
        return defaultEnchanting;
    }

    public int anvilCombineCost() {
        return anvilCombineCost;
    }
}
