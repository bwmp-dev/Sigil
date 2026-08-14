package dev.bwmp.sigil.text;

import dev.bwmp.keystone.text.KeystoneText;
import dev.bwmp.keystone.text.MessageService;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Sends raw MiniMessage on behalf of code that cannot build a Component.
 * <p>
 * {@link MessageService} addresses messages by key from {@code messages.yml},
 * which is right for Sigil's own strings and useless to an add-on whose text
 * lives in its own config. This is the other half: markup in, parsed and sent.
 * <p>
 * It exists at all because Adventure is relocated into {@code
 * dev.bwmp.sigil.libs}. An add-on that built a {@code Component} would be
 * building the unrelocated one, and handing it across would fail to link.
 */
public final class Messenger {

    private final MessageService messages;

    public Messenger(MessageService messages) {
        this.messages = messages;
    }

    public void send(CommandSender target, String miniMessage) {
        if (target == null || miniMessage == null || miniMessage.isEmpty()) {
            return;
        }
        messages.sendComponent(target, parse(miniMessage));
    }

    public void sendActionBar(Player target, String miniMessage) {
        if (target == null || miniMessage == null || miniMessage.isEmpty()) {
            return;
        }
        messages.audience(target).sendActionBar(parse(miniMessage));
    }

    private Component parse(String miniMessage) {
        return KeystoneText.parse(miniMessage);
    }
}
