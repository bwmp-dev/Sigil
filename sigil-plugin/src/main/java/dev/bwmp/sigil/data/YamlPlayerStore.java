package dev.bwmp.sigil.data;

import dev.bwmp.sigil.api.data.PlayerStore;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One add-on's player store, backed by a YAML file under
 * {@code plugins/Sigil/playerdata/}.
 * <p>
 * A file per owner rather than one shared file: it keeps two add-ons' keys from
 * colliding, and it means uninstalling one is deleting one file rather than
 * editing around it.
 */
public final class YamlPlayerStore implements PlayerStore {

    private final File file;
    private final Logger logger;
    private final YamlConfiguration data;

    /**
     * Set by every write and cleared by a successful save, so the periodic
     * flush skips a store nothing has touched. Atomic because on Folia writes
     * arrive from whichever region thread owns the player.
     */
    private final AtomicBoolean dirty = new AtomicBoolean();

    public YamlPlayerStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        this.data = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    @Override
    public double getDouble(UUID player, String key, double fallback) {
        return data.getDouble(path(player, key), fallback);
    }

    @Override
    public void setDouble(UUID player, String key, double value) {
        write(path(player, key), value);
    }

    @Override
    public int getInt(UUID player, String key, int fallback) {
        return data.getInt(path(player, key), fallback);
    }

    @Override
    public void setInt(UUID player, String key, int value) {
        write(path(player, key), value);
    }

    @Override
    public String getString(UUID player, String key, String fallback) {
        return data.getString(path(player, key), fallback);
    }

    @Override
    public void setString(UUID player, String key, String value) {
        write(path(player, key), value);
    }

    @Override
    public boolean contains(UUID player, String key) {
        return data.contains(path(player, key));
    }

    @Override
    public void clear(UUID player, String key) {
        write(key == null ? player.toString() : path(player, key), null);
    }

    @Override
    public void save() {
        if (!dirty.getAndSet(false)) {
            return;
        }
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            synchronized (data) {
                data.save(file);
            }
        } catch (IOException failure) {
            // Marked dirty again so the next flush retries rather than treating
            // a transient disk error as a successful write.
            dirty.set(true);
            logger.log(Level.WARNING, "Could not save player data to " + file.getName(), failure);
        }
    }

    /** True when there is unsaved work, so a periodic flush can skip idle stores. */
    public boolean isDirty() {
        return dirty.get();
    }

    private void write(String path, Object value) {
        synchronized (data) {
            data.set(path, value);
        }
        dirty.set(true);
    }

    private String path(UUID player, String key) {
        return player + "." + key;
    }
}
