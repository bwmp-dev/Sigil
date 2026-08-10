package dev.bwmp.sigil.api.ability;

import java.time.Duration;

/** An ability's identity, description and cooldown policy. */
public final class AbilityMeta {

    private final String id;
    private final String name;
    private String description = "";
    private Duration cooldown = Duration.ZERO;
    private CooldownScope scope = CooldownScope.PLAYER;
    private boolean showInLore = true;

    private AbilityMeta(String id, String name) {
        this.id = id;
        this.name = name;
    }

    /**
     * @param id   stable, lowercase, unique within its item; used for cooldown
     *             keys and the permission node, so renaming it resets both
     * @param name shown in lore
     */
    public static AbilityMeta of(String id, String name) {
        return new AbilityMeta(id, name);
    }

    public AbilityMeta description(String description) {
        this.description = description == null ? "" : description;
        return this;
    }

    public AbilityMeta cooldown(Duration cooldown) {
        this.cooldown = cooldown == null ? Duration.ZERO : cooldown;
        return this;
    }

    public AbilityMeta cooldownSeconds(double seconds) {
        return cooldown(Duration.ofMillis((long) (seconds * 1000)));
    }

    public AbilityMeta scope(CooldownScope scope) {
        this.scope = scope;
        return this;
    }

    /** Hides this ability from the item's lore, for passive or internal effects. */
    public AbilityMeta hidden() {
        this.showInLore = false;
        return this;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Duration cooldown() {
        return cooldown;
    }

    public long cooldownMillis() {
        return cooldown.toMillis();
    }

    public CooldownScope scope() {
        return scope;
    }

    public boolean showInLore() {
        return showInLore;
    }
}
