package dev.bwmp.sigil.api.event;

import dev.bwmp.sigil.api.ability.Ability;
import dev.bwmp.sigil.api.ability.Trigger;
import dev.bwmp.sigil.api.item.CustomItem;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Fired before an ability runs, after its permission and cooldown checks pass.
 * <p>
 * Cancelling suppresses the ability without consuming a use or starting a
 * cooldown — the activation is treated as if it never happened, which is what a
 * region-protection plugin wants.
 */
public class CustomItemAbilityEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ItemStack stack;
    private final CustomItem item;
    private final Ability ability;
    private final Trigger trigger;
    private boolean cancelled;

    public CustomItemAbilityEvent(Player player, ItemStack stack, CustomItem item, Ability ability, Trigger trigger) {
        this.player = player;
        this.stack = stack;
        this.item = item;
        this.ability = ability;
        this.trigger = trigger;
    }

    public Player getPlayer() {
        return player;
    }

    public ItemStack getStack() {
        return stack;
    }

    public CustomItem getItem() {
        return item;
    }

    public Ability getAbility() {
        return ability;
    }

    public Trigger getTrigger() {
        return trigger;
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
