package dev.bwmp.sigil.visual;

import dev.bwmp.sigil.api.scheduler.SigilScheduler;
import dev.bwmp.sigil.api.visual.DisplayEffect;
import dev.bwmp.sigil.api.visual.DisplayService;
import dev.bwmp.sigil.api.visual.PlayerDisplaySpec;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Reflection keeps Sigil loadable on its supported 1.18 API floor. */
public final class DisplayEffectService implements DisplayService, Listener {

    private final Plugin plugin;
    private final SigilScheduler scheduler;
    private final DisplayReflection reflection;
    private final Map<Plugin, Set<Effect>> byOwner = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Effect>> byPlayer = new ConcurrentHashMap<>();

    public DisplayEffectService(Plugin plugin, SigilScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.reflection = DisplayReflection.load();
        if (reflection == null) {
            plugin.getLogger().info("Display effects unavailable (requires Minecraft 1.19.4 or newer).");
        }
    }

    @Override
    public boolean available() {
        return reflection != null;
    }

    @Override
    public DisplayEffect surround(Plugin owner, Player player, PlayerDisplaySpec spec) {
        if (reflection == null || owner == null || !owner.isEnabled() || !player.isValid()) {
            return DisplayEffect.NONE;
        }

        try {
            List<Entity> entities = reflection.spawnShell(player, spec);
            Effect effect = new Effect(owner, player.getUniqueId(), entities);
            byOwner.computeIfAbsent(owner, ignored -> ConcurrentHashMap.newKeySet()).add(effect);
            byPlayer.computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet()).add(effect);
            return effect;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not create display effect", exception);
            return DisplayEffect.NONE;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeAll(byPlayer.remove(event.getPlayer().getUniqueId()));
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() != plugin) {
            removeAll(byOwner.remove(event.getPlugin()));
        }
    }

    public void shutdown() {
        List<Effect> effects = byOwner.values().stream().flatMap(Set::stream).distinct().toList();
        effects.forEach(Effect::remove);
        byOwner.clear();
        byPlayer.clear();
    }

    private void removeAll(Set<Effect> effects) {
        if (effects != null) {
            List.copyOf(effects).forEach(Effect::remove);
        }
    }

    private final class Effect implements DisplayEffect {

        private final Plugin owner;
        private final UUID playerId;
        private final List<Entity> entities;
        private volatile boolean active = true;

        private Effect(Plugin owner, UUID playerId, List<Entity> entities) {
            this.owner = owner;
            this.playerId = playerId;
            this.entities = entities;
        }

        @Override
        public boolean active() {
            return active && entities.stream().anyMatch(Entity::isValid);
        }

        @Override
        public void remove() {
            if (!active) {
                return;
            }
            active = false;
            Set<Effect> ownerEffects = byOwner.getOrDefault(owner, Collections.emptySet());
            ownerEffects.remove(this);
            Set<Effect> playerEffects = byPlayer.getOrDefault(playerId, Collections.emptySet());
            playerEffects.remove(this);
            entities.forEach(entity -> scheduler.atEntity(entity, entity::remove));
        }
    }

    private record DisplayReflection(Class<? extends Entity> blockDisplayClass, Method setBlock,
                                     Method setTransformation, Constructor<?> transformation,
                                     Constructor<?> vector, Constructor<?> rotation) {

        @SuppressWarnings("unchecked")
        private static DisplayReflection load() {
            try {
                Class<? extends Entity> blockDisplay =
                        (Class<? extends Entity>) Class.forName("org.bukkit.entity.BlockDisplay");
                Class<?> display = Class.forName("org.bukkit.entity.Display");
                Class<?> transformation = Class.forName("org.bukkit.util.Transformation");
                Class<?> vector = Class.forName("org.joml.Vector3f");
                Class<?> rotation = Class.forName("org.joml.AxisAngle4f");
                return new DisplayReflection(blockDisplay,
                        blockDisplay.getMethod("setBlock", BlockData.class),
                        display.getMethod("setTransformation", transformation),
                        transformation.getConstructor(vector, rotation, vector, rotation),
                        vector.getConstructor(float.class, float.class, float.class),
                        rotation.getConstructor(float.class, float.class, float.class, float.class));
            } catch (ReflectiveOperationException exception) {
                return null;
            }
        }

        private List<Entity> spawnShell(Player player, PlayerDisplaySpec spec)
                throws ReflectiveOperationException {
            World world = player.getWorld();
            Location origin = player.getLocation();
            BlockData block = spec.material().createBlockData();
            float diameter = (float) (spec.radius() * 2.0);
            float height = (float) spec.height();
            float radius = (float) spec.radius();
            List<Entity> result = new ArrayList<>(4);
            try {
                result.add(spawnPanel(world, origin, player, block, -radius, 0.0f, 0.04f, height, diameter));
                result.add(spawnPanel(world, origin, player, block, radius, 0.0f, 0.04f, height, diameter));
                result.add(spawnPanel(world, origin, player, block, -radius, 1.0f, diameter, height, 0.04f));
                result.add(spawnPanel(world, origin, player, block, radius, 1.0f, diameter, height, 0.04f));
                return List.copyOf(result);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                result.forEach(Entity::remove);
                throw exception;
            }
        }

        private Entity spawnPanel(World world, Location origin, Player player, BlockData block,
                                  float side, float axis, float scaleX, float scaleY, float scaleZ)
                throws ReflectiveOperationException {
            Entity entity = world.spawn(origin, blockDisplayClass);
            try {
                setBlock.invoke(entity, block);
                float x = axis == 0.0f ? -scaleX / 2.0f : side - scaleX / 2.0f;
                float z = axis == 0.0f ? side - scaleZ / 2.0f : -scaleZ / 2.0f;
                if (!player.addPassenger(entity)) {
                    throw new IllegalStateException("server refused display passenger");
                }
                float passengerOffset = (float) (origin.getY() - entity.getLocation().getY());
                Object transform = transformation.newInstance(
                        vector.newInstance(x, passengerOffset, z),
                        rotation.newInstance(0.0f, 0.0f, 0.0f, 1.0f),
                        vector.newInstance(scaleX, scaleY, scaleZ),
                        rotation.newInstance(0.0f, 0.0f, 0.0f, 1.0f));
                setTransformation.invoke(entity, transform);
                return entity;
            } catch (InvocationTargetException exception) {
                entity.remove();
                throw new ReflectiveOperationException(exception.getCause());
            } catch (ReflectiveOperationException | RuntimeException exception) {
                entity.remove();
                throw exception;
            }
        }
    }
}
