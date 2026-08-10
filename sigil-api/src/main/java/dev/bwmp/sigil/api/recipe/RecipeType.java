package dev.bwmp.sigil.api.recipe;

/** The station a recipe belongs to. */
public enum RecipeType {

    SHAPED,
    SHAPELESS,
    FURNACE,
    BLASTING,
    SMOKING,
    CAMPFIRE,

    /**
     * Smithing transform. Requires 1.19.4+; below that the recipe is skipped
     * and reported rather than mis-registered as the older two-slot form, whose
     * semantics differ enough to be a bug factory.
     */
    SMITHING,

    STONECUTTING;

    public boolean isCooking() {
        return this == FURNACE || this == BLASTING || this == SMOKING || this == CAMPFIRE;
    }

    public boolean isCrafting() {
        return this == SHAPED || this == SHAPELESS;
    }
}
