package dev.bwmp.sigil.api.scheduler;

/** A cancellable scheduled task. */
public interface SigilTask {

    void cancel();

    boolean isCancelled();
}
