package dev.bwmp.sigil.ability.builtin;

import dev.bwmp.sigil.api.ability.Ability;
import dev.bwmp.sigil.api.ability.AbilityConfig;
import dev.bwmp.sigil.api.ability.AbilityContext;
import dev.bwmp.sigil.api.ability.AbilityMeta;
import dev.bwmp.sigil.api.ability.AbilityType;
import dev.bwmp.sigil.api.ability.ActionResult;
import dev.bwmp.sigil.api.ability.CooldownScope;
import dev.bwmp.sigil.api.ability.Trigger;
import dev.bwmp.sigil.api.ability.TriggerBinding;
import dev.bwmp.sigil.config.Parsers;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ability types usable from YAML, so an item with ordinary behaviour needs no
 * Java at all.
 * <p>
 * Each reads its parameters from {@link AbilityConfig} with a sensible default
 * for every one, so a half-written config still produces a working ability
 * rather than an error.
 */
public final class BuiltinAbilities {

    private BuiltinAbilities() {
    }

    /** Every built-in type, keyed by the id YAML refers to it by. */
    public static Map<String, AbilityType> all() {
        Map<String, AbilityType> types = new LinkedHashMap<>();
        types.put("launch", BuiltinAbilities::launch);
        types.put("projectile", BuiltinAbilities::projectile);
        types.put("potion", BuiltinAbilities::potion);
        types.put("heal", BuiltinAbilities::heal);
        types.put("area_break", BuiltinAbilities::areaBreak);
        types.put("smite", BuiltinAbilities::smite);
        types.put("worn_effect", BuiltinAbilities::wornEffect);
        return types;
    }

    // --- shared plumbing -------------------------------------------------

    /**
     * Reads the trigger, cooldown and scope every type accepts, so the set of
     * common keys is identical across all of them.
     */
    private static AbilityMeta meta(String id, String name, AbilityConfig config) {
        return AbilityMeta.of(id, name)
                .description(config.getString("description", ""))
                .cooldown(java.time.Duration.ofMillis(
                        Parsers.durationMillis(config.getString("cooldown", "0"), 0L)))
                .scope(scope(config.getString("cooldown-scope", "player")));
    }

    private static CooldownScope scope(String raw) {
        try {
            return CooldownScope.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return CooldownScope.PLAYER;
        }
    }

    private static Set<TriggerBinding> bindings(AbilityConfig config, Trigger fallback) {
        Trigger trigger = Parsers.trigger(config.getString("trigger", fallback.name())).orElse(fallback);
        TriggerBinding binding = TriggerBinding.of(trigger);

        Parsers.target(config.getString("target", "any")).ifPresent(binding::target);
        if (config.contains("sneaking")) {
            binding.sneaking(config.getBoolean("sneaking", false)
                    ? TriggerBinding.Requirement.REQUIRED
                    : TriggerBinding.Requirement.FORBIDDEN);
        }
        return Set.of(binding);
    }

    /** A type whose behaviour is one function, sharing the common key handling. */
    private abstract static class ConfiguredAbility implements Ability {
        private final AbilityMeta meta;
        private final Set<TriggerBinding> triggers;
        final AbilityConfig config;

        ConfiguredAbility(AbilityMeta meta, Set<TriggerBinding> triggers, AbilityConfig config) {
            this.meta = meta;
            this.triggers = triggers;
            this.config = config;
        }

        @Override
        public AbilityMeta meta() {
            return meta;
        }

        @Override
        public Set<TriggerBinding> triggers() {
            return triggers;
        }
    }

    // --- types -----------------------------------------------------------

    private static Ability launch(String id, String name, AbilityConfig config) {
        return new ConfiguredAbility(meta(id, name, config), bindings(config, Trigger.RIGHT_CLICK), config) {
            @Override
            public ActionResult execute(AbilityContext context) {
                Player player = context.player();
                double power = config.getDouble("power", 1.5);
                double lift = config.getDouble("lift", 0.4);

                Vector velocity = player.getLocation().getDirection().normalize().multiply(power);
                velocity.setY(velocity.getY() + lift);
                player.setVelocity(velocity);
                // Cleared so the launch itself does not become the fall that
                // kills the player on landing.
                player.setFallDistance(0f);

                sound(player.getLocation(), config, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH);
                return ActionResult.consume().cancelEvent();
            }
        };
    }

    private static Ability projectile(String id, String name, AbilityConfig config) {
        return new ConfiguredAbility(meta(id, name, config), bindings(config, Trigger.RIGHT_CLICK), config) {
            @Override
            public ActionResult execute(AbilityContext context) {
                Player player = context.player();
                Class<? extends Projectile> type = projectileType(config.getString("projectile", "SNOWBALL"));
                if (type == null) {
                    return ActionResult.fail();
                }

                Projectile launched = player.launchProjectile(type,
                        player.getLocation().getDirection().multiply(config.getDouble("speed", 1.5)));
                launched.setShooter(player);

                sound(player.getLocation(), config, Sound.ENTITY_SNOWBALL_THROW);
                return ActionResult.consume().cancelEvent();
            }
        };
    }

    private static Ability potion(String id, String name, AbilityConfig config) {
        return new ConfiguredAbility(meta(id, name, config), bindings(config, Trigger.RIGHT_CLICK), config) {
            @Override
            public ActionResult execute(AbilityContext context) {
                PotionEffectType effect = PotionEffectType.getByName(
                        config.getString("effect", "SPEED").toUpperCase(Locale.ROOT));
                if (effect == null) {
                    return ActionResult.fail();
                }

                int seconds = config.getInt("duration", 10);
                int amplifier = Math.max(0, config.getInt("amplifier", 1) - 1);

                // "self" targets the holder; anything else affects whatever was
                // hit, which only makes sense for entity triggers.
                LivingEntity target = config.getBoolean("self", true)
                        ? context.player()
                        : context.entity().filter(LivingEntity.class::isInstance)
                        .map(LivingEntity.class::cast).orElse(null);
                if (target == null) {
                    return ActionResult.fail();
                }

                target.addPotionEffect(new PotionEffect(effect, seconds * 20, amplifier));
                sound(target.getLocation(), config, Sound.ENTITY_GENERIC_DRINK);
                return ActionResult.consume();
            }
        };
    }

    private static Ability heal(String id, String name, AbilityConfig config) {
        return new ConfiguredAbility(meta(id, name, config), bindings(config, Trigger.RIGHT_CLICK), config) {
            @Override
            public ActionResult execute(AbilityContext context) {
                Player player = context.player();
                double max = player.getMaxHealth();
                if (player.getHealth() >= max) {
                    // Nothing to do, so no cooldown and no charge spent.
                    return ActionResult.fail();
                }

                player.setHealth(Math.min(max, player.getHealth() + config.getDouble("amount", 4.0)));
                player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3);
                sound(player.getLocation(), config, Sound.ENTITY_PLAYER_LEVELUP);
                return ActionResult.consume();
            }
        };
    }

    private static Ability areaBreak(String id, String name, AbilityConfig config) {
        return new ConfiguredAbility(meta(id, name, config), bindings(config, Trigger.BLOCK_BREAK), config) {
            @Override
            public ActionResult execute(AbilityContext context) {
                Block origin = context.block().orElse(null);
                if (origin == null) {
                    return ActionResult.fail();
                }

                int radius = Math.max(1, Math.min(4, config.getInt("radius", 1)));
                boolean broke = false;
                for (int x = -radius; x <= radius; x++) {
                    for (int y = -radius; y <= radius; y++) {
                        for (int z = -radius; z <= radius; z++) {
                            Block block = origin.getRelative(x, y, z);
                            if (block.equals(origin) || !isBreakable(block)) {
                                continue;
                            }
                            // Region ownership matters on Folia: a radius can
                            // cross a chunk border into another region's
                            // thread, and touching it directly would be a data
                            // race rather than a lag spike.
                            if (context.scheduler().ownsRegion(block.getLocation())) {
                                block.breakNaturally(context.stack());
                            } else {
                                context.scheduler().atLocation(block.getLocation(),
                                        () -> block.breakNaturally(context.stack()));
                            }
                            broke = true;
                        }
                    }
                }
                return broke ? ActionResult.consume() : ActionResult.fail();
            }
        };
    }

    private static Ability smite(String id, String name, AbilityConfig config) {
        return new ConfiguredAbility(meta(id, name, config), bindings(config, Trigger.DAMAGE_ENTITY), config) {
            @Override
            public ActionResult execute(AbilityContext context) {
                Entity target = context.entity().orElse(null);
                if (target == null) {
                    return ActionResult.fail();
                }

                if (config.getBoolean("lightning", true)) {
                    target.getWorld().strikeLightningEffect(target.getLocation());
                }

                double damage = config.getDouble("damage", 4.0);
                EntityDamageEvent hit = context.event(EntityDamageEvent.class).orElse(null);
                if (hit != null && hit.getEntity().equals(target)) {
                    // Added to the blow that triggered this, not dealt as a
                    // second one. Calling damage() here would re-enter the
                    // damage pipeline while the outer event is still being
                    // handled: it fires another EntityDamageByEntityEvent that
                    // dispatches this same ability again — the cooldown does
                    // not start until execute() returns — and it spends the
                    // target's invulnerability ticks, so the sword blow that
                    // caused it lands for less than it should.
                    hit.setDamage(hit.getDamage() + damage);
                } else if (target instanceof LivingEntity living) {
                    // Bound to something that is not a hit on this target — a
                    // right-click, or TAKE_DAMAGE where the target is whoever
                    // attacked. There is no blow to add to.
                    living.damage(damage, context.player());
                }
                return ActionResult.consume();
            }
        };
    }

    /**
     * Grants an effect while the item is worn and removes it when taken off.
     * <p>
     * Binds both {@link Trigger#EQUIP} and {@link Trigger#UNEQUIP}, so it is
     * also the thing that proves those two triggers actually fire. Returns
     * SUCCESS rather than CONSUME: putting armour on should not spend a charge.
     */
    private static Ability wornEffect(String id, String name, AbilityConfig config) {
        AbilityMeta meta = meta(id, name, config);
        Set<TriggerBinding> triggers = Set.of(
                TriggerBinding.of(Trigger.EQUIP).slot(TriggerBinding.Slot.ARMOUR),
                TriggerBinding.of(Trigger.UNEQUIP).slot(TriggerBinding.Slot.ARMOUR));

        return new ConfiguredAbility(meta, triggers, config) {
            @Override
            public ActionResult execute(AbilityContext context) {
                PotionEffectType effect = PotionEffectType.getByName(
                        config.getString("effect", "SPEED").toUpperCase(Locale.ROOT));
                if (effect == null) {
                    return ActionResult.fail();
                }

                Player player = context.player();
                if (context.trigger() == Trigger.UNEQUIP) {
                    player.removePotionEffect(effect);
                    return ActionResult.success();
                }

                // Effectively permanent while worn, and simply reapplied on the
                // next equip rather than tracked - the unequip branch is what
                // ends it.
                int amplifier = Math.max(0, config.getInt("amplifier", 1) - 1);
                player.addPotionEffect(new PotionEffect(effect, Integer.MAX_VALUE, amplifier, true, false));
                return ActionResult.success();
            }
        };
    }

    // --- helpers ---------------------------------------------------------

    private static boolean isBreakable(Block block) {
        Material type = block.getType();
        if (type.isAir() || type == Material.BEDROCK || type == Material.BARRIER) {
            return false;
        }
        // Never mine out a container: the drops would be a duplication vector
        // and the loss is unrecoverable for the player.
        return block.getState() instanceof org.bukkit.inventory.InventoryHolder == false;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Projectile> projectileType(String raw) {
        try {
            Class<?> type = Class.forName("org.bukkit.entity." + normalise(raw));
            return Projectile.class.isAssignableFrom(type) ? (Class<? extends Projectile>) type : null;
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static String normalise(String raw) {
        StringBuilder name = new StringBuilder();
        for (String part : raw.trim().toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return name.toString();
    }

    private static void sound(Location location, AbilityConfig config, Sound fallback) {
        String configured = config.getString("sound", null);
        Sound sound = fallback;
        if (configured != null) {
            try {
                sound = Sound.valueOf(configured.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Keep the default rather than failing the ability over a
                // cosmetic key.
            }
        }
        if (location.getWorld() != null) {
            location.getWorld().playSound(location, sound,
                    (float) config.getDouble("volume", 1.0), (float) config.getDouble("pitch", 1.0));
        }
    }
}
