# Contributing

## Commit messages decide the version

Releases are automated with [release-please](https://github.com/googleapis/release-please). It reads [Conventional Commits](https://www.conventionalcommits.org) from the default branch and keeps a release PR open with the next version and a generated changelog. **Merging that PR is what cuts a release** — nothing ships until you do.

This means the version number is a consequence of how you write commit messages, not something anyone edits by hand.

| Prefix | Effect | Use for |
|---|---|---|
| `fix:` | patch — `1.2.3` → `1.2.4` | bug fixes |
| `feat:` | minor — `1.2.3` → `1.3.0` | new behaviour |
| `feat!:` or a `BREAKING CHANGE:` footer | major, once past 1.0 | anything that breaks `sigil-api` or a config format |
| `docs:` `chore:` `refactor:` `test:` `ci:` | no release | everything else |

```
feat: add tag ingredients to recipes

Recipes can now reference #minecraft:planks. Tags resolve to a
fixed material list at load, so what a recipe accepts cannot drift.
```

While the version is below `1.0.0`, breaking changes bump the minor rather than the major, which is standard for pre-release semver.

To force a specific version — a first `1.0.0`, say — add a footer to any commit:

```
Release-As: 1.0.0
```

## What counts as breaking

`sigil-api` is the surface the version promises apply to. Changing it breaks other people's plugins, so treat it as public API.

Config formats matter too. A key that server owners already have in their `items/` files cannot be renamed without a migration path, because Sigil never overwrites a file once it exists — their old key would simply stop doing anything, silently.

## Versions in poms are generated

Every version release-please owns is annotated:

```xml
<version>0.1.0</version> <!-- x-release-please-version -->
```

Do not edit those by hand. Equally, **do not add that annotation to a version that is not ours** — `sigil-plugin/pom.xml` references `keystone-parent`, and bumping that would point Sigil at a Keystone release that does not exist. Only versions belonging to this repository carry the marker.

## Before opening a PR

```
mvn install    # in Keystone first, then here
```

CI builds on every PR. If you touched anything to do with shading, relocation or the compatibility tiers, say which server versions you tested on — the matrix runs from 1.18 to 26.x across Spigot, Paper, Purpur and Folia, and a change can be correct on one and wrong on another.

`/sigil platform` reports which tier and scheduler a server actually selected, which is usually the fastest way to tell.
