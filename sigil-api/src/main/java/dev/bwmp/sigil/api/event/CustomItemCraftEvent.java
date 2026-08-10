package dev.bwmp.sigil.api.event;

import dev.bwmp.sigil.api.item.CustomItem;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/** Fired when a player is about to craft a Sigil item. Cancelling blocks it. */
public class CustomItemCraftEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final CustomItem item;
    private ItemStack result;
    private boolean cancelled;

    public CustomItemCraftEvent(Player player, CustomItem item, ItemStack result) {
        this.player = player;
        this.item = item;
        this.result = result;
    }

    public Player getPlayer() {
        return player;
    }

    public CustomItem getItem() {
        return item;
    }

    public ItemStack getResult() {
        return result;
    }

    /** Replaces what the crafting produces. */
    public void setResult(ItemStack result) {
        this.result = result;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
