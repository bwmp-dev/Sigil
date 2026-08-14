package dev.bwmp.sigil.command;

import dev.bwmp.keystone.command.CommandArguments;
import dev.bwmp.keystone.command.PlatformSubcommand;
import dev.bwmp.keystone.command.RootCommand;
import dev.bwmp.keystone.command.SimpleSubcommand;
import dev.bwmp.keystone.text.KeystoneText;
import dev.bwmp.keystone.text.MessageService;
import dev.bwmp.sigil.SigilPlugin;
import dev.bwmp.sigil.api.ability.Ability;
import dev.bwmp.sigil.api.recipe.SigilRecipe;
import dev.bwmp.sigil.gui.ItemBrowserMenu;
import dev.bwmp.sigil.gui.RecipePreviewMenu;
import dev.bwmp.sigil.item.SigilItem;
import dev.bwmp.sigil.permission.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SigilCommand {

    private final SigilPlugin plugin;
    private final MessageService messages;

    public SigilCommand(SigilPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public RootCommand build() {
        RootCommand root = new RootCommand(messages, "usage");

        root.register(SimpleSubcommand.of("give", this::give)
                .permission(Permissions.COMMAND_ROOT + ".give")
                .usage("give <item> [player] [amount]")
                .description("Give a Sigil item")
                .completer(this::completeGive));

        root.register(SimpleSubcommand.of("list", this::list)
                .permission(Permissions.COMMAND_ROOT + ".list")
                .description("List every registered item"));

        root.register(SimpleSubcommand.of("info", this::info)
                .permission(Permissions.COMMAND_ROOT + ".info")
                .usage("info <item>")
                .description("Show an item's full definition")
                .completer((sender, args) -> RootCommand.matching(itemIds(), args.get(0, ""))));

        root.register(SimpleSubcommand.of("menu", context ->
                        new ItemBrowserMenu(plugin).open(context.requirePlayer()))
                .permission(Permissions.COMMAND_ROOT + ".menu")
                .requiresPlayer()
                .description("Open the item browser"));

        root.register(SimpleSubcommand.of("recipe", this::recipe)
                .permission(Permissions.COMMAND_ROOT + ".menu")
                .requiresPlayer()
                .usage("recipe <item>")
                .description("Show an item's recipe")
                .completer((sender, args) -> RootCommand.matching(itemIds(), args.get(0, ""))));

        root.register(SimpleSubcommand.of("refresh", this::refresh)
                .permission(Permissions.COMMAND_ROOT + ".refresh")
                .usage("refresh [player|all]")
                .description("Re-render items against the current config"));

        root.register(SimpleSubcommand.of("reload", context -> {
                    plugin.reloadSigil();
                    messages.send(context.sender(), "reloaded",
                            MessageService.value("count", String.valueOf(plugin.registries().sigilItems().size())));
                })
                .permission(Permissions.COMMAND_ROOT + ".reload")
                .description("Reload config, items and recipes"));

        root.register(new PlatformSubcommand(plugin.keystone(), messages,
                Permissions.COMMAND_ROOT + ".platform"));

        root.defaultTo(SimpleSubcommand.of("help", this::help).description("Show this help"));
        return root;
    }

    private void help(dev.bwmp.keystone.command.CommandContext context) {
        messages.sendComponent(context.sender(), KeystoneText.parse("<gray>--- <white>Sigil<gray> ---"));
        for (dev.bwmp.keystone.command.Subcommand subcommand : buildForHelp()) {
            if (!subcommand.permission().isBlank() && !context.sender().hasPermission(subcommand.permission())) {
                continue;
            }
            messages.sendComponent(context.sender(), KeystoneText.parse(
                    "<yellow>/sigil " + subcommand.usage() + " <dark_gray>- <gray>" + subcommand.description()));
        }
    }

    private List<dev.bwmp.keystone.command.Subcommand> buildForHelp() {
        return build().subcommands();
    }

    private void give(dev.bwmp.keystone.command.CommandContext context) {
        CommandArguments args = context.args();
        Optional<SigilItem> item = resolveItem(args.get(0, ""));
        if (item.isEmpty()) {
            messages.send(context.sender(), "unknown-item", MessageService.value("item", args.get(0, "")));
            return;
        }

        Player target = args.size() > 1
                ? Bukkit.getPlayerExact(args.get(1, ""))
                : context.player().orElse(null);
        if (target == null) {
            messages.send(context.sender(), "unknown-player", MessageService.value("player", args.get(1, "")));
            return;
        }

        int amount = Math.max(1, Math.min(64, args.integer(2).orElse(1)));
        target.getInventory().addItem(item.get().createStack(amount));

        messages.send(context.sender(), "given",
                MessageService.value("amount", String.valueOf(amount)),
                MessageService.value("item", item.get().id().toString()),
                MessageService.value("player", target.getName()));
    }

    private void list(dev.bwmp.keystone.command.CommandContext context) {
        List<SigilItem> items = plugin.registries().sigilItems();
        messages.sendComponent(context.sender(), KeystoneText.parse(
                "<gray>--- <white>Sigil items<gray> (" + items.size() + ") ---"));

        for (SigilItem item : items) {
            String colour = plugin.registries().rarityOrUnknown(item.definition().rarityId()).colour();
            String enabled = item.definition().enabled() ? "" : " <dark_gray>(disabled)";
            messages.sendComponent(context.sender(), KeystoneText.parse(
                    colour + KeystoneText.escape(item.id().toString())
                            + " <dark_gray>- <gray>" + KeystoneText.escape(stripTags(item.definition().displayName()))
                            + enabled));
        }
    }

    private void info(dev.bwmp.keystone.command.CommandContext context) {
        Optional<SigilItem> found = resolveItem(context.args().get(0, ""));
        if (found.isEmpty()) {
            messages.send(context.sender(), "unknown-item",
                    MessageService.value("item", context.args().get(0, "")));
            return;
        }

        SigilItem item = found.get();
        CommandSender sender = context.sender();
        line(sender, "<gray>--- <white>" + KeystoneText.escape(item.id().toString()) + "<gray> ---");
        line(sender, "<gray>Base: <white>" + item.definition().base());
        line(sender, "<gray>Rarity: <white>" + item.definition().rarityId());
        line(sender, "<gray>Category: <white>"
                + (item.definition().category().isEmpty() ? "-" : item.definition().category()));
        if (!item.definition().tags().isEmpty()) {
            line(sender, "<gray>Tags: <white>"
                    + KeystoneText.escape(String.join(", ", item.definition().tags())));
        }
        line(sender, "<gray>Uses: <white>" + (item.definition().uses().limited()
                ? item.definition().uses().max() + (item.definition().uses().deleteAtZero() ? " (breaks)" : "")
                : "unlimited"));
        line(sender, "<gray>Revision: <white>" + item.definition().revision());

        if (item.abilities().isEmpty()) {
            line(sender, "<gray>Abilities: <white>none <dark_gray>(this is a material)");
        } else {
            for (Ability ability : item.abilities()) {
                line(sender, "<gray>Ability <white>" + KeystoneText.escape(ability.meta().id())
                        + " <dark_gray>| <gray>triggers: <white>" + KeystoneText.escape(ability.triggers().toString())
                        + " <dark_gray>| <gray>cd: <white>" + ability.meta().cooldownMillis() + "ms");
            }
        }

        for (SigilRecipe recipe : item.definition().recipes()) {
            line(sender, "<gray>Recipe: <white>" + recipe.type() + " <dark_gray>x" + recipe.amount());
        }
    }

    private void recipe(dev.bwmp.keystone.command.CommandContext context) {
        Optional<SigilItem> item = resolveItem(context.args().get(0, ""));
        if (item.isEmpty()) {
            messages.send(context.sender(), "unknown-item",
                    MessageService.value("item", context.args().get(0, "")));
            return;
        }
        if (item.get().definition().recipes().isEmpty()) {
            messages.send(context.sender(), "no-recipe",
                    MessageService.value("item", item.get().id().toString()));
            return;
        }
        new RecipePreviewMenu(plugin, item.get(), 0).open(context.requirePlayer());
    }

    private void refresh(dev.bwmp.keystone.command.CommandContext context) {
        String target = context.args().get(0, "");
        int changed = 0;

        if (target.equalsIgnoreCase("all")) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                changed += plugin.refreshService().refreshInventory(online);
            }
        } else if (!target.isEmpty()) {
            Player player = Bukkit.getPlayerExact(target);
            if (player == null) {
                messages.send(context.sender(), "unknown-player", MessageService.value("player", target));
                return;
            }
            changed = plugin.refreshService().refreshInventory(player);
        } else {
            Optional<Player> self = context.player();
            if (self.isEmpty()) {
                messages.send(context.sender(), "player-only");
                return;
            }
            changed = plugin.refreshService().refreshInventory(self.get());
        }

        messages.send(context.sender(), "refreshed", MessageService.value("count", String.valueOf(changed)));
    }

    private List<String> completeGive(CommandSender sender, CommandArguments args) {
        if (args.size() <= 1) {
            return RootCommand.matching(itemIds(), args.get(0, ""));
        }
        if (args.size() == 2) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(player -> names.add(player.getName()));
            return RootCommand.matching(names, args.get(1, ""));
        }
        return List.of("1", "8", "16", "64");
    }

    private List<String> itemIds() {
        List<String> ids = new ArrayList<>();
        for (SigilItem item : plugin.registries().sigilItems()) {
            ids.add(item.id().toString());
            // The bare key too, so `/sigil give grapple` works without anyone
            // having to type the namespace for the common case.
            if (item.id().getNamespace().equals("sigil")) {
                ids.add(item.id().getKey());
            }
        }
        return ids;
    }

    private Optional<SigilItem> resolveItem(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.toLowerCase();
        NamespacedKey key = NamespacedKey.fromString(value.contains(":") ? value : "sigil:" + value);
        return key == null ? Optional.empty() : plugin.registries().sigilItem(key);
    }

    private void line(CommandSender sender, String miniMessage) {
        messages.sendComponent(sender, KeystoneText.parse(miniMessage));
    }

    private static String stripTags(String input) {
        return input == null ? "" : input.replaceAll("<[^>]*>", "");
    }
}
