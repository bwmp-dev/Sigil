package dev.bwmp.sigil.data;

import dev.bwmp.sigil.api.data.PlayerStore;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * The player stores Sigil hands out, one per owning plugin.
 * <p>
 * Held rather than created per call so that two requests from the same add-on
 * get the same file and the same in-memory state — two views of one file would
 * silently overwrite each other on save.
 */
public final class PlayerStores {

    private final Map<String, YamlPlayerStore> stores = new ConcurrentHashMap<>();
    private final File directory;
    private final Logger logger;

    public PlayerStores(File dataFolder, Logger logger) {
        this.directory = new File(dataFolder, "playerdata");
        this.logger = logger;
    }

    public PlayerStore of(Plugin owner) {
        String name = owner.getName().toLowerCase(Locale.ROOT);
        return stores.computeIfAbsent(name,
                key -> new YamlPlayerStore(new File(directory, key + ".yml"), logger));
    }

    public void saveAll() {
        stores.values().forEach(YamlPlayerStore::save);
    }
}
