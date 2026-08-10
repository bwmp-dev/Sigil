package dev.bwmp.sigil.api.item;

/**
 * How an item behaves in vanilla mechanics that would otherwise strip or
 * launder its identity.
 * <p>
 * Every field is a {@link Boolean} rather than a {@code boolean}: null means
 * "inherit the server-wide default". That distinction matters because an item
 * that has never expressed an opinion should follow the config, while one that
 * explicitly says {@code false} should keep saying false even if the default
 * changes later.
 */
public record InteractionRules(
        Boolean vanillaRecipes,
        Boolean anvilRename,
        Boolean anvilCombine,
        Boolean enchanting) {

    /** Inherit everything. */
    public static InteractionRules inherit() {
        return new InteractionRules(null, null, null, null);
    }

    public boolean vanillaRecipes(boolean fallback) {
        return vanillaRecipes == null ? fallback : vanillaRecipes;
    }

    public boolean anvilRename(boolean fallback) {
        return anvilRename == null ? fallback : anvilRename;
    }

    public boolean anvilCombine(boolean fallback) {
        return anvilCombine == null ? fallback : anvilCombine;
    }

    public boolean enchanting(boolean fallback) {
        return enchanting == null ? fallback : enchanting;
    }
}
