package dev.bwmp.sigil.api.visual;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Shared display-entity effects supplied by Sigil on Minecraft 1.19.4+. */
public interface DisplayService {

    boolean available();

    /**
     * Creates a translucent-style shell from four block displays and attaches
     * it to the player. The returned handle removes every entity in the effect.
     */
    DisplayEffect surround(Plugin owner, Player player, PlayerDisplaySpec spec);
}
