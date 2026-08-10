# Item YAML reference

Every `.yml` under `plugins/Sigil/items/<namespace>/` defines one item. The filename is the id, the folder is the namespace: `items/aether/mythril.yml` is `aether:mythril`.

Two kinds of file end up here. One **overrides** an item registered in code — the code supplies defaults and behaviour, the file retunes it. The other **defines** an item outright, naming ability types instead of writing Java. They use the same keys; a file simply supplies whatever it wants to change.

Run `/sigil reload` to apply changes. Items already in inventories update themselves.

---

## Top level

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | `false` removes the item from the registry entirely |
| `display` | the id | MiniMessage. If it has no colour of its own it inherits the rarity's |
| `base` | *required* | the vanilla material it is built from, e.g. `IRON_INGOT` |
| `rarity` | `common` | must match a key under `rarities` in config.yml |
| `description` | none | list of MiniMessage lines |
| `category` | none | free-form grouping. `material` marks it as a crafting material |
| `model` | none | named item model key (1.21.4+) |
| `custom-model-data` | `-1` | integer fallback for older servers |
| `permission` | auto | overrides the default `sigil.item.<ns>.<id>` node |

```yaml
display: "<gradient:#00b0ff:#9b00ff>Grappling Hook</gradient>"
base: TRIPWIRE_HOOK
rarity: rare
description:
  - "A handy device for reaching high places."
```

---

## Uses

```yaml
uses:
  type: limited      # or: infinite
  max: 250
  delete-at-zero: true
```

`delete-at-zero: false` keeps the item as a spent husk that no longer activates.

Limited-use items are given a unique per-stack id so two never merge and share one counter. That works on every supported version — unlike forcing a max stack size, which only exists from 1.20.5.

---

## Interaction rules

Each key is optional. Omitting one means "follow the server default in config.yml", which is not the same as setting it — an item that never expressed an opinion keeps following the config if it later changes.

```yaml
rules:
  vanilla-recipes: false   # can this be an ingredient in ordinary vanilla recipes?
  anvil-rename: false      # can players rename it?
  anvil-combine: true      # can two of them merge their remaining charges?
  enchanting: false        # can it be enchanted at a table?
```

When `anvil-rename` is allowed, a name a player gives an item survives every later re-render — spending a charge, merging charges, a grindstone, a `/sigil reload` that changed the item's name in config. Sigil stamps the name it wrote onto the stack and only overwrites a display name that still matches that stamp, so a name it did not write is left alone. Items that forbid renaming keep the old behaviour and are reset to the configured name, which is the point of the rule rather than a side effect of it.

One gap: stacks rendered by a version before this stamp existed have nothing to compare against, so the first re-render of an old stack still resets its name. It is honoured from then on.

Grindstones always preserve a custom item's identity, lore and charges while still removing player-applied enchantments. That is not configurable, because the alternative is handing the player something that looks right and is silently broken.

---

## Abilities

Each entry names a registered ability type and configures it.

```yaml
abilities:
  - type: sigil:projectile
    id: firebolt
    name: "Firebolt"
    description: "Launch a small fireball."
    trigger: right_click
    cooldown: 2s
    projectile: SMALL_FIREBALL
    speed: 1.6
```

### Keys every type accepts

| Key | Default | Meaning |
|---|---|---|
| `id` | the type's name | stable, unique within the item; used for the cooldown key and permission node, so renaming it resets both |
| `name` | the id | shown in lore |
| `description` | none | shown in lore |
| `trigger` | per type | see below |
| `cooldown` | `0` | `2s`, `500ms`, `1m`, or a plain number of seconds |
| `cooldown-scope` | `player` | `player`, `stack` or `global` |
| `sneaking` | any | `true` requires sneaking, `false` forbids it |
| `target` | `any` | `air`, `block`, `entity` or `any` |

`cooldown-scope: player` is almost always right. It survives the item being dropped, re-picked-up or creative-cloned, none of which should refund an ability.

### Triggers

`left_click`, `right_click`, `block_break`, `block_place`, `damage_entity`, `take_damage`, `projectile_launch`, `projectile_hit`, `swap_hands`, `drop`, `consume_item`, `equip`, `unequip`, `tick`

`swap_hands` is the F key — the conventional "activate" input for ability items. `equip`/`unequip` fire for armour. `tick` is polled, and the polling loop does not run at all unless some item actually uses it.

### Built-in types

| Type | Does | Notable keys |
|---|---|---|
| `sigil:launch` | throws the holder in their look direction | `power`, `lift` |
| `sigil:projectile` | fires a projectile | `projectile`, `speed` |
| `sigil:potion` | applies an effect | `effect`, `duration`, `amplifier`, `self` |
| `sigil:heal` | restores health | `amount` |
| `sigil:area_break` | breaks blocks around the one mined | `radius` (1–4) |
| `sigil:smite` | lightning and damage on the target | `damage`, `lightning` |
| `sigil:worn_effect` | grants an effect while worn, removes it when taken off | `effect`, `amplifier` |

All of them also accept `sound`, `volume` and `pitch`.

---

## Recipes

One recipe under `recipe:`, or several under `recipes:` as a list.

Ingredients can be written four ways:

```yaml
IRON_INGOT                    # a vanilla material
sigil:aether_ingot            # a custom item, matched by id
{ item: IRON_INGOT, amount: 2 }
[ IRON_INGOT, GOLD_INGOT ]    # any one of these
```

A custom item never satisfies a vanilla slot — an Aether Ingot will not be accepted where an iron ingot is asked for.

### Shaped

```yaml
recipe:
  type: shaped
  amount: 1
  shape:
    - " SI"
    - " SL"
    - "I  "
  keys:
    S: STRING
    L: LEAD
    I: sigil:aether_ingot
```

A space is an empty slot. The pattern may sit anywhere in the grid, as in vanilla.

### Shapeless

```yaml
recipe:
  type: shapeless
  ingredients:
    - sigil:aether_block
    - { item: STICK, amount: 2 }
```

Here `amount` means "this many slots of it", since each slot contributes one item per craft.

### Cooking

`furnace`, `blasting`, `smoking`, `campfire`.

```yaml
recipe:
  type: furnace
  input: sigil:raw_aether
  cook-time: 200
  experience: 0.7
```

Furnaces are the one rough edge: vanilla matches the input by material alone, so a plain iron ingot will *start* cooking in a recipe meant for an Aether Ingot and then produce nothing when Sigil rejects it. Fuel is still consumed.

### Smithing

Requires 1.19.4+. Below that the recipe is skipped and reported at startup rather than silently mis-registered.

```yaml
recipe:
  type: smithing
  base: DIAMOND_SWORD
  addition: sigil:aether_ingot
  template: NETHERITE_UPGRADE_SMITHING_TEMPLATE   # optional
```

### Stonecutting

```yaml
recipe:
  type: stonecutting
  input: sigil:aether_block
  amount: 4
```

---

## When something is wrong

Sigil accumulates problems rather than stopping at the first, so one bad file reports everything wrong with it in a single startup block naming the file and the reason. Three categories:

- **error** — that item or recipe will not work
- **warn** — it loaded, but not as written
- **gate** — the server version cannot do what was asked, and what happened instead

A `gate` line is not a mistake. It is Sigil telling you a smithing recipe was skipped on 1.19.2, or an item model fell back to custom model data, so that correct-but-degraded behaviour is never indistinguishable from a bug.

---

## Generated chest loot

`plugins/Sigil/loot.yml` adds registered items to loot-table chests when their
normal loot is generated. Each entry rolls independently:

```yaml
entries:
  - table: minecraft:chests/end_city_treasure
    item: skyward:wayfinder_sigil
    chance: 0.025
    min: 1
    max: 1
```

`chance` is a value greater than `0` and at most `1`. Amounts default to one
and are capped to the item's real stack size; limited-use items always generate
as a single item.

An entry may select a `table`, a Terra `source`, or both. The bundled Aether
pack marks its generated chests with sources such as `terra:mob_room` and
`terra:valkyrie_temple`, allowing structure-specific rewards even when several
structures use the same vanilla loot table:

```yaml
entries:
  - source: terra:valkyrie_temple
    item: sigil:aether_ingot
    chance: 0.30
    min: 1
    max: 2
```

When both selectors are present, both must match. `/sigil reload` reloads this
file. Loot is added only when Minecraft rolls an ungenerated chest for the
first time; already-opened chests are not changed.
