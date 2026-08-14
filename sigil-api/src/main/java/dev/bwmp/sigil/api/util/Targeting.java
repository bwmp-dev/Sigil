package dev.bwmp.sigil.api.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Finding what an ability is aimed at.
 * <p>
 * Every ability that is not "affect the holder" starts by answering some
 * version of "what did they mean", and each one answering it slightly
 * differently is how a beam and a cone end up disagreeing about whether they
 * hit the same creeper. These are the shared answers.
 * <p>
 * Bukkit types only, deliberately — see the note in {@code SigilScheduler}
 * about why nothing relocated may appear in this module.
 */
public final class Targeting {

    /**
     * Entities that should not be targeted regardless of what an ability asks
     * for: dead ones, spectators, and things with no health to take.
     */
    public static final Predicate<Entity> TARGETABLE = entity ->
            entity instanceof LivingEntity living
                    && !living.isDead()
                    && living.isValid()
                    && !(entity instanceof Player player && player.getGameMode() == org.bukkit.GameMode.SPECTATOR);

    /**
     * Hostile mobs, for "reveal nearby threats" and sentry acquisition.
     * <p>
     * {@code Monster} rather than an enumerated list: it is the interface the
     * server itself uses for hostility, so a mob added in a later version is
     * covered without this needing to know it exists.
     */
    public static final Predicate<Entity> HOSTILE = entity -> entity instanceof Monster;

    private Targeting() {
    }

    /** Everything but {@code self}, combined with any further condition. */
    public static Predicate<Entity> excluding(Entity self, Predicate<Entity> and) {
        return entity -> !entity.equals(self) && TARGETABLE.test(entity) && and.test(entity);
    }

    public static Predicate<Entity> excluding(Entity self) {
        return excluding(self, entity -> true);
    }

    /**
     * The block the player is looking at, within {@code range}.
     */
    public static Optional<Block> blockInSight(Player player, double range) {
        RayTraceResult hit = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(), player.getEyeLocation().getDirection(), range);
        return hit == null ? Optional.empty() : Optional.ofNullable(hit.getHitBlock());
    }

    /**
     * The living entity the player is looking at, within {@code range}.
     *
     * @param tolerance how far off-centre the ray may pass and still count;
     *                  0.3 or so, since a ray that must hit the hitbox exactly
     *                  makes a beam feel broken at distance
     */
    public static Optional<LivingEntity> entityInSight(Player player, double range, double tolerance) {
        RayTraceResult hit = player.getWorld().rayTraceEntities(
                player.getEyeLocation(), player.getEyeLocation().getDirection(), range, tolerance,
                excluding(player));
        if (hit == null || !(hit.getHitEntity() instanceof LivingEntity living)) {
            return Optional.empty();
        }
        return Optional.of(living);
    }

    /**
     * Where the player is aiming: the surface they are looking at, or the far
     * end of the ray when they are looking at open sky.
     * <p>
     * Always present, which is what an ability that <em>places</em> something
     * wants — a deployable aimed at nothing should land somewhere in front of
     * the player rather than refuse to deploy.
     */
    public static Location pointInSight(Player player, double range) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();

        RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, direction, range);
        if (hit != null && hit.getHitPosition() != null) {
            Vector position = hit.getHitPosition();
            return new Location(player.getWorld(), position.getX(), position.getY(), position.getZ());
        }
        return eye.clone().add(direction.multiply(range));
    }

    /**
     * The first solid surface below {@code from}, for landing a deployable.
     * Empty when there is nothing within {@code maxDrop} blocks — over a void
     * or a deep shaft, where dropping the item would simply lose it.
     */
    public static Optional<Location> groundBelow(Location from, int maxDrop) {
        World world = from.getWorld();
        if (world == null) {
            return Optional.empty();
        }
        Location probe = from.clone();
        for (int step = 0; step < maxDrop; step++) {
            Block block = probe.getBlock();
            if (block.getType().isSolid()) {
                return Optional.of(block.getLocation().add(0.5, 1.0, 0.5));
            }
            probe.subtract(0, 1, 0);
            if (probe.getY() < world.getMinHeight()) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** Living entities within {@code radius} of {@code centre}, nearest first. */
    public static List<LivingEntity> inRadius(Location centre, double radius, Predicate<Entity> filter) {
        World world = centre.getWorld();
        if (world == null) {
            return List.of();
        }
        List<LivingEntity> found = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(centre, radius, radius, radius)) {
            if (entity instanceof LivingEntity living && TARGETABLE.test(entity) && filter.test(entity)
                    && entity.getLocation().distanceSquared(centre) <= radius * radius) {
                found.add(living);
            }
        }
        found.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(centre)));
        return found;
    }

    /** The nearest living entity within {@code radius}, if any. */
    public static Optional<LivingEntity> nearest(Location centre, double radius, Predicate<Entity> filter) {
        List<LivingEntity> found = inRadius(centre, radius, filter);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /**
     * Living entities inside a cone opening from {@code origin} along
     * {@code direction}, nearest first.
     *
     * @param angleDegrees half-angle of the cone; 30 gives a 60-degree sweep
     */
    public static List<LivingEntity> inCone(Location origin, Vector direction, double range,
                                            double angleDegrees, Predicate<Entity> filter) {
        Vector facing = direction.clone().normalize();
        double minimumDot = Math.cos(Math.toRadians(Math.max(1.0, Math.min(180.0, angleDegrees))));

        List<LivingEntity> found = new ArrayList<>();
        for (LivingEntity candidate : inRadius(origin, range, filter)) {
            Vector toTarget = candidate.getLocation().add(0, candidate.getHeight() / 2, 0)
                    .toVector().subtract(origin.toVector());
            if (toTarget.lengthSquared() < 1.0e-6) {
                // Standing inside the origin: always in the cone, and
                // normalising a zero vector would produce NaN.
                found.add(candidate);
                continue;
            }
            if (toTarget.normalize().dot(facing) >= minimumDot) {
                found.add(candidate);
            }
        }
        return found;
    }

    /**
     * Living entities along a straight line from {@code origin}, nearest
     * first — a beam or a piercing shot.
     *
     * @param width how far from the centre line still counts
     */
    public static List<LivingEntity> inLine(Location origin, Vector direction, double range,
                                            double width, Predicate<Entity> filter) {
        Vector facing = direction.clone().normalize();
        List<LivingEntity> found = new ArrayList<>();

        for (LivingEntity candidate : inRadius(origin, range, filter)) {
            Vector toTarget = candidate.getLocation().add(0, candidate.getHeight() / 2, 0)
                    .toVector().subtract(origin.toVector());
            double along = toTarget.dot(facing);
            if (along < 0 || along > range) {
                continue;
            }
            // Perpendicular distance from the line, via the projection onto it.
            double offset = toTarget.clone().subtract(facing.clone().multiply(along)).length();
            if (offset <= width) {
                found.add(candidate);
            }
        }
        return found;
    }

    /**
     * How far a beam from {@code origin} travels before hitting terrain, so a
     * line attack stops at the wall the player is shooting into rather than
     * through it.
     */
    public static double rangeToTerrain(Location origin, Vector direction, double maxRange) {
        World world = origin.getWorld();
        if (world == null) {
            return maxRange;
        }
        RayTraceResult hit = world.rayTraceBlocks(origin, direction.clone().normalize(), maxRange);
        if (hit == null || hit.getHitPosition() == null) {
            return maxRange;
        }
        return hit.getHitPosition().distance(origin.toVector());
    }
}
