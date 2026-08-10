package dev.bwmp.sigil.permission;

import dev.bwmp.sigil.api.ability.Ability;
import dev.bwmp.sigil.item.SigilItem;
import dev.bwmp.sigil.registry.SigilRegistries;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

/**
 * Deny-only permissions: everything is allowed unless explicitly revoked.
 * <p>
 * <b>Nodes must be registered for this to work at all.</b> Bukkit falls back to
 * {@link PermissionDefault#OP} for a node it has never seen, so an unregistered
 * "default true" node returns false for every non-op player — the exact
 * opposite of the intent. Registering each node with
 * {@link PermissionDefault#TRUE} is what makes {@code -sigil.item.x.y} in a
 * permissions plugin the only thing that ever denies anything.
 */
public final class Permissions {

    public static final String COMMAND_ROOT = "sigil.command";

    private Permissions() {
    }

    public static String itemNode(SigilItem item) {
        return "sigil.item." + item.id().getNamespace() + "." + item.id().getKey();
    }

    public static String rarityNode(String rarityId) {
        return "sigil.rarity." + rarityId.toLowerCase();
    }

    public static String abilityNode(SigilItem item, Ability ability) {
        return "sigil.ability." + item.id().getNamespace() + "." + item.id().getKey()
                + "." + ability.meta().id();
    }

    /**
     * Registers every node as default-true. Re-registration is skipped rather
     * than throwing, so a reload is safe.
     */
    public static void registerAll(SigilRegistries registries) {
        for (SigilItem item : registries.sigilItems()) {
            register(item.definition().permission() == null ? itemNode(item) : item.definition().permission());
            for (Ability ability : item.abilities()) {
                register(abilityNode(item, ability));
            }
        }
        registries.rarities().forEach(rarity -> register(rarityNode(rarity.id())));
    }

    private static void register(String node) {
        if (Bukkit.getPluginManager().getPermission(node) != null) {
            return;
        }
        Bukkit.getPluginManager().addPermission(new Permission(node, PermissionDefault.TRUE));
    }

    public static boolean canUse(Player player, SigilItem item, SigilRegistries registries) {
        String itemPermission = item.definition().permission() == null
                ? itemNode(item)
                : item.definition().permission();
        if (!player.hasPermission(itemPermission)) {
            return false;
        }
        // AND, not OR: denying a rarity should deny everything of that rarity,
        // and denying one item should deny it regardless of rarity. Both are
        // grants by default, so requiring both costs nothing until someone
        // revokes one deliberately.
        return player.hasPermission(rarityNode(item.definition().rarityId()));
    }

    public static boolean canUseAbility(Player player, SigilItem item, Ability ability) {
        return player.hasPermission(abilityNode(item, ability));
    }
}
