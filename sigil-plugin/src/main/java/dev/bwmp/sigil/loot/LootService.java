package dev.bwmp.sigil.loot;

import dev.bwmp.sigil.registry.SigilRegistries;
import org.bukkit.NamespacedKey;
import org.bukkit.block.TileState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class LootService implements Listener {

    private final JavaPlugin plugin;
    private final SigilRegistries registries;
    private final NamespacedKey sourceKey;
    private final File file;
    private volatile List<LootEntry> entries = List.of();

    public LootService(JavaPlugin plugin, SigilRegistries registries) {
        this.plugin = plugin;
        this.registries = registries;
        this.sourceKey = new NamespacedKey(plugin, "loot_source");
        this.file = new File(plugin.getDataFolder(), "loot.yml");
    }

    public void reload() {
        if (!file.exists()) {
            plugin.saveResource("loot.yml", false);
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (!yaml.getBoolean("enabled", true)) {
            entries = List.of();
            return;
        }

        List<LootEntry> loaded = new ArrayList<>();
        List<Map<?, ?>> configured = yaml.getMapList("entries");
        for (int index = 0; index < configured.size(); index++) {
            Map<?, ?> raw = configured.get(index);
            String label = "loot.yml entry " + (index + 1);

            NamespacedKey item = key(raw.get("item"));
            NamespacedKey table = key(raw.get("table"));
            NamespacedKey source = key(raw.get("source"));
            if (item == null) {
                plugin.getLogger().warning(label + " has no valid item id; skipped.");
                continue;
            }
            if (table == null && source == null) {
                plugin.getLogger().warning(label + " needs a valid table or source; skipped.");
                continue;
            }

            double chance = number(raw.get("chance"), 0.0);
            int min = Math.max(1, integer(raw.get("min"), 1));
            int max = Math.max(min, integer(raw.get("max"), min));
            if (chance <= 0.0 || chance > 1.0) {
                plugin.getLogger().warning(label + " chance must be greater than 0 and at most 1; skipped.");
                continue;
            }

            loaded.add(new LootEntry(item, table, source, chance, min, max));
        }

        entries = List.copyOf(loaded);
        plugin.getLogger().info("Loaded " + entries.size() + " custom loot entry(s).");
    }

    @EventHandler(ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        NamespacedKey table = event.getLootTable().getKey();
        NamespacedKey source = source(event);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (LootEntry entry : entries) {
            if (!entry.matches(table, source) || random.nextDouble() >= entry.chance()) {
                continue;
            }
            registries.sigilItem(entry.itemId()).ifPresent(item -> {
                int stackLimit = item.definition().uses().limited()
                        ? 1
                        : item.definition().base().getMaxStackSize();
                int min = Math.min(entry.minAmount(), stackLimit);
                int max = Math.min(entry.maxAmount(), stackLimit);
                int amount = random.nextInt(min, max + 1);
                event.getLoot().add(item.createStack(amount));
            });
        }
    }

    private NamespacedKey source(LootGenerateEvent event) {
        if (!(event.getInventoryHolder() instanceof TileState state)) {
            return null;
        }
        String raw = state.getPersistentDataContainer().get(sourceKey, PersistentDataType.STRING);
        return key(raw);
    }

    private NamespacedKey key(Object raw) {
        if (raw == null) {
            return null;
        }
        return NamespacedKey.fromString(String.valueOf(raw).toLowerCase(Locale.ROOT));
    }

    private double number(Object raw, double fallback) {
        return raw instanceof Number number ? number.doubleValue() : fallback;
    }

    private int integer(Object raw, int fallback) {
        return raw instanceof Number number ? number.intValue() : fallback;
    }
}
