package dev.bwmp.sigil.api.ability;

/**
 * What an ability did, and what the dispatcher should do about it.
 * <p>
 * The four outcomes exist because a single boolean conflates things that need
 * to differ. "I wasn't interested" and "I tried and couldn't" must not start a
 * cooldown; "I fired" and "I fired and spent a charge" differ in whether a use
 * is consumed. Collapsing those is how an ability ends up on cooldown for
 * having missed.
 */
public final class ActionResult {

    public enum Kind {
        /** Not interested. No cooldown, no use consumed, other abilities still run. */
        PASS,
        /** Fired. Cooldown applies, no use consumed. */
        SUCCESS,
        /** Fired and spent a charge. Cooldown applies. */
        CONSUME,
        /** Attempted and failed - no target, no room, nothing to do. No cooldown. */
        FAIL
    }

    private static final ActionResult PASS = new ActionResult(Kind.PASS, false, -1L);

    private final Kind kind;
    private final boolean cancelEvent;
    private final long cooldownMillis;

    private ActionResult(Kind kind, boolean cancelEvent, long cooldownMillis) {
        this.kind = kind;
        this.cancelEvent = cancelEvent;
        this.cooldownMillis = cooldownMillis;
    }

    public static ActionResult pass() {
        return PASS;
    }

    public static ActionResult success() {
        return new ActionResult(Kind.SUCCESS, false, -1L);
    }

    public static ActionResult consume() {
        return new ActionResult(Kind.CONSUME, false, -1L);
    }

    public static ActionResult fail() {
        return new ActionResult(Kind.FAIL, false, -1L);
    }

    /** Also cancels the Bukkit event that triggered this, e.g. to suppress block placement. */
    public ActionResult cancelEvent() {
        return new ActionResult(kind, true, cooldownMillis);
    }

    /** Overrides the ability's declared cooldown for this one activation. */
    public ActionResult withCooldown(long millis) {
        return new ActionResult(kind, cancelEvent, Math.max(0L, millis));
    }

    public Kind kind() {
        return kind;
    }

    public boolean shouldCancelEvent() {
        return cancelEvent;
    }

    /** True when the ability actually did something. */
    public boolean fired() {
        return kind == Kind.SUCCESS || kind == Kind.CONSUME;
    }

    public boolean consumesUse() {
        return kind == Kind.CONSUME;
    }

    /** Negative means "use the ability's declared cooldown". */
    public long cooldownOverrideMillis() {
        return cooldownMillis;
    }
}
