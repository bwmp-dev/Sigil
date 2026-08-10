package dev.bwmp.sigil.content;

import dev.bwmp.sigil.api.ability.Ability;
import dev.bwmp.sigil.api.ability.AbilityContext;
import dev.bwmp.sigil.api.ability.AbilityMeta;
import dev.bwmp.sigil.api.ability.ActionResult;
import dev.bwmp.sigil.api.ability.CooldownScope;
import dev.bwmp.sigil.api.ability.Trigger;
import dev.bwmp.sigil.api.ability.TriggerBinding;
import dev.bwmp.sigil.api.scheduler.SigilTask;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Set;

/**
 * Pulls the player toward whatever they are looking at.
 * <p>
 * The reference example for a coded ability: it ray-traces, fails cleanly when
 * there is nothing to grab, and drives a repeating pull through the scheduler
 * rather than a {@code BukkitRunnable} — which is the whole reason it works
 * unchanged on Folia.
 */
public final class GrappleAbility implements Ability {

    private static final double MAX_DISTANCE = 30.0;

    @Override
    public AbilityMeta meta() {
        return AbilityMeta.of("grapple", "Grapple")
                .description("Fire a hook and pull yourself to where you are looking.")
                .cooldownSeconds(1.5)
                .scope(CooldownScope.PLAYER);
    }

    @Override
    public Set<TriggerBinding> triggers() {
        return Set.of(TriggerBinding.of(Trigger.RIGHT_CLICK).target(TriggerBinding.Target.ANY));
    }

    @Override
    public ActionResult execute(AbilityContext context) {
        Player player = context.player();
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();

        Vector position = trace(player, eye, direction);
        if (position == null) {
            // Nothing in range. FAIL rather than SUCCESS so the player is not
            // put on cooldown for aiming at open sky - which on a sky island is
            // most directions. The miss is audible so it does not read as the
            // item being broken.
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.6f, 0.7f);
            return ActionResult.fail();
        }

        Location anchor = new Location(player.getWorld(), position.getX(), position.getY(), position.getZ());

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 0.8f, 1.3f);
        startPull(context, player, anchor);
        return ActionResult.consume().cancelEvent();
    }

    /**
     * Finds something to hook, blocks first and then entities.
     * <p>
     * Entities matter more here than they look: on a sky island the only thing
     * within range in a given direction is often a mob or another player, and a
     * grapple that ignored them would feel broken rather than restrained.
     */
    private Vector trace(Player player, Location eye, Vector direction) {
        RayTraceResult blocks = player.getWorld().rayTraceBlocks(eye, direction, MAX_DISTANCE);
        if (blocks != null && blocks.getHitPosition() != null) {
            return blocks.getHitPosition();
        }

        RayTraceResult entities = player.getWorld().rayTraceEntities(
                eye, direction, MAX_DISTANCE, 0.4, entity -> !entity.equals(player));
        if (entities != null && entities.getHitEntity() != null) {
            return entities.getHitEntity().getLocation().toVector();
        }
        return null;
    }

    private void startPull(AbilityContext context, Player player, Location anchor) {
        // Held in a one-element array so the task can cancel itself; the
        // reference is only assigned after atEntityTimer returns.
        final SigilTask[] task = new SigilTask[1];
        final int[] ticks = {0};

        task[0] = context.scheduler().atEntityTimer(player, () -> {
            if (!player.isOnline() || player.isDead() || ticks[0]++ > 40) {
                cancel(task[0]);
                return;
            }

            Location current = player.getLocation();
            Vector toAnchor = anchor.toVector().subtract(current.toVector());
            double distance = toAnchor.length();

            boolean grounded = current.clone().subtract(0, 0.2, 0).getBlock().getType().isSolid();
            if (distance < 1.5 || (grounded && distance < 2.5)) {
                cancel(task[0]);
                return;
            }

            Vector velocity = toAnchor.normalize().multiply(Math.max(1.0, Math.min(2.6, distance * 0.4)));
            // A little extra lift, scaled to how far above the player the
            // anchor is, so grappling up a wall clears the lip instead of
            // sliding into it.
            velocity.setY(velocity.getY() + Math.max(0.1, Math.min(0.9, 0.2 + (anchor.getY() - current.getY()) * 0.1)));

            player.setFallDistance(0f);
            player.setVelocity(velocity);
            player.getWorld().spawnParticle(Particle.CRIT, anchor, 2, 0.1, 0.1, 0.1, 0.0);
        }, 1L, 1L);
    }

    private static void cancel(SigilTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }
}
