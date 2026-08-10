package dev.bwmp.sigil.api.ability;

/**
 * A trigger plus the conditions under which it counts.
 * <p>
 * Declarative rather than procedural: the dispatcher filters on these before
 * calling the ability, so an ability body never begins with a stack of guard
 * clauses re-deriving whether it should have been called.
 */
public final class TriggerBinding {

    /** Whether a condition must hold, must not hold, or is irrelevant. */
    public enum Requirement {
        REQUIRED, FORBIDDEN, ANY;

        public boolean matches(boolean actual) {
            return this == ANY || (this == REQUIRED) == actual;
        }
    }

    /** What the interaction was aimed at. */
    public enum Target {
        AIR, BLOCK, ENTITY, ANY;

        public boolean matches(Target actual) {
            return this == ANY || this == actual;
        }
    }

    /** Where the item was when the trigger fired. */
    public enum Slot {
        MAIN_HAND, OFF_HAND, ARMOUR, ANY;

        public boolean matches(Slot actual) {
            if (this == ANY) {
                return true;
            }
            return this == actual;
        }
    }

    private final Trigger trigger;
    private Requirement sneaking = Requirement.ANY;
    private Target target = Target.ANY;
    private Slot slot = Slot.ANY;

    private TriggerBinding(Trigger trigger) {
        this.trigger = trigger;
    }

    public static TriggerBinding of(Trigger trigger) {
        return new TriggerBinding(trigger);
    }

    public TriggerBinding sneaking(Requirement requirement) {
        this.sneaking = requirement;
        return this;
    }

    /** Shorthand for {@code sneaking(REQUIRED)}. */
    public TriggerBinding sneaking() {
        return sneaking(Requirement.REQUIRED);
    }

    /** Shorthand for {@code sneaking(FORBIDDEN)}. */
    public TriggerBinding notSneaking() {
        return sneaking(Requirement.FORBIDDEN);
    }

    public TriggerBinding target(Target target) {
        this.target = target;
        return this;
    }

    public TriggerBinding slot(Slot slot) {
        this.slot = slot;
        return this;
    }

    public Trigger trigger() {
        return trigger;
    }

    public boolean matches(Trigger firedTrigger, boolean isSneaking, Target actualTarget, Slot actualSlot) {
        return trigger == firedTrigger
                && sneaking.matches(isSneaking)
                && target.matches(actualTarget)
                && slot.matches(actualSlot);
    }

    public Requirement sneakingRequirement() {
        return sneaking;
    }

    public Target targetRequirement() {
        return target;
    }

    /**
     * How this binding reads in item lore.
     * <p>
     * Only the sneak modifier survives, because it changes what the player has
     * to press. The target is deliberately dropped: "at block" is an
     * implementation detail that tells a player nothing they can act on, and it
     * made tooltips longer than the ability descriptions.
     */
    public String label() {
        if (sneaking == Requirement.REQUIRED) {
            return "Sneak + " + trigger.displayName();
        }
        return trigger.displayName();
    }

    /** Full detail, for {@code /sigil info} and logs rather than for players. */
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder(trigger.name().toLowerCase());
        if (sneaking != Requirement.ANY) {
            text.append(sneaking == Requirement.REQUIRED ? " (sneaking)" : " (not sneaking)");
        }
        if (target != Target.ANY) {
            text.append(" at ").append(target.name().toLowerCase());
        }
        return text.toString();
    }
}
