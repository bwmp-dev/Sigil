package dev.bwmp.sigil.item;

import dev.bwmp.sigil.api.item.Uses;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Spending and merging item charges. */
public final class UsesService {

    private final Keys keys;
    private final ItemFactory factory;
    private final ItemResolver resolver;

    public UsesService(Keys keys, ItemFactory factory, ItemResolver resolver) {
        this.keys = keys;
        this.factory = factory;
        this.resolver = resolver;
    }

    /**
     * Spends one charge.
     * <p>
     * Because limited-use stacks are unique and therefore never stack, this is
     * a simple decrement — no splitting the stack and re-homing the remainder,
     * which is what the design this replaces had to do and got wrong when the
     * inventory was full.
     *
     * @return false when the item was destroyed
     */
    public boolean consume(Player player, ItemStack stack, SigilItem item) {
        Uses uses = item.definition().uses();
        if (!uses.limited() || player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }

        int remaining = resolver.usesOf(stack);
        if (remaining < 0) {
            return true;
        }

        remaining = Math.max(0, remaining - 1);
        if (remaining == 0 && uses.deleteAtZero()) {
            stack.setAmount(Math.max(0, stack.getAmount() - 1));
            return false;
        }

        write(stack, remaining);
        factory.rerender(stack, item);
        return true;
    }

    /** True when a limited-use item has no charges left and was kept as a husk. */
    public boolean isSpent(ItemStack stack, SigilItem item) {
        return item.definition().uses().limited() && resolver.usesOf(stack) == 0;
    }

    /**
     * Restores charges, capped at the definition's maximum.
     * <p>
     * Reports what it actually added rather than whether it succeeded, so a
     * repair kit can decline to spend itself on an item that was already full —
     * which is the difference between a useful item and a trap.
     *
     * @return charges added; 0 when the item is unlimited, already full, or
     *         a spent husk the definition deletes at zero
     */
    public int restore(ItemStack stack, SigilItem item, int amount) {
        Uses uses = item.definition().uses();
        if (!uses.limited() || amount == 0) {
            return 0;
        }

        int current = resolver.usesOf(stack);
        if (current < 0) {
            return 0;
        }

        int updated = Math.max(0, Math.min(uses.max(), current + amount));
        if (updated == current) {
            return 0;
        }

        write(stack, updated);
        factory.rerender(stack, item);
        return updated - current;
    }

    /**
     * Merges two stacks of the same item, summing charges.
     * <p>
     * Capped at the definition's maximum, and the excess stays on the sacrifice
     * rather than vanishing — an anvil that silently eats charges is worse than
     * one that refuses the combine.
     *
     * @return the merged result, or null when the combine is not valid
     */
    public ItemStack merge(ItemStack target, ItemStack sacrifice, SigilItem item) {
        if (!item.definition().uses().limited() || !resolver.sameItem(target, sacrifice)) {
            return null;
        }

        int max = item.definition().uses().max();
        int targetUses = Math.max(0, resolver.usesOf(target));
        int sacrificeUses = Math.max(0, resolver.usesOf(sacrifice));
        if (targetUses >= max || sacrificeUses <= 0) {
            return null;
        }

        int merged = Math.min(max, targetUses + sacrificeUses);
        ItemStack result = target.clone();
        write(result, merged);
        factory.rerender(result, item);
        return result;
    }

    /** How much of the sacrifice a merge would actually absorb. */
    public int mergeSurplus(ItemStack target, ItemStack sacrifice, SigilItem item) {
        int max = item.definition().uses().max();
        int targetUses = Math.max(0, resolver.usesOf(target));
        int sacrificeUses = Math.max(0, resolver.usesOf(sacrifice));
        return Math.max(0, targetUses + sacrificeUses - max);
    }

    private void write(ItemStack stack, int remaining) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(keys.uses, PersistentDataType.INTEGER, remaining);
        stack.setItemMeta(meta);
    }
}
