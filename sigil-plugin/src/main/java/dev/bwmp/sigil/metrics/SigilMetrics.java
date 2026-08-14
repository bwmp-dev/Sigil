package dev.bwmp.sigil.metrics;

import dev.bwmp.keystone.metrics.Chart;
import dev.bwmp.keystone.metrics.KeystoneMetrics;
import dev.bwmp.sigil.SigilPlugin;
import dev.bwmp.sigil.item.SigilItem;
import dev.bwmp.sigil.registry.RegistrySnapshot;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What Sigil reports, and where to.
 * <p>
 * Every sampler reads either the published {@link RegistrySnapshot} — immutable
 * and swapped in one write — or a size field. None of them walk a collection a
 * reload mutates: bStats samples on its own thread on Folia, so a sampler
 * iterating live state would be a ConcurrentModificationException that appears
 * on exactly one platform and nowhere a developer would look for it.
 */
public final class SigilMetrics {

    private static final int BSTATS_SERVICE_ID = 33367;
    private static final String TELEMETRY_URL = "https://plugins.metrics.bwmp.dev";
    private static final String TELEMETRY_PROJECT = "sigil";

    private SigilMetrics() {
    }

    public static void start(SigilPlugin plugin) {
        KeystoneMetrics.builder(plugin.keystone())
                .bstats(BSTATS_SERVICE_ID)
                .telemetry(TELEMETRY_URL, TELEMETRY_PROJECT)
                .chart(Chart.singleLine("items", () -> plugin.registries().current().size()))
                .chart(Chart.advancedPie("items_by_rarity", () -> itemsByRarity(plugin)))
                .chart(Chart.singleLine("recipes", () -> recipes(plugin)))
                .chart(Chart.singleLine("ability_types", () -> plugin.registries().abilityTypes().size()))
                .chart(Chart.singleLine("addon_items", () -> plugin.apiDefinitions().size()))
                .chart(Chart.advancedPie("addons", () -> addons(plugin)))
                .chart(Chart.simplePie("builtin_content",
                        () -> plugin.settings().builtinContent() ? "Enabled" : "Disabled"))
                .start();
    }

    /**
     * Counted by rarity id rather than display name: the id is the config key
     * and is the same string on every server, where the display name is
     * whatever the owner renamed it to and would split one slice into hundreds.
     */
    private static Map<String, Integer> itemsByRarity(SigilPlugin plugin) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (SigilItem item : plugin.registries().current().items()) {
            String rarity = item.definition().rarityId();
            counts.merge(rarity == null || rarity.isBlank() ? "unknown" : rarity, 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Summed off the definitions rather than read from {@code RecipeService},
     * whose list of registered keys is a plain ArrayList rebuilt by reload.
     */
    private static int recipes(SigilPlugin plugin) {
        int total = 0;
        for (SigilItem item : plugin.registries().current().items()) {
            total += item.definition().recipes().size();
        }
        return total;
    }

    /** The plugins built on Sigil, which is what decides whether the API is worth keeping. */
    private static Map<String, Integer> addons(SigilPlugin plugin) {
        String name = plugin.getName();
        Map<String, Integer> found = new LinkedHashMap<>();
        for (Plugin other : Bukkit.getPluginManager().getPlugins()) {
            if (other == plugin) {
                continue;
            }
            if (other.getDescription().getDepend().contains(name)
                    || other.getDescription().getSoftDepend().contains(name)) {
                found.put(other.getName(), 1);
            }
        }
        return found;
    }
}
