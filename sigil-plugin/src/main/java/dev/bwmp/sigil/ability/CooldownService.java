package dev.bwmp.sigil.ability;

import dev.bwmp.sigil.api.ability.CooldownScope;
import dev.bwmp.sigil.item.ItemResolver;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks when each ability may next run.
 * <p>
 * Held in memory and keyed by subject, rather than written onto the ItemStack
 * the way the design this replaces did. Storing a cooldown on the stack made it
 * per-stack rather than per-player, so dropping and re-picking-up an item, or
 * middle-clicking it in creative, reset it — and every activation rewrote the
 * item's metadata.
 * <p>
 * Concurrent because on Folia abilities fire on whichever region thread owns
 * the player, not on one main thread.
 */
public final class CooldownService {

    private final Map<String, Long> expiries = new ConcurrentHashMap<>();
    private final ItemResolver resolver;

    public CooldownService(ItemResolver resolver) {
        this.resolver = resolver;
    }

    /** Milliseconds remaining, or 0 when ready. */
    public long remaining(Player player, ItemStack stack, String abilityKey, CooldownScope scope) {
        Long expiry = expiries.get(key(player, stack, abilityKey, scope));
        if (expiry == null) {
            return 0L;
        }
        long remaining = expiry - System.currentTimeMillis();
        return Math.max(0L, remaining);
    }

    public boolean isReady(Player player, ItemStack stack, String abilityKey, CooldownScope scope) {
        return remaining(player, stack, abilityKey, scope) <= 0L;
    }

    public void start(Player player, ItemStack stack, String abilityKey, CooldownScope scope, long millis) {
        if (millis <= 0L) {
            return;
        }
        expiries.put(key(player, stack, abilityKey, scope), System.currentTimeMillis() + millis);
    }

    public void clear(Player player) {
        String prefix = "player:" + player.getUniqueId() + "|";
        expiries.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public void clearAll() {
        expiries.clear();
    }

    /**
     * Drops expired entries. Without this the map grows for the lifetime of the
     * server, since nothing else ever removes a per-stack key.
     */
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        expiries.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    public int size() {
        return expiries.size();
    }

    private String key(Player player, ItemStack stack, String abilityKey, CooldownScope scope) {
        return switch (scope) {
            case PLAYER -> "player:" + player.getUniqueId() + "|" + abilityKey;
            // Falls back to the player when a stack has no unique id, which is
            // the case for unlimited-use items. Sharing one global key across
            // every holder would be worse than a slightly stricter scope.
            case STACK -> resolver.uidOf(stack)
                    .map(uid -> "stack:" + uid + "|" + abilityKey)
                    .orElse("player:" + player.getUniqueId() + "|" + abilityKey);
            case GLOBAL -> "global:|" + abilityKey;
        };
    }
}
