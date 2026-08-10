package dev.bwmp.sigil.api.item;

/**
 * A tier of item, defined entirely in config rather than hardcoded.
 *
 * @param id          lowercase key, e.g. {@code legendary}
 * @param displayName shown in lore
 * @param colour      MiniMessage colour tag, e.g. {@code <#ffbe39>}
 * @param order       sort position in menus and listings; low first
 * @param glow        whether items of this rarity get an enchantment shimmer
 */
public record Rarity(String id, String displayName, String colour, int order, boolean glow) {

    /**
     * Used when a definition names a rarity that does not exist, so one typo in
     * one file cannot stop a server booting.
     */
    public static Rarity unknown(String id) {
        return new Rarity(id, id, "<gray>", Integer.MAX_VALUE, false);
    }

    /** The rarity name wrapped in its own colour, ready to parse. */
    public String formatted() {
        return colour + displayName;
    }
}
