package dev.bwmp.sigil.ability;

import dev.bwmp.sigil.api.ability.AbilityConfig;
import dev.bwmp.sigil.api.ability.AbilityContext;
import dev.bwmp.sigil.api.ability.Trigger;
import dev.bwmp.sigil.api.ability.TriggerBinding;
import dev.bwmp.sigil.api.item.CustomItem;
import dev.bwmp.sigil.api.scheduler.SigilScheduler;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/** The activation an ability is running from. */
public final class ContextImpl implements AbilityContext {

    private final Player player;
    private final ItemStack stack;
    private final CustomItem item;
    private final Trigger trigger;
    private final TriggerBinding.Slot slot;
    private final Block block;
    private final Entity entity;
    private final Event event;
    private final AbilityConfig config;
    private final int remainingUses;
    private final SigilScheduler scheduler;

    public ContextImpl(Player player, ItemStack stack, CustomItem item, Trigger trigger, TriggerBinding.Slot slot,
                       Block block, Entity entity, Event event, AbilityConfig config, int remainingUses,
                       SigilScheduler scheduler) {
        this.player = player;
        this.stack = stack;
        this.item = item;
        this.trigger = trigger;
        this.slot = slot;
        this.block = block;
        this.entity = entity;
        this.event = event;
        this.config = config == null ? AbilityConfig.EMPTY : config;
        this.remainingUses = remainingUses;
        this.scheduler = scheduler;
    }

    @Override
    public Player player() {
        return player;
    }

    @Override
    public ItemStack stack() {
        return stack;
    }

    @Override
    public CustomItem item() {
        return item;
    }

    @Override
    public Trigger trigger() {
        return trigger;
    }

    @Override
    public TriggerBinding.Slot slot() {
        return slot;
    }

    @Override
    public Optional<Block> block() {
        return Optional.ofNullable(block);
    }

    @Override
    public Optional<Entity> entity() {
        return Optional.ofNullable(entity);
    }

    @Override
    public <T extends Event> Optional<T> event(Class<T> type) {
        // An empty Optional rather than a ClassCastException: an ability asking
        // for the wrong event type has made a mistake, but it should not take
        // the activation down with it.
        return type.isInstance(event) ? Optional.of(type.cast(event)) : Optional.empty();
    }

    @Override
    public AbilityConfig config() {
        return config;
    }

    @Override
    public int remainingUses() {
        return remainingUses;
    }

    @Override
    public SigilScheduler scheduler() {
        return scheduler;
    }
}
