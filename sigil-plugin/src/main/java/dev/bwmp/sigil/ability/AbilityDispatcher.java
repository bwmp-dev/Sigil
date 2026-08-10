package dev.bwmp.sigil.ability;

import dev.bwmp.keystone.text.MessageService;
import dev.bwmp.sigil.api.ability.Ability;
import dev.bwmp.sigil.api.ability.AbilityConfig;
import dev.bwmp.sigil.api.ability.AbilityMeta;
import dev.bwmp.sigil.api.ability.ActionResult;
import dev.bwmp.sigil.api.ability.Trigger;
import dev.bwmp.sigil.api.ability.TriggerBinding;
import dev.bwmp.sigil.api.event.CustomItemAbilityEvent;
import dev.bwmp.sigil.api.scheduler.SigilScheduler;
import dev.bwmp.sigil.config.SigilSettings;
import dev.bwmp.sigil.item.ItemResolver;
import dev.bwmp.sigil.item.SigilItem;
import dev.bwmp.sigil.item.UsesService;
import dev.bwmp.sigil.permission.Permissions;
import dev.bwmp.sigil.registry.SigilRegistries;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The single path from "something happened" to "an ability ran".
 * <p>
 * Permission checks, cooldown enforcement, use consumption and event
 * cancellation all live here rather than inside abilities. That is the central
 * design decision: in the plugin this replaces, each ability called its own
 * cooldown check, and one that forgot silently had no cooldown at all. Here,
 * forgetting is not expressible.
 */
public final class AbilityDispatcher {

    private final SigilRegistries registries;
    private final ItemResolver resolver;
    private final UsesService uses;
    private final CooldownService cooldowns;
    private final SigilScheduler scheduler;
    private final MessageService messages;
    private final SigilSettings settings;
    private final Logger logger;

    public AbilityDispatcher(SigilRegistries registries, ItemResolver resolver, UsesService uses,
                             CooldownService cooldowns, SigilScheduler scheduler, MessageService messages,
                             SigilSettings settings, Logger logger) {
        this.registries = registries;
        this.resolver = resolver;
        this.uses = uses;
        this.cooldowns = cooldowns;
        this.scheduler = scheduler;
        this.messages = messages;
        this.settings = settings;
        this.logger = logger;
    }

    /**
     * @return true when some ability fired, so the caller can suppress vanilla
     *         behaviour it would otherwise allow
     */
    public boolean dispatch(Player player, ItemStack stack, Trigger trigger, TriggerBinding.Slot slot,
                            TriggerBinding.Target target, Block block, Entity entity, Event event) {
        if (stack == null || player == null) {
            return false;
        }

        Optional<SigilItem> resolved = resolver.idOf(stack).flatMap(registries::sigilItem);
        if (resolved.isEmpty()) {
            return false;
        }
        SigilItem item = resolved.get();
        if (item.abilities().isEmpty()) {
            return false;
        }

        if (!Permissions.canUse(player, item, registries)) {
            return false;
        }

        // A limited-use item kept as a husk at zero charges stops working, but
        // is not destroyed. Checking here means no ability has to.
        if (uses.isSpent(stack, item)) {
            return false;
        }

        boolean sneaking = player.isSneaking();
        boolean anyFired = false;

        for (Ability ability : item.abilities()) {
            if (!matches(ability, trigger, sneaking, target, slot)) {
                continue;
            }
            if (!Permissions.canUseAbility(player, item, ability)) {
                continue;
            }

            AbilityMeta meta = ability.meta();
            String cooldownKey = item.id() + "/" + meta.id();
            long remaining = cooldowns.remaining(player, stack, cooldownKey, meta.scope());
            if (remaining > 0) {
                notifyCooldown(player, remaining);
                continue;
            }

            CustomItemAbilityEvent pre = new CustomItemAbilityEvent(player, stack, item, ability, trigger);
            player.getServer().getPluginManager().callEvent(pre);
            if (pre.isCancelled()) {
                continue;
            }

            ActionResult result = run(ability, player, stack, item, trigger, slot, block, entity, event);
            if (result.kind() == ActionResult.Kind.PASS || result.kind() == ActionResult.Kind.FAIL) {
                // Neither starts a cooldown. Punishing a player for an ability
                // that could not find a target is the behaviour a single
                // boolean return value forces, and it is wrong.
                continue;
            }

            anyFired = true;
            long cooldownMillis = result.cooldownOverrideMillis() >= 0
                    ? result.cooldownOverrideMillis()
                    : meta.cooldownMillis();
            cooldowns.start(player, stack, cooldownKey, meta.scope(), cooldownMillis);
            applyVanillaCooldownOverlay(player, stack, cooldownMillis);

            if (result.shouldCancelEvent() && event instanceof Cancellable) {
                ((Cancellable) event).setCancelled(true);
            }
            if (result.consumesUse()) {
                uses.consume(player, stack, item);
            }
        }

        return anyFired;
    }

    private ActionResult run(Ability ability, Player player, ItemStack stack, SigilItem item, Trigger trigger,
                             TriggerBinding.Slot slot, Block block, Entity entity, Event event) {
        try {
            ContextImpl context = new ContextImpl(player, stack, item, trigger, slot, block, entity, event,
                    AbilityConfig.EMPTY, resolver.usesOf(stack), scheduler);
            ActionResult result = ability.execute(context);
            return result == null ? ActionResult.pass() : result;
        } catch (RuntimeException | LinkageError exception) {
            // One broken ability must not break the item, and certainly not the
            // event it was dispatched from.
            logger.log(Level.WARNING, "Ability '" + ability.meta().id() + "' on " + item.id() + " threw", exception);
            return ActionResult.pass();
        }
    }

    private boolean matches(Ability ability, Trigger trigger, boolean sneaking,
                            TriggerBinding.Target target, TriggerBinding.Slot slot) {
        for (TriggerBinding binding : ability.triggers()) {
            if (binding.matches(trigger, sneaking, target, slot)) {
                return true;
            }
        }
        return false;
    }

    private void notifyCooldown(Player player, long remainingMillis) {
        String seconds = String.format("%.1f", remainingMillis / 1000.0);
        if (settings.cooldownActionBar()) {
            messages.sendActionBar(player, "cooldown", MessageService.value("time", seconds));
        } else {
            messages.send(player, "cooldown", MessageService.value("time", seconds));
        }
    }

    private void applyVanillaCooldownOverlay(Player player, ItemStack stack, long millis) {
        if (!settings.vanillaCooldownOverlay() || millis <= 0) {
            return;
        }
        // The grey sweep the client already draws for ender pearls. Free,
        // correct feedback that needs no message at all.
        player.setCooldown(stack.getType(), (int) Math.min(Integer.MAX_VALUE, millis / 50));
    }
}
