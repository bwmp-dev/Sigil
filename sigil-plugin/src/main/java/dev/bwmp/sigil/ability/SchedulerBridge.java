package dev.bwmp.sigil.ability;

import dev.bwmp.keystone.scheduler.KeystoneScheduler;
import dev.bwmp.keystone.scheduler.KeystoneTask;
import dev.bwmp.sigil.api.scheduler.SigilScheduler;
import dev.bwmp.sigil.api.scheduler.SigilTask;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.concurrent.CompletableFuture;

/**
 * Presents Keystone's scheduler as Sigil's own.
 * <p>
 * This thin layer exists because Keystone is relocated into Sigil's jar. A
 * third party compiling an ability against {@code sigil-api} must not be handed
 * a {@code dev.bwmp.keystone.*} type: they would compile against that name
 * while the shipped jar contains {@code dev.bwmp.sigil.libs.keystone.*}, giving
 * a NoClassDefFoundError that names a class which looks entirely correct.
 */
public final class SchedulerBridge implements SigilScheduler {

    private final KeystoneScheduler delegate;

    public SchedulerBridge(KeystoneScheduler delegate) {
        this.delegate = delegate;
    }

    @Override
    public SigilTask run(Runnable task) {
        return wrap(delegate.run(task));
    }

    @Override
    public SigilTask runLater(Runnable task, long delayTicks) {
        return wrap(delegate.runLater(task, delayTicks));
    }

    @Override
    public SigilTask runTimer(Runnable task, long delayTicks, long periodTicks) {
        return wrap(delegate.runTimer(task, delayTicks, periodTicks));
    }

    @Override
    public SigilTask atEntity(Entity entity, Runnable task) {
        return wrap(delegate.atEntity(entity, task));
    }

    @Override
    public SigilTask atEntityTimer(Entity entity, Runnable task, long delayTicks, long periodTicks) {
        return wrap(delegate.atEntityTimer(entity, task, delayTicks, periodTicks));
    }

    @Override
    public SigilTask atLocation(Location location, Runnable task) {
        return wrap(delegate.atLocation(location, task));
    }

    @Override
    public CompletableFuture<Boolean> teleport(Entity entity, Location target) {
        return delegate.teleport(entity, target);
    }

    @Override
    public boolean ownsRegion(Location location) {
        return delegate.ownsRegion(location);
    }

    private static SigilTask wrap(KeystoneTask task) {
        return new SigilTask() {
            @Override
            public void cancel() {
                task.cancel();
            }

            @Override
            public boolean isCancelled() {
                return task.isCancelled();
            }
        };
    }
}
