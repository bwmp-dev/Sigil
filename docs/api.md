# Sigil API

For plugins that want to register their own items, ability types, or react to Sigil items.

```xml
<dependency>
    <groupId>dev.bwmp</groupId>
    <artifactId>sigil-api</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

```yaml
# plugin.yml
depend: [Sigil]
```

`sigil-api` depends on nothing but the Bukkit API. That is deliberate — see [Why the API looks the way it does](#why-the-api-looks-the-way-it-does).

---

## Getting the API

```java
SigilAPI api = SigilAPI.get().orElseThrow();
```

Published through Bukkit's service manager, so it unregisters cleanly and the dependency is explicit.

---

## Identifying items

```java
Optional<CustomItem> item = api.resolve(stack);
boolean isCustom = api.isCustom(stack);
int uses = api.remainingUses(stack);       // -1 when unlimited
```

`resolve` reads the persistent id and nothing else. Never identify a Sigil item by its display name or material — both are configurable and neither is identity.

---

## Writing an ability

An ability declares *when* it runs and *what* it does. It does not enforce its own cooldown, check its own permission, or decrement its own uses — the dispatcher does all three, so forgetting one is not expressible.

```java
public final class FreezeAbility implements Ability {

    @Override
    public AbilityMeta meta() {
        return AbilityMeta.of("freeze", "Freeze")
            .description("Roots your target in place.")
            .cooldownSeconds(8)
            .scope(CooldownScope.PLAYER);
    }

    @Override
    public Set<TriggerBinding> triggers() {
        return Set.of(TriggerBinding.of(Trigger.DAMAGE_ENTITY));
    }

    @Override
    public ActionResult execute(AbilityContext ctx) {
        Entity target = ctx.entity().orElse(null);
        if (!(target instanceof LivingEntity living)) {
            return ActionResult.fail();      // no cooldown for a miss
        }
        living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 4));
        return ActionResult.consume();
    }
}
```

### Return the right result

| Result | Cooldown | Use spent | Meaning |
|---|---|---|---|
| `pass()` | no | no | not interested; other abilities still get a look |
| `success()` | yes | no | fired |
| `consume()` | yes | yes | fired and spent a charge |
| `fail()` | no | no | tried and couldn't — no target, no room, nothing to do |

The `fail()` / `pass()` distinction is the point. A single boolean forces "I missed" and "I fired" into the same bucket, which is how an ability ends up on cooldown for having hit nothing.

Add `.cancelEvent()` to suppress the Bukkit event that triggered it.

### Abilities must be stateless

One instance is shared by every stack of the item. Per-player or per-item state belongs in the item's persistent data or in your own plugin.

### Talking to the player

```java
ctx.actionBar("<gray>Shield <white>" + remaining + "<gray>/" + capacity);
ctx.message("<red>Nothing to recall to.");
```

MiniMessage strings, not Components, for the reason in [Why the API looks the way it does](#why-the-api-looks-the-way-it-does). The action bar is usually the right channel — charge counts and failed lock-ons do not belong in chat history. Outside an ability, `api.send(sender, markup)` and `api.sendActionBar(player, markup)` do the same.

### Shared helpers

Two utility classes exist so that abilities agree with each other rather than each rolling their own geometry.

`Targeting` answers "what did they mean":

```java
Targeting.pointInSight(player, 20);                 // always a Location, even aimed at sky
Targeting.entityInSight(player, 30, 0.4);
Targeting.groundBelow(location, 6);                 // where a deployable lands
Targeting.inCone(origin, direction, 8, 30, Targeting.HOSTILE);
Targeting.inLine(origin, direction, 24, 1.0, Targeting.excluding(player));
Targeting.rangeToTerrain(origin, direction, 24);    // stop a beam at the wall
```

`Effects` reads the `sound`, `volume`, `pitch` and `particle` keys every type accepts, and draws the two shapes everything wants:

```java
Effects.sound(location, ctx.config(), Sound.ENTITY_GENERIC_EXPLODE);
Effects.line(from, to, Particle.CRIT, 0.5);
Effects.ring(centre, radius, Particle.FLAME, 32);
```

Use `Effects.sound` rather than `Sound.valueOf`: `Sound` stopped being an enum in 1.21.3, so `valueOf` is a `NoSuchMethodError` there rather than a catchable bad name. `Effects` goes through the string overload, which has worked unchanged since well before Sigil's 1.18.2 floor — and lets an owner name a resource-pack sound no constant covers.

### Scheduling

Use `ctx.scheduler()`, never `BukkitRunnable`. It is what makes an ability work on Folia unchanged.

```java
ctx.scheduler().atEntityTimer(player, this::step, 1L, 1L);   // follows the player's region
ctx.scheduler().atLocation(block.getLocation(), () -> block.breakNaturally());
```

If an ability touches blocks away from the player, check `ctx.scheduler().ownsRegion(location)` first and hand the work to `atLocation` when it returns false. Off Folia that check is always true, so the same code is correct on both.

---

## Registering an ability type

An `AbilityType` builds abilities from YAML, which makes it available to **every** item on the server, including ones defined purely in config.

```java
@Override
public void onEnable() {
    SigilAPI.get().ifPresent(api ->
        api.registerAbilityType(this, new NamespacedKey(this, "freeze"),
            (id, name, config) -> new FreezeAbility(config.getInt("duration", 60))));
}
```

Server owners can then write:

```yaml
abilities:
  - type: yourplugin:freeze
    duration: 100
```

`AbilityConfig` reads flat keys with a fallback for each, and nested ones for a type whose parameters have structure:

```java
config.getSection("stats").map(s -> s.getInt("damage", 4));
config.getSections("stages");     // a YAML list of maps
config.getKeys();
```

The nested accessors are `default` methods returning empty, so adding them could not break an existing implementation.

The id's namespace must match your plugin. Registrations are dropped automatically when your plugin disables, so a reload cannot leave a handler pointing at a dead classloader.

Throw `IllegalArgumentException` from `create` for a bad config — the message is shown to the admin against the offending file.

---

## Registering an item

```java
ItemDefinition definition = new ItemDefinition(
        new NamespacedKey(this, "frostblade"),
        "<aqua>Frostblade",
        Material.DIAMOND_SWORD,
        "epic",
        List.of("Cold to the touch."),
        null, -1,
        Uses.limited(200, true),
        InteractionRules.inherit(),
        List.of(),
        null, true, "");

api.register(this, definition, new FreezeAbility());
```

Your item gets its own YAML file like any other, so server owners can retune it without touching your code.

---

## Charges

```java
int uses = api.remainingUses(stack);        // -1 when unlimited
int added = api.addUses(stack, 50);         // capped at the item's max
```

`addUses` reports what it actually added, not whether it worked. That is what lets a repair kit decline to spend itself on an item that was already full — the difference between a useful item and a trap.

```java
long ms = api.cooldownRemaining(player, stack, "grapple");
```

Read-only, and it does not start a cooldown. For a menu or an action-bar countdown.

---

## Loot

```java
api.registerLoot(this, LootRule.mob(itemId, EntityType.WITHER, 0.5));
api.registerLoot(this, LootRule.chest(itemId, key("minecraft:chests/end_city_treasure"), 0.05, 1, 1));
api.registerLoot(this, LootRule.source(itemId, key("terra:valkyrie_temple"), 0.30, 1, 1));
```

So a pack ships working drops rather than a README asking the owner to paste YAML. Rules registered here sit alongside `loot.yml` instead of replacing it, and are dropped when your plugin disables. The full constructor also takes `requirePlayerKill` and `lootingBonus`; see [the loot section of the item reference](items.md#generated-loot) for what those mean.

---

## Events

```java
@EventHandler
public void onAbility(CustomItemAbilityEvent event) {
    if (inProtectedRegion(event.getPlayer())) {
        event.setCancelled(true);   // no cooldown, no charge spent
    }
}
```

`CustomItemCraftEvent` fires before a Sigil item is crafted and can replace or block the result.

---

## Why the API looks the way it does

`sigil-api` references only Bukkit types and its own. It never exposes Adventure or anything from Sigil's internals.

That is not stylistic. Sigil shades its dependencies and relocates them into `dev.bwmp.sigil.libs`. If the API exposed such a type, you would compile against `dev.bwmp.keystone.KeystoneScheduler` while the shipped jar contains `dev.bwmp.sigil.libs.keystone.KeystoneScheduler` — a `NoClassDefFoundError` at runtime naming a class that looks entirely correct.

So `SigilScheduler` is Sigil's own interface, and text crosses the boundary as MiniMessage strings rather than components. If you extend the API, keep that rule.
