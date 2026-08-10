package dev.bwmp.sigil.api.recipe;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;

import java.util.List;
import java.util.Locale;

/**
 * One slot's requirement in a recipe.
 * <p>
 * A custom ingredient is matched by the persistent id on the stack, never by
 * its display name or material, so a renamed lookalike cannot satisfy it and a
 * lore change cannot break it.
 */
public abstract class Ingredient {

    private final int amount;

    private Ingredient(int amount) {
        this.amount = Math.max(1, amount);
    }

    public int amount() {
        return amount;
    }

    /** The material a Bukkit recipe should register for this slot. */
    public abstract Material exemplarMaterial();

    /** Human-readable, for menus and error messages. */
    public abstract String describe();

    public static Ingredient vanilla(Material material) {
        return vanilla(material, 1);
    }

    public static Ingredient vanilla(Material material, int amount) {
        return new Vanilla(material, amount);
    }

    /** A Sigil item or material, matched by id. */
    public static Ingredient custom(NamespacedKey id, Material exemplar, int amount) {
        return new Custom(id, exemplar, amount);
    }

    public static Ingredient anyOf(List<Ingredient> options) {
        return new AnyOf(options);
    }

    /**
     * Any material in a vanilla tag, e.g. {@code #minecraft:planks}.
     * <p>
     * Resolved to a concrete material list at load rather than held as a live
     * tag, so the set a recipe accepts cannot change under it at runtime and
     * the recipe preview menu has something to draw.
     */
    public static Ingredient tag(String tagName, List<Material> materials, int amount) {
        return new Tag(tagName, materials, amount);
    }

    public static final class Vanilla extends Ingredient {
        private final Material material;

        private Vanilla(Material material, int amount) {
            super(amount);
            this.material = material;
        }

        public Material material() {
            return material;
        }

        @Override
        public Material exemplarMaterial() {
            return material;
        }

        @Override
        public String describe() {
            return material.name().toLowerCase(Locale.ROOT) + (amount() > 1 ? " x" + amount() : "");
        }
    }

    public static final class Custom extends Ingredient {
        private final NamespacedKey id;
        private final Material exemplar;

        private Custom(NamespacedKey id, Material exemplar, int amount) {
            super(amount);
            this.id = id;
            this.exemplar = exemplar;
        }

        public NamespacedKey id() {
            return id;
        }

        @Override
        public Material exemplarMaterial() {
            return exemplar;
        }

        @Override
        public String describe() {
            return id + (amount() > 1 ? " x" + amount() : "");
        }
    }

    public static final class Tag extends Ingredient {
        private final String tagName;
        private final List<Material> materials;

        private Tag(String tagName, List<Material> materials, int amount) {
            super(amount);
            this.tagName = tagName;
            this.materials = List.copyOf(materials);
        }

        public String tagName() {
            return tagName;
        }

        public List<Material> materials() {
            return materials;
        }

        public boolean contains(Material material) {
            return materials.contains(material);
        }

        @Override
        public Material exemplarMaterial() {
            return materials.isEmpty() ? Material.AIR : materials.get(0);
        }

        @Override
        public String describe() {
            return "#" + tagName + (amount() > 1 ? " x" + amount() : "");
        }
    }

    public static final class AnyOf extends Ingredient {
        private final List<Ingredient> options;

        private AnyOf(List<Ingredient> options) {
            super(1);
            this.options = List.copyOf(options);
        }

        public List<Ingredient> options() {
            return options;
        }

        @Override
        public Material exemplarMaterial() {
            // Bukkit registration needs a single material for the slot; the
            // strict matcher is what actually enforces the alternatives.
            return options.isEmpty() ? Material.AIR : options.get(0).exemplarMaterial();
        }

        @Override
        public String describe() {
            StringBuilder text = new StringBuilder("any of [");
            for (int i = 0; i < options.size(); i++) {
                if (i > 0) {
                    text.append(", ");
                }
                text.append(options.get(i).describe());
            }
            return text.append(']').toString();
        }
    }
}
