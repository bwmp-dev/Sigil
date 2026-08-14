package dev.bwmp.sigil.loot;

import dev.bwmp.sigil.api.loot.LootRule;
import dev.bwmp.sigil.item.SigilItem;
import dev.bwmp.sigil.registry.SigilRegistries;
import org.bukkit.NamespacedKey;
import org.bukkit.block.TileState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Puts registered items into the world without a recipe.
 * <p>
 * Two sources feed one list of rules: {@code loot.yml}, which the server owner
 * owns, and {@link #register} calls from add-ons, which ship working drops out
 * of the box. They are kept apart so a {@code /sigil reload} can rebuild the
 * file-backed half without losing registrations made at enable time — the same
 * split, and for the same reason, as items registered through the API.
 */
public final class LootService implements Listener {

    private final JavaPlugin plugin;
    private final SigilRegistries registries;
    private final NamespacedKey sourceKey;
    private final File file;

    private volatile List<LootRule> fileRules = List.of();
    private final List<Registration> registered = new CopyOnWriteArrayList<>();

    /**
     * Looked up by key rather than by constant. {@code LOOT_BONUS_MOBS} was
     * removed in favour of {@code LOOTING} in 1.21, so a field reference
     * compiled against the 1.18.2 floor is a NoSuchFieldError on a modern
     * server. The key has been stable since 1.13.
     */
    private static final Enchantment LOOTING =
            Enchantment.getByKey(NamespacedKey.minecraft("looting"));

    private record Registration(Plugin owner, LootRule rule) {
    }

    public LootService(JavaPlugin plugin, SigilRegistries registries) {
        this.plugin = plugin;
        this.registries = registries;
        this.sourceKey = new NamespacedKey(plugin, "loot_source");
        this.file = new File(plugin.getDataFolder(), "loot.yml");
    }

    /** Adds an add-on's rule. Ignored once {@code owner} disables. */
    public void register(Plugin owner, LootRule rule) {
        registered.add(new Registration(owner, rule));
    }

    public void reload() {
        if (!file.exists()) {
            plugin.saveResource("loot.yml", false);
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (!yaml.getBoolean("enabled", true)) {
            fileRules = List.of();
            return;
        }

        List<LootRule> loaded = new ArrayList<>();
        List<Map<?, ?>> configured = yaml.getMapList("entries");
        for (int index = 0; index < configured.size(); index++) {
            Map<?, ?> raw = configured.get(index);
            String label = "loot.yml entry " + (index + 1);

            NamespacedKey item = key(raw.get("item"));
            if (item == null) {
                plugin.getLogger().warning(label + " has no valid item id; skipped.");
                continue;
            }

            EntityType mob = mob(raw.get("mob"));
            if (raw.get("mob") != null && mob == null) {
                plugin.getLogger().warning(label + " names an unknown mob '" + raw.get("mob") + "'; skipped.");
                continue;
            }

            int min = Math.max(1, integer(raw.get("min"), 1));
            try {
                loaded.add(new LootRule(
                        item,
                        key(raw.get("table")),
                        key(raw.get("source")),
                        mob,
                        number(raw.get("chance"), 0.0),
                        min,
                        Math.max(min, integer(raw.get("max"), min)),
                        bool(raw.get("require-player-kill"), true),
                        number(raw.get("looting-bonus"), 0.0)));
            } catch (IllegalArgumentException invalid) {
                // The record's own validation is the single definition of what a
                // usable rule is, so the file path and the API path cannot drift.
                plugin.getLogger().warning(label + " is invalid: " + invalid.getMessage() + "; skipped.");
            }
        }

        fileRules = List.copyOf(loaded);
        plugin.getLogger().info("Loaded " + fileRules.size() + " custom loot entry(s).");
    }

    @EventHandler(ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        NamespacedKey table = event.getLootTable().getKey();
        NamespacedKey source = source(event);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        forEachRule(rule -> {
            if (rule.isMobRule() || !matchesChest(rule, table, source) || random.nextDouble() >= rule.chance()) {
                return;
            }
            roll(rule, random).ifPresent(event.getLoot()::add);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        EntityType type = event.getEntity().getType();
        Player killer = event.getEntity().getKiller();
        int looting = lootingLevel(killer);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        forEachRule(rule -> {
            if (!rule.isMobRule() || rule.mob() != type) {
                return;
            }
            if (rule.requirePlayerKill() && killer == null) {
                return;
            }
            if (random.nextDouble() >= rule.chanceWithLooting(looting)) {
                return;
            }
            roll(rule, random).ifPresent(event.getDrops()::add);
        });
    }

    /**
     * Walks both rule sources, skipping registrations whose owner has since
     * disabled — which is what keeps a reloaded add-on from dropping twice.
     */
    private void forEachRule(java.util.function.Consumer<LootRule> action) {
        fileRules.forEach(action);
        for (Registration registration : registered) {
            if (registration.owner().isEnabled()) {
                action.accept(registration.rule());
            }
        }
    }

    private boolean matchesChest(LootRule rule, NamespacedKey table, NamespacedKey source) {
        return (rule.table() == null || rule.table().equals(table))
                && (rule.source() == null || rule.source().equals(source));
    }

    private java.util.Optional<ItemStack> roll(LootRule rule, ThreadLocalRandom random) {
        return registries.sigilItem(rule.item()).map(item -> item.createStack(amount(item, rule, random)));
    }

    private int amount(SigilItem item, LootRule rule, ThreadLocalRandom random) {
        int stackLimit = item.definition().uses().limited()
                ? 1
                : item.definition().base().getMaxStackSize();
        int min = Math.min(rule.minAmount(), stackLimit);
        int max = Math.min(rule.maxAmount(), stackLimit);
        return random.nextInt(min, max + 1);
    }

    private int lootingLevel(Player killer) {
        if (killer == null || LOOTING == null) {
            return 0;
        }
        ItemStack weapon = killer.getInventory().getItemInMainHand();
        return weapon.getEnchantmentLevel(LOOTING);
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

    private EntityType mob(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return EntityType.valueOf(String.valueOf(raw).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private double number(Object raw, double fallback) {
        return raw instanceof Number number ? number.doubleValue() : fallback;
    }

    private int integer(Object raw, int fallback) {
        return raw instanceof Number number ? number.intValue() : fallback;
    }

    private boolean bool(Object raw, boolean fallback) {
        return raw instanceof Boolean value ? value : fallback;
    }
}
