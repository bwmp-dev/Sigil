package dev.bwmp.sigil.listener;

import dev.bwmp.sigil.ability.AbilityDispatcher;
import dev.bwmp.sigil.api.ability.Trigger;
import dev.bwmp.sigil.api.ability.TriggerBinding;
import dev.bwmp.sigil.item.Keys;
import dev.bwmp.sigil.item.ItemResolver;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Turns Bukkit events into ability activations.
 * <p>
 * Every trigger funnels through {@link AbilityDispatcher}, so adding one means
 * touching this class and the {@link Trigger} enum, and nothing else. The
 * design this replaces required a new method on the item base class, a new
 * branch in the dispatcher, and an override in every item.
 */
public final class TriggerListener implements Listener {

    /**
     * Whether this thread is already inside a damage dispatch.
     * <p>
     * A thread local rather than a field because Folia runs regions on separate
     * threads: a guard shared between them would have one region suppressing
     * another's abilities. Each region only needs to guard against itself.
     */
    private final ThreadLocal<Boolean> handlingDamage = new ThreadLocal<>();

    private final AbilityDispatcher dispatcher;
    private final ItemResolver resolver;
    private final Keys keys;

    public TriggerListener(AbilityDispatcher dispatcher, ItemResolver resolver, Keys keys) {
        this.dispatcher = dispatcher;
        this.resolver = resolver;
        this.keys = keys;
    }

    /**
     * <b>Deliberately not {@code ignoreCancelled = true}.</b>
     * <p>
     * {@code PlayerInteractEvent.isCancelled()} is defined as
     * {@code useInteractedBlock() == DENY}, and on an air click there is no
     * block for that to allow — so it defaults to DENY and the event reports
     * itself cancelled. With {@code ignoreCancelled} Bukkit then skips this
     * handler for every air interaction, and abilities only fire when aimed at
     * a block. That is not a grapple bug; it silently applied to every
     * click-triggered ability.
     * <p>
     * {@code useItemInHand()} is the flag that actually means "this item use
     * was suppressed", so that is what gets checked instead.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.useItemInHand() == Event.Result.DENY) {
            return;
        }

        // Only the hand actually holding the item, or a right-click fires twice
        // - once per hand - and every ability runs double.
        if (event.getHand() == null) {
            return;
        }

        ItemStack stack = event.getItem();
        if (stack == null) {
            return;
        }

        Action action = event.getAction();
        Trigger trigger = switch (action) {
            case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> Trigger.LEFT_CLICK;
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> Trigger.RIGHT_CLICK;
            default -> null;
        };
        if (trigger == null) {
            return;
        }

        TriggerBinding.Target target = (action == Action.LEFT_CLICK_BLOCK || action == Action.RIGHT_CLICK_BLOCK)
                ? TriggerBinding.Target.BLOCK
                : TriggerBinding.Target.AIR;

        dispatcher.dispatch(event.getPlayer(), stack, trigger, slotOf(event.getHand()),
                target, event.getClickedBlock(), null, event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        ItemStack stack = event.getHand() == EquipmentSlot.OFF_HAND
                ? event.getPlayer().getInventory().getItemInOffHand()
                : event.getPlayer().getInventory().getItemInMainHand();

        dispatcher.dispatch(event.getPlayer(), stack, Trigger.RIGHT_CLICK, slotOf(event.getHand()),
                TriggerBinding.Target.ENTITY, null, event.getRightClicked(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (Boolean.TRUE.equals(handlingDamage.get())) {
            // An ability dealt damage while this thread was already handling a
            // hit, and it has come back round as another event. Dispatching it
            // would run the same ability a second time — a cooldown does not
            // start until the ability returns — and an ability that damages
            // would keep re-entering until the target's invulnerability ticks
            // happened to stop it. Damage Sigil caused does not trigger Sigil.
            return;
        }

        handlingDamage.set(Boolean.TRUE);
        try {
            if (event.getDamager() instanceof Player attacker) {
                dispatcher.dispatch(attacker, attacker.getInventory().getItemInMainHand(),
                        Trigger.DAMAGE_ENTITY, TriggerBinding.Slot.MAIN_HAND,
                        TriggerBinding.Target.ENTITY, null, event.getEntity(), event);
            }

            if (event.getEntity() instanceof Player victim) {
                // Armour first, then hands: a defensive ability usually lives on
                // what is being worn.
                for (ItemStack armour : victim.getInventory().getArmorContents()) {
                    if (armour != null) {
                        dispatcher.dispatch(victim, armour, Trigger.TAKE_DAMAGE, TriggerBinding.Slot.ARMOUR,
                                TriggerBinding.Target.ENTITY, null, event.getDamager(), event);
                    }
                }
                dispatcher.dispatch(victim, victim.getInventory().getItemInMainHand(),
                        Trigger.TAKE_DAMAGE, TriggerBinding.Slot.MAIN_HAND,
                        TriggerBinding.Target.ENTITY, null, event.getDamager(), event);
            }
        } finally {
            handlingDamage.remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        dispatcher.dispatch(event.getPlayer(), event.getPlayer().getInventory().getItemInMainHand(),
                Trigger.BLOCK_BREAK, TriggerBinding.Slot.MAIN_HAND,
                TriggerBinding.Target.BLOCK, event.getBlock(), null, event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        dispatcher.dispatch(event.getPlayer(), event.getItemInHand(),
                Trigger.BLOCK_PLACE, slotOf(event.getHand()),
                TriggerBinding.Target.BLOCK, event.getBlock(), null, event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        // The item moving into the off hand is the one the player was holding,
        // which is the one they meant to activate.
        dispatcher.dispatch(event.getPlayer(), event.getOffHandItem(),
                Trigger.SWAP_HANDS, TriggerBinding.Slot.MAIN_HAND,
                TriggerBinding.Target.AIR, null, null, event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        dispatcher.dispatch(event.getPlayer(), event.getItemDrop().getItemStack(),
                Trigger.DROP, TriggerBinding.Slot.MAIN_HAND,
                TriggerBinding.Target.AIR, null, event.getItemDrop(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        dispatcher.dispatch(event.getPlayer(), event.getItem(),
                Trigger.CONSUME_ITEM, TriggerBinding.Slot.MAIN_HAND,
                TriggerBinding.Target.AIR, null, null, event);
    }

    /**
     * Stamps the firing item onto the projectile.
     * <p>
     * Without this a projectile hit cannot be traced back to the item that
     * launched it — which is why the equivalent hook in the design this
     * replaces was an empty method that never did anything.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player shooter)) {
            return;
        }
        ItemStack stack = shooter.getInventory().getItemInMainHand();
        resolver.idOf(stack).ifPresent(id -> {
            event.getEntity().getPersistentDataContainer()
                    .set(keys.projectileSource, PersistentDataType.STRING, id.toString());
            dispatcher.dispatch(shooter, stack, Trigger.PROJECTILE_LAUNCH, TriggerBinding.Slot.MAIN_HAND,
                    TriggerBinding.Target.AIR, null, event.getEntity(), event);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        String sourceId = projectile.getPersistentDataContainer()
                .get(keys.projectileSource, PersistentDataType.STRING);
        if (sourceId == null || !(projectile.getShooter() instanceof Player shooter)) {
            return;
        }

        // Resolved from the shooter's hand rather than stored, because the
        // stack is what abilities mutate and a stored copy would not persist.
        ItemStack stack = shooter.getInventory().getItemInMainHand();
        if (!resolver.idOf(stack).map(id -> id.toString().equals(sourceId)).orElse(false)) {
            return;
        }

        Entity hit = event.getHitEntity();
        dispatcher.dispatch(shooter, stack, Trigger.PROJECTILE_HIT, TriggerBinding.Slot.MAIN_HAND,
                hit != null ? TriggerBinding.Target.ENTITY : TriggerBinding.Target.BLOCK,
                event.getHitBlock(), hit, event);
    }

    private static TriggerBinding.Slot slotOf(EquipmentSlot slot) {
        return slot == EquipmentSlot.OFF_HAND ? TriggerBinding.Slot.OFF_HAND : TriggerBinding.Slot.MAIN_HAND;
    }
}
