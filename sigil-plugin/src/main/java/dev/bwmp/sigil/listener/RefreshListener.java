package dev.bwmp.sigil.listener;

import dev.bwmp.sigil.item.RefreshService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Brings stale stacks up to date when a player is about to look at them.
 * <p>
 * Cheap enough to sit on these events because the staleness test is a single
 * integer comparison against persistent data; a stack already current costs one
 * metadata read and nothing else.
 */
public final class RefreshListener implements Listener {

    private final RefreshService refresh;

    public RefreshListener(RefreshService refresh) {
        this.refresh = refresh;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        refresh.refreshInventory(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            refresh.refreshInventory(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeld(PlayerItemHeldEvent event) {
        refresh.refresh(event.getPlayer().getInventory().getItem(event.getNewSlot()));
    }
}
