package dev.bwmp.sigil.api.recipe;

import org.bukkit.NamespacedKey;

import java.util.List;
import java.util.Map;

/**
 * A recipe producing a Sigil item.
 *
 * @param result      id of the item produced
 * @param type        which station
 * @param amount      how many are produced
 * @param shape       up to three rows of up to three symbols; SHAPED only
 * @param keys        symbol to ingredient; SHAPED only
 * @param ingredients the inputs; SHAPELESS, cooking, stonecutting (one), and
 *                    SMITHING (base then addition, optionally preceded by a
 *                    template)
 * @param cookTime    ticks; cooking types only
 * @param experience  granted on completion; cooking types only
 */
public record SigilRecipe(
        NamespacedKey result,
        RecipeType type,
        int amount,
        List<String> shape,
        Map<Character, Ingredient> keys,
        List<Ingredient> ingredients,
        int cookTime,
        float experience) {

    public SigilRecipe {
        shape = shape == null ? List.of() : List.copyOf(shape);
        keys = keys == null ? Map.of() : Map.copyOf(keys);
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        amount = Math.max(1, amount);
    }

    public static SigilRecipe shaped(NamespacedKey result, List<String> shape,
                                     Map<Character, Ingredient> keys, int amount) {
        return new SigilRecipe(result, RecipeType.SHAPED, amount, shape, keys, List.of(), 0, 0f);
    }

    public static SigilRecipe shapeless(NamespacedKey result, List<Ingredient> ingredients, int amount) {
        return new SigilRecipe(result, RecipeType.SHAPELESS, amount, List.of(), Map.of(), ingredients, 0, 0f);
    }

    public static SigilRecipe cooking(NamespacedKey result, RecipeType type, Ingredient input,
                                      int amount, int cookTime, float experience) {
        return new SigilRecipe(result, type, amount, List.of(), Map.of(), List.of(input), cookTime, experience);
    }

    public static SigilRecipe smithing(NamespacedKey result, Ingredient template,
                                       Ingredient base, Ingredient addition) {
        List<Ingredient> parts = template == null ? List.of(base, addition) : List.of(template, base, addition);
        return new SigilRecipe(result, RecipeType.SMITHING, 1, List.of(), Map.of(), parts, 0, 0f);
    }

    public static SigilRecipe stonecutting(NamespacedKey result, Ingredient input, int amount) {
        return new SigilRecipe(result, RecipeType.STONECUTTING, amount, List.of(), Map.of(), List.of(input), 0, 0f);
    }

    /** True when a smithing recipe carries an explicit template slot. */
    public boolean hasSmithingTemplate() {
        return type == RecipeType.SMITHING && ingredients.size() == 3;
    }
}
