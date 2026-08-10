# Sigil

Custom items, materials, abilities and recipes for Minecraft servers.

Runs on **Minecraft 1.18 – 26.x**, on Spigot, Paper, Purpur, Pufferfish and Folia, from one jar. Java 17+.

Sigil has one content type. What other plugins call a "material" is just an item with no abilities — which is what makes "a recipe whose ingredient is a custom item" work without a second parallel concept.

---

## Quick start

Drop `Sigil.jar` into `plugins/` and start the server. It writes a file per item under `plugins/Sigil/items/<namespace>/`, and never overwrites one once it exists.

```
/sigil menu                 browse every item, click one to see its recipe
/sigil give <item> [player] [amount]
/sigil list [rarity]
/sigil info <item>          abilities, triggers, cooldowns, rules, recipes
/sigil reload               re-read config, items and recipes
/sigil platform             what Sigil thinks it is running on
```

`/sigil platform` is the first thing to include in a bug report. It prints the detected server, the compatibility tier chosen, and which scheduler is in use.

---

## Making an item without writing code

Any `.yml` under `items/<namespace>/` is an item. The filename is its id and the folder is its namespace, so `items/aether/mythril.yml` is `aether:mythril`.

```yaml
display: "<#7fd4ff>Mythril Ingot"
base: IRON_INGOT
rarity: uncommon
description:
  - "Refined under pressure."

recipe:
  type: shaped
  shape: [ "NNN", "NNN", "NNN" ]
  keys:
    N: IRON_NUGGET
```

That is a complete custom material. Copy it, rename it, and you have another.

To give it behaviour, name a registered ability type:

```yaml
abilities:
  - type: sigil:projectile
    id: firebolt
    name: "Firebolt"
    trigger: right_click
    cooldown: 2s
    projectile: SMALL_FIREBALL
    speed: 1.6
```

See [`docs/items.md`](docs/items.md) for every key, and `items/sigil/ember_wand.yml` for a worked example that ships with the plugin — it has no Java behind it at all.

---

## What makes it different

**Config is read once.** Definitions are parsed at startup into immutable records and published behind a single volatile reference. Nothing re-reads a file at runtime, and a reload can never be observed half-applied.

**Items already in inventories update themselves.** Every stack carries a revision stamp. Edit an item's name or lore, run `/sigil reload`, and existing copies re-render the next time a player looks at them — keeping their remaining uses and any data other plugins wrote.

**A re-render will not take a player's name off an item.** Sigil stamps the name it wrote, and only overwrites a display name that still matches it. That is what lets an item be both renameable and limited-use: without it, the anvil label a player gave a charged item would vanish the first time they spent a charge, because spending one re-renders the stack.

**Abilities cannot forget their own plumbing.** Cooldowns, permissions, use consumption and event cancellation are enforced by the dispatcher, not by each ability. An ability returns `PASS`, `SUCCESS`, `CONSUME` or `FAIL`, and the distinction matters: `FAIL` means "tried and couldn't", so missing with a grapple does not put it on cooldown.

**Recipes are matched by identity, not by appearance.** A custom ingredient is matched on its persistent id, so a renamed lookalike cannot satisfy it and a lore change cannot break it. Two recipes can share a shape and differ only in whether an ingredient is custom — something vanilla recipe registration cannot express.

**Permissions are deny-only.** Everything works out of the box. `-sigil.item.aether.mythril` in LuckPerms denies exactly that item; `sigil.ability.<ns>.<id>.<ability>` denies one ability while leaving the rest of the item usable.

---

## Compatibility

Sigil picks an implementation per server by probing for the capability, never by comparing version strings — forks misreport versions, and a probe tests the thing that actually matters.

| Feature | From | Below that |
|---|---|---|
| Named item models | 1.21.4 | falls back to custom model data |
| Max stack size | 1.20.5 | not needed; unique stack data prevents stacking anyway |
| Smithing transform recipes | 1.19.4 | recipe skipped and reported at startup |
| Grindstone protection | Paper | absent, reported at startup |

Anything unavailable is downgraded silently *per feature* and reported once in a startup block, so nothing is quietly broken without being named.

**Folia is supported.** All scheduling goes through an abstraction with a region-threaded backend; there is no `BukkitRunnable` or `Bukkit.getScheduler()` anywhere in the plugin.

---

## For developers

Sigil publishes `sigil-api` for registering items and ability types from your own plugin. See [`docs/api.md`](docs/api.md).

```java
SigilAPI.get().ifPresent(api ->
    api.registerAbilityType(this, new NamespacedKey(this, "freeze"), new FreezeAbilityType()));
```

---

## Building

Sigil shades [Keystone](https://github.com/bwmp-dev/Keystone) into its jar. Keystone is published, so `mvn install` is all you need:

```
mvn install
```

`keystone-parent` is Sigil's Maven parent, and Maven resolves a parent *before* it reads the project's own `<repositories>` — so the Nexus repository has to come from your `~/.m2/settings.xml`:

```xml
<profiles>
  <profile>
    <id>bwmp-nexus</id>
    <repositories>
      <repository>
        <id>bwmp-nexus</id>
        <url>https://nexus.bwmp.dev/repository/maven-public/</url>
      </repository>
    </repositories>
  </profile>
</profiles>
<activeProfiles><activeProfile>bwmp-nexus</activeProfile></activeProfiles>
```

Building Keystone from source and `mvn install`-ing it locally also works, and is the way to test an unreleased change:

```
git clone https://github.com/bwmp-dev/Keystone && cd Keystone && mvn install
```

The result is `sigil-plugin/target/Sigil-<version>.jar`. There is no second plugin for server owners to install — the framework is inside the jar.

---

## Licence

[LGPL-3.0](LICENSE). In practice:

- **Running it, and writing addons against `sigil-api`, carries no obligations.** A plugin that merely depends on the API is not a derivative work, so Skyward-style addons can be licensed however you like, closed source included. That is why LGPL was chosen over GPL.
- **Modifying Sigil itself** means publishing those modifications under the same licence.

`LICENSE` is the LGPL and `COPYING` is the GPL, which the LGPL builds on by reference — both are needed for the licence to be complete.
