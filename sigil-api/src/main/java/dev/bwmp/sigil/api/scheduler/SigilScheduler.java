package dev.bwmp.sigil.api.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.concurrent.CompletableFuture;

/**
 * Scheduling for abilities. {@code BukkitRunnable} must not be used in ability
 * code; going through this is what lets an ability work on Folia unchanged.
 * <p>
 * This deliberately mirrors Keystone's scheduler rather than exposing it.
 * Keystone is relocated into Sigil's jar, so a third party compiling against
 * {@code sigil-api} would resolve {@code dev.bwmp.keystone.KeystoneScheduler}
 * while the shipped jar contains {@code dev.bwmp.sigil.libs.keystone....} —
 * a NoClassDefFoundError naming a class that looks correct. Re-declaring the
 * handful of methods here keeps the published API stable and independent of
 * what Keystone does next.
 */
public interface SigilScheduler {

    SigilTask run(Runnable task);

    SigilTask runLater(Runnable task, long delayTicks);

    SigilTask runTimer(Runnable task, long delayTicks, long periodTicks);

    /** Runs on the thread owning {@code entity}, following it between regions. */
    SigilTask atEntity(Entity entity, Runnable task);

    SigilTask atEntityTimer(Entity entity, Runnable task, long delayTicks, long periodTicks);

    /** Runs on the thread owning the region containing {@code location}. */
    SigilTask atLocation(Location location, Runnable task);

    CompletableFuture<Boolean> teleport(Entity entity, Location target);

    /** True when the calling thread may touch blocks at {@code location}. */
    boolean ownsRegion(Location location);
}
