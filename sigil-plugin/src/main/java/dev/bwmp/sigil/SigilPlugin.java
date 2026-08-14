package dev.bwmp.sigil;

import dev.bwmp.keystone.Keystone;
import dev.bwmp.keystone.KeystoneHandle;
import dev.bwmp.keystone.config.LoadReport;
import dev.bwmp.keystone.config.ManagedConfig;
import dev.bwmp.keystone.registry.OwnedRegistry;
import dev.bwmp.keystone.text.MessageService;
import dev.bwmp.sigil.ability.AbilityDispatcher;
import dev.bwmp.sigil.ability.CooldownService;
import dev.bwmp.sigil.ability.SchedulerBridge;
import dev.bwmp.sigil.ability.PlayerWatchService;
import dev.bwmp.sigil.ability.builtin.BuiltinAbilities;
import dev.bwmp.sigil.api.SigilAPI;
import dev.bwmp.sigil.api.ability.Ability;
import dev.bwmp.sigil.api.ability.AbilityType;
import dev.bwmp.sigil.api.item.ItemDefinition;
import dev.bwmp.sigil.api.item.Rarity;
import dev.bwmp.sigil.command.SigilCommand;
import dev.bwmp.sigil.config.DefinitionLoader;
import dev.bwmp.sigil.config.DefinitionWriter;
import dev.bwmp.sigil.config.SigilSettings;
import dev.bwmp.sigil.content.BuiltinContent;
import dev.bwmp.sigil.data.PlayerStores;
import dev.bwmp.sigil.item.ItemFactory;
import dev.bwmp.sigil.item.ItemResolver;
import dev.bwmp.sigil.item.Keys;
import dev.bwmp.sigil.item.LoreRenderer;
import dev.bwmp.sigil.item.RefreshService;
import dev.bwmp.sigil.item.SigilItem;
import dev.bwmp.sigil.item.UsesService;
import dev.bwmp.sigil.listener.CraftingListener;
import dev.bwmp.sigil.listener.GrindstoneGuard;
import dev.bwmp.sigil.listener.ProtectionListener;
import dev.bwmp.sigil.listener.RefreshListener;
import dev.bwmp.sigil.listener.TriggerListener;
import dev.bwmp.sigil.loot.LootService;
import dev.bwmp.sigil.metrics.SigilMetrics;
import dev.bwmp.sigil.permission.Permissions;
import dev.bwmp.sigil.recipe.RecipeService;
import dev.bwmp.sigil.registry.RegistrySnapshot;
import dev.bwmp.sigil.registry.SigilRegistries;
import dev.bwmp.sigil.text.Messenger;
import dev.bwmp.sigil.visual.DisplayEffectService;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SigilPlugin extends JavaPlugin {

    private KeystoneHandle keystone;
    private SigilRegistries registries;
    private SigilSettings settings;
    private ManagedConfig mainConfig;
    private MessageService messages;
    private Messenger messenger;

    private Keys keys;
    private ItemResolver resolver;
    private ItemFactory factory;
    private UsesService uses;
    private RefreshService refreshService;
    private CooldownService cooldowns;
    private RecipeService recipes;
    private PlayerWatchService playerWatch;
    private AbilityDispatcher dispatcher;
    private SchedulerBridge scheduler;
    private LootService lootService;
    private PlayerStores playerStores;
    private DisplayEffectService displays;

    /**
     * Items registered through the API by other plugins.
     * <p>
     * Kept separate from built-in content because {@link #loadContent()}
     * rebuilds the built-ins from scratch every time. Sharing one map meant a
     * rebuild wiped every addon's registrations — including the rebuild the
     * registration itself triggered, so an addon's items never appeared at all.
     */
    private final Map<NamespacedKey, ItemDefinition> apiDefinitions = new LinkedHashMap<>();
    private final Map<NamespacedKey, List<Ability>> apiAbilities = new LinkedHashMap<>();
    private final List<Listener> listeners = new ArrayList<>();
    private boolean rebuildScheduled;

    @Override
    public void onEnable() {
        keystone = Keystone.bootstrap(this);

        mainConfig = new ManagedConfig(this, "config.yml");
        messages = keystone.messages();
        messenger = new Messenger(messages);
        settings = SigilSettings.load(mainConfig);

        OwnedRegistry<AbilityType> abilityTypes = keystone.registry("ability type");
        registries = new SigilRegistries(abilityTypes);
        scheduler = new SchedulerBridge(keystone.scheduler());
        displays = new DisplayEffectService(this, scheduler);

        playerStores = new PlayerStores(getDataFolder(), getLogger());
        keys = new Keys(this);
        resolver = new ItemResolver(keys, registries);
        factory = new ItemFactory(keys, keystone.items(),
                new LoreRenderer(registries, settings), registries, resolver, settings);
        uses = new UsesService(keys, factory, resolver);
        refreshService = new RefreshService(resolver, factory, registries);
        cooldowns = new CooldownService(resolver);
        recipes = new RecipeService(this, resolver);

        dispatcher = new AbilityDispatcher(registries, resolver, uses, cooldowns,
                scheduler, messages, messenger, settings, getLogger());

        registerBuiltinAbilityTypes();
        loadContent();
        lootService = new LootService(this, registries);
        lootService.reload();
        registerListeners();
        getServer().getPluginManager().registerEvents(displays, this);
        registerCommand();

        getServer().getServicesManager().register(SigilAPI.class, new SigilApiImpl(this),
                this, ServicePriority.Normal);

        // Expired cooldowns are never removed by the normal path, so without a
        // sweep the map grows for the lifetime of the server.
        keystone.scheduler().runTimer(cooldowns::purgeExpired, 20L * 60, 20L * 60);

        // Player data is written on a timer rather than on every change: a mana
        // pool ticking down changes several times a second, and saving each one
        // would be a file write per tick per add-on. Stores with nothing to
        // write skip themselves.
        keystone.scheduler().runTimer(playerStores::saveAll, 20L * 60, 20L * 60);

        // Last, so every sampler it registers has something to read. Shutdown
        // is wired to the Keystone handle, so there is nothing to undo.
        SigilMetrics.start(this);
    }

    @Override
    public void onDisable() {
        if (playerWatch != null) {
            playerWatch.stop();
        }
        if (displays != null) {
            displays.shutdown();
        }
        if (playerStores != null) {
            // Before the service is unregistered, so an add-on shutting down
            // after this still writes into a store that will be flushed.
            playerStores.saveAll();
        }
        if (recipes != null) {
            recipes.unregisterAll();
        }
        getServer().getServicesManager().unregisterAll(this);
        if (keystone != null) {
            keystone.shutdown();
        }
    }

    private void registerBuiltinAbilityTypes() {
        BuiltinAbilities.all().forEach((name, type) ->
                registries.abilityTypes().registerInternal(new NamespacedKey(this, name), type));
    }

    /** Loads settings, definitions and recipes, and publishes a new snapshot. */
    private void loadContent() {
        LoadReport report = new LoadReport();

        File itemsDirectory = new File(getDataFolder(), "items");
        //noinspection ResultOfMethodCallIgnored
        itemsDirectory.mkdirs();

        // Built locally each time, so nothing an addon registered is destroyed
        // by a rebuild. Addon entries are applied last, which also lets an
        // addon deliberately replace a built-in item by reusing its id.
        Map<NamespacedKey, ItemDefinition> definitions = new LinkedHashMap<>();
        Map<NamespacedKey, List<Ability>> abilities = new LinkedHashMap<>();

        if (settings.builtinContent()) {
            for (BuiltinContent.Entry entry : BuiltinContent.all()) {
                definitions.put(entry.definition().id(), entry.definition());
                abilities.put(entry.definition().id(), entry.abilities());
            }
        }
        definitions.putAll(apiDefinitions);
        abilities.putAll(apiAbilities);

        // Rarities must exist before definitions are parsed, since parsing
        // warns about unknown ones.
        publishRarities();

        DefinitionWriter writer = new DefinitionWriter(itemsDirectory, getLogger());
        definitions.values().forEach(writer::writeIfAbsent);

        // Guarded rather than relying on saveResource's own no-overwrite flag,
        // which still logs a warning every load once the file exists.
        if (!new File(itemsDirectory, "sigil/ember_wand.yml").exists()) {
            saveResource("items/sigil/ember_wand.yml", false);
        }

        DefinitionLoader loader = new DefinitionLoader(itemsDirectory, registries);
        Map<NamespacedKey, DefinitionLoader.Parsed> parsed =
                loader.loadAll(definitions, abilities, report);

        Map<NamespacedKey, SigilItem> items = new LinkedHashMap<>();
        parsed.forEach((id, entry) -> {
            if (!entry.definition().enabled()) {
                return;
            }
            items.put(id, new SigilItem(entry.definition(), entry.abilities(), factory));
        });

        registries.publish(new RegistrySnapshot(items, settings.rarities()));

        recipes.registerAll(registries.current(), report);
        Permissions.registerAll(registries);

        if (playerWatch != null) {
            playerWatch.stop();
        }
        playerWatch = new PlayerWatchService(this, registries, dispatcher, keystone.scheduler(), settings.tickIntervalTicks());
        playerWatch.start();

        report.print(getLogger(), "Sigil items");
    }

    private void publishRarities() {
        Map<String, Rarity> rarities = settings.rarities();
        registries.publish(new RegistrySnapshot(Map.of(), rarities));
    }

    private void registerListeners() {
        listeners.add(new TriggerListener(dispatcher, resolver, keys));
        listeners.add(new CraftingListener(recipes, resolver, registries, settings));
        listeners.add(new ProtectionListener(resolver, registries, settings, uses, factory));
        listeners.add(new RefreshListener(refreshService));
        listeners.add(lootService);
        listeners.forEach(listener -> getServer().getPluginManager().registerEvents(listener, this));

        // Registered separately: its event postdates the API floor, so it is
        // probed for and wired up only where it exists.
        if (!new GrindstoneGuard(resolver, registries, factory).register(this)) {
            getLogger().info("Grindstone protection unavailable on this server version; skipped.");
        }
    }

    private void registerCommand() {
        new SigilCommand(this, messages).build().bind(this, "sigil");
    }

    /**
     * Rebuilds the registry once, however many registrations arrive first.
     * <p>
     * Addons register their items one at a time during their own enable. Doing
     * a full rebuild per item is wasted work that grows with the number of
     * addons; deferring to the next tick means they all land in one pass.
     * <p>
     * The item is therefore not in the registry when {@code register} returns,
     * which is why that method hands back a transient view of the definition.
     */
    public synchronized void scheduleRebuild() {
        if (rebuildScheduled) {
            return;
        }
        rebuildScheduled = true;
        keystone.scheduler().run(() -> {
            synchronized (SigilPlugin.this) {
                rebuildScheduled = false;
            }
            loadContent();
            getLogger().info("Registry rebuilt: " + registries.sigilItems().size() + " item(s).");
        });
    }

    public void reloadSigil() {
        mainConfig.reload();
        messages.reload();
        settings = SigilSettings.load(mainConfig);

        // Rebuilt because it captures the settings object, which has just been
        // replaced; leaving the old one would render lore from stale config.
        factory = new ItemFactory(keys, keystone.items(),
                new LoreRenderer(registries, settings), registries, resolver, settings);
        uses = new UsesService(keys, factory, resolver);
        refreshService = new RefreshService(resolver, factory, registries);
        dispatcher = new AbilityDispatcher(registries, resolver, uses, cooldowns,
                scheduler, messages, messenger, settings, getLogger());

        loadContent();
        lootService.reload();
    }

    public KeystoneHandle keystone() {
        return keystone;
    }

    public SigilRegistries registries() {
        return registries;
    }

    public SigilSettings settings() {
        return settings;
    }

    public ItemResolver resolver() {
        return resolver;
    }

    public ItemFactory factory() {
        return factory;
    }

    public RefreshService refreshService() {
        return refreshService;
    }

    public RecipeService recipes() {
        return recipes;
    }

    public Map<NamespacedKey, ItemDefinition> apiDefinitions() {
        return apiDefinitions;
    }

    public Map<NamespacedKey, List<Ability>> apiAbilities() {
        return apiAbilities;
    }

    public UsesService uses() {
        return uses;
    }

    public CooldownService cooldowns() {
        return cooldowns;
    }

    public LootService loot() {
        return lootService;
    }

    public Messenger messenger() {
        return messenger;
    }

    public PlayerStores playerStores() {
        return playerStores;
    }

    public SchedulerBridge scheduler() {
        return scheduler;
    }

    public DisplayEffectService displays() {
        return displays;
    }
}
