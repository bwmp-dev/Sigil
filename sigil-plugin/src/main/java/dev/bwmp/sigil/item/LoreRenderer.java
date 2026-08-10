package dev.bwmp.sigil.item;

import dev.bwmp.keystone.text.KeystoneText;
import dev.bwmp.keystone.text.LegacyRenderer;
import dev.bwmp.sigil.api.ability.Ability;
import dev.bwmp.sigil.api.ability.AbilityMeta;
import dev.bwmp.sigil.api.item.ItemDefinition;
import dev.bwmp.sigil.api.item.Rarity;
import dev.bwmp.sigil.config.SigilSettings;
import dev.bwmp.sigil.registry.SigilRegistries;
import net.kyori.adventure.text.Component;

import dev.bwmp.sigil.api.ability.Trigger;
import dev.bwmp.sigil.api.ability.TriggerBinding;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Builds an item's display name and lore from a configurable template.
 * <p>
 * The template lives in config so layout is a server decision. Sections
 * collapse when empty, which the design this replaces did not do — it emitted
 * blank separator lines unconditionally, so an item with no description got a
 * gap where the description would have been.
 */
public final class LoreRenderer {

    private final SigilRegistries registries;
    private final SigilSettings settings;

    public LoreRenderer(SigilRegistries registries, SigilSettings settings) {
        this.registries = registries;
        this.settings = settings;
    }

    public String renderName(ItemDefinition definition) {
        Rarity rarity = registries.rarityOrUnknown(definition.rarityId());
        String name = definition.displayName();
        // A name with no colour of its own inherits its rarity's, so an item
        // never renders as plain white by omission.
        String coloured = name.startsWith("<") ? name : rarity.colour() + name;
        return LegacyRenderer.renderMiniMessage(coloured);
    }

    public List<String> renderLore(ItemDefinition definition, List<Ability> abilities, int remainingUses) {
        List<Component> lines = new ArrayList<>();

        for (String token : settings.loreTemplate()) {
            switch (token.trim()) {
                case "<description>" -> appendMiniMessage(lines, definition.description());
                case "<abilities>" -> appendAbilities(lines, abilities);
                case "<uses>" -> appendUses(lines, definition, remainingUses);
                case "<rarity>" -> lines.add(KeystoneText.parse(
                        registries.rarityOrUnknown(definition.rarityId()).colour() + "<bold>"
                                + registries.rarityOrUnknown(definition.rarityId()).displayName()));
                case "" -> {
                    // A blank template entry is a separator, but only if it
                    // would not double up or lead. Collapsing here is what
                    // keeps sections tidy when one of them renders to nothing.
                    if (!lines.isEmpty() && !lines.get(lines.size() - 1).equals(Component.empty())) {
                        lines.add(Component.empty());
                    }
                }
                default -> lines.add(KeystoneText.parse(token));
            }
        }

        while (!lines.isEmpty() && lines.get(lines.size() - 1).equals(Component.empty())) {
            lines.remove(lines.size() - 1);
        }
        return LegacyRenderer.renderAll(lines);
    }

    private void appendMiniMessage(List<Component> lines, List<String> raw) {
        for (String line : raw) {
            lines.add(KeystoneText.parse("<gray>" + line));
        }
    }

    private void appendAbilities(List<Component> lines, List<Ability> abilities) {
        for (Ability ability : abilities) {
            AbilityMeta meta = ability.meta();
            if (!meta.showInLore()) {
                continue;
            }

            String triggers = describeTriggers(ability);
            lines.add(KeystoneText.parse("<gold>Ability: <yellow>" + KeystoneText.escape(meta.name())
                    + (triggers.isEmpty() ? "" : " <aqua><bold>" + triggers)));
            if (!meta.description().isEmpty()) {
                lines.add(KeystoneText.parse("<gray>" + meta.description()));
            }
            if (meta.cooldownMillis() > 0) {
                lines.add(KeystoneText.parse("<dark_gray>Cooldown: <gray>"
                        + formatSeconds(meta.cooldownMillis()) + "s"));
            }
        }
    }

    private void appendUses(List<Component> lines, ItemDefinition definition, int remainingUses) {
        if (!definition.uses().limited()) {
            return;
        }
        int shown = remainingUses >= 0 ? remainingUses : definition.uses().max();
        lines.add(KeystoneText.parse("<dark_gray>Uses: <white>" + shown + "<dark_gray>/" + definition.uses().max()));
    }

    /**
     * Renders an ability's triggers the way a player should read them.
     * <p>
     * Two things happen here beyond formatting. Bindings are grouped by trigger
     * and sorted by the enum's own order, because {@code Set.of()} has no
     * defined iteration order — it is deliberately randomised per JVM run, so
     * without this the lore could list triggers differently after a restart.
     * <p>
     * And a trigger bound both sneaking and not-sneaking collapses to the plain
     * trigger, since together they cover every case. That pair is what produced
     * "right_click (sneaking), right_click (not sneaking)" — technically true,
     * and useless to read.
     */
    private String describeTriggers(Ability ability) {
        Map<Trigger, EnumSet<TriggerBinding.Requirement>> byTrigger = new EnumMap<>(Trigger.class);
        for (TriggerBinding binding : ability.triggers()) {
            byTrigger.computeIfAbsent(binding.trigger(),
                            ignored -> EnumSet.noneOf(TriggerBinding.Requirement.class))
                    .add(binding.sneakingRequirement());
        }

        List<String> labels = new ArrayList<>();
        for (Map.Entry<Trigger, EnumSet<TriggerBinding.Requirement>> entry : byTrigger.entrySet()) {
            EnumSet<TriggerBinding.Requirement> sneaking = entry.getValue();
            boolean coversBoth = sneaking.contains(TriggerBinding.Requirement.ANY)
                    || (sneaking.contains(TriggerBinding.Requirement.REQUIRED)
                    && sneaking.contains(TriggerBinding.Requirement.FORBIDDEN));

            String label = !coversBoth && sneaking.contains(TriggerBinding.Requirement.REQUIRED)
                    ? "Sneak + " + entry.getKey().displayName()
                    : entry.getKey().displayName();

            if (!labels.contains(label)) {
                labels.add(label);
            }
        }
        return String.join(", ", labels);
    }

    private static String formatSeconds(long millis) {
        double seconds = millis / 1000.0;
        return seconds == Math.floor(seconds)
                ? String.valueOf((long) seconds)
                : String.format("%.1f", seconds);
    }
}
