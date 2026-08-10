# Changelog

Notable changes to Sigil. Versions follow [semantic versioning](https://semver.org);
`sigil-api` is the surface that version promises apply to.

## Unreleased

First release. Sigil replaces an earlier plugin wholesale rather than iterating
on it, so there is no upgrade path — items from the old plugin are not
recognised.

### Items

- One content type. A "material" is an item with no abilities, which is what
  makes a recipe whose ingredient is a custom item work without a parallel
  concept for materials.
- Items are defined in code, in YAML, or both — a YAML file naming an ability
  type needs no Java at all.
- Identity is a namespaced id in persistent data, never a hash of the display
  name, material or rarity. Editing any of those in config no longer orphans
  every existing copy.
- Stacks carry a revision stamp and re-render themselves after a config change,
  keeping their remaining uses and any data other plugins wrote.
- A player's own name for an item survives re-rendering when the item allows
  renaming.
- Configuration is parsed once into immutable records behind a volatile
  reference. Nothing re-reads a file at runtime and a reload cannot be observed
  half-applied.

### Abilities

- Abilities declare their triggers and return `PASS`, `SUCCESS`, `CONSUME` or
  `FAIL`. Cooldowns, permissions, use consumption and event cancellation are
  enforced by the dispatcher, so an ability cannot forget them.
- `FAIL` means "tried and could not", and deliberately does not start a
  cooldown — missing with a grapple should not cost you the grapple.
- Fourteen triggers including `swap_hands`, `equip`/`unequip` and `tick`.
- Cooldowns are per player, per stack or global, held in memory rather than
  written onto the ItemStack.
- Projectiles are stamped with the item that fired them, so a hit can be traced
  back to its source.

### Recipes

- Shaped, shapeless, cooking, smithing and stonecutting.
- Registered as real Bukkit recipes so the recipe book, shift-click crafting and
  ingredient consumption work natively, then validated strictly by persistent id
  — a renamed lookalike cannot satisfy a custom ingredient, and a lore change
  cannot break one.
- Two recipes may share a shape and differ only in whether an ingredient is
  custom, which vanilla registration cannot express.
- Tag ingredients (`#minecraft:planks`) resolve at load.

### Compatibility

- Minecraft 1.18 – 26.x on Spigot, Paper, Purpur, Pufferfish and Folia, from one
  jar, on Java 17 bytecode.
- Implementations are chosen by capability probe, never by version comparison.
- Unavailable features degrade per feature and are reported once at startup, so
  correct-but-degraded is never indistinguishable from broken.
- Verified running on Paper 1.20.4, Paper 1.21.1, Purpur 26.1.2 and Folia 26.2.

### Other

- Deny-only permissions: everything works out of the box, and a node revokes.
- `sigil-api` for third-party items and ability types, exposing no shaded type.
- Item browser and recipe preview menus, with chains walkable between recipes.
