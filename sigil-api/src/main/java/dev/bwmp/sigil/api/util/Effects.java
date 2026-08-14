package dev.bwmp.sigil.api.util;

import dev.bwmp.sigil.api.ability.AbilityConfig;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.Locale;

/**
 * The sound and particle keys every ability type accepts, read one way.
 * <p>
 * Types that each parse {@code sound} slightly differently is how one item ends
 * up honouring {@code pitch} and its neighbour silently ignoring it. Reading
 * them here means the documented common keys are common by construction.
 */
public final class Effects {

    private Effects() {
    }

    /**
     * Plays the configured sound, or {@code fallback} when none is set.
     * <p>
     * A configured sound is passed to the {@code String} overload of
     * {@code playSound} rather than resolved through {@code Sound.valueOf}.
     * That is not a style preference: {@code Sound} stopped being an enum in
     * 1.21.3, so {@code valueOf} is a {@code NoSuchMethodError} there rather
     * than a catchable bad-name. The string overload has existed unchanged
     * since long before Sigil's 1.18.2 floor and works on every version in
     * between, which also means a server owner can name a resource-pack sound
     * that no {@code Sound} constant covers.
     */
    public static void sound(Location location, AbilityConfig config, Sound fallback) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        float volume = (float) config.getDouble("volume", 1.0);
        float pitch = (float) config.getDouble("pitch", 1.0);

        String configured = config.getString("sound", null);
        if (configured == null || configured.isBlank()) {
            if (fallback != null) {
                world.playSound(location, fallback, volume, pitch);
            }
            return;
        }
        world.playSound(location, soundKey(configured), volume, pitch);
    }

    /**
     * Turns {@code ENTITY_WARDEN_SONIC_BOOM} or {@code entity.warden.sonic_boom}
     * into the dotted key the server expects. Accepting the enum-style spelling
     * matters because it is what every existing config and every wiki page uses.
     */
    public static String soundKey(String raw) {
        String trimmed = raw.trim();
        if (trimmed.indexOf('.') >= 0) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        return trimmed.toLowerCase(Locale.ROOT).replace('_', '.');
    }

    /**
     * Spawns the configured particle, or {@code fallback} when none is set.
     * An unknown name falls back rather than failing the ability, since a
     * cosmetic key is never worth taking the behaviour down for.
     */
    public static void particles(Location location, AbilityConfig config, Particle fallback,
                                 int count, double spread) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        Particle particle = particle(config.getString("particle", null), fallback);
        if (particle == null) {
            return;
        }
        world.spawnParticle(particle, location, Math.max(1, config.getInt("particle-count", count)),
                spread, spread, spread, 0.0);
    }

    /**
     * Resolves a particle by name, keeping {@code fallback} when it does not
     * exist on this server. Names move between versions — {@code REDSTONE}
     * became {@code DUST} in 1.20.5 — so this has to tolerate a miss.
     */
    public static Particle particle(String raw, Particle fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Particle.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    /**
     * Draws a particle line between two points — a beam, a tether, the path a
     * projectile took.
     *
     * @param step spacing in blocks; 0.5 reads as solid without flooding the
     *             client with packets over a long range
     */
    public static void line(Location from, Location to, Particle particle, double step) {
        World world = from.getWorld();
        if (world == null || !world.equals(to.getWorld())) {
            return;
        }
        Vector delta = to.toVector().subtract(from.toVector());
        double distance = delta.length();
        if (distance < 1.0e-6) {
            return;
        }

        Vector increment = delta.normalize().multiply(Math.max(0.05, step));
        Location point = from.clone();
        for (double travelled = 0; travelled < distance; travelled += increment.length()) {
            world.spawnParticle(particle, point, 1, 0, 0, 0, 0);
            point.add(increment);
        }
    }

    /**
     * Draws a horizontal particle ring, for a deployable's radius or an area
     * effect's edge — the one piece of feedback that makes "am I inside it"
     * answerable at a glance.
     */
    public static void ring(Location centre, double radius, Particle particle, int points) {
        World world = centre.getWorld();
        if (world == null) {
            return;
        }
        int steps = Math.max(8, points);
        for (int index = 0; index < steps; index++) {
            double angle = 2 * Math.PI * index / steps;
            world.spawnParticle(particle, centre.clone().add(
                    Math.cos(angle) * radius, 0, Math.sin(angle) * radius), 1, 0, 0, 0, 0);
        }
    }
}
