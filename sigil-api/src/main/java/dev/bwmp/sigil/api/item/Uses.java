package dev.bwmp.sigil.api.item;

/**
 * How many times an item can be used before it is spent.
 *
 * @param limited      false for unlimited use
 * @param max          the starting charge count; ignored when unlimited
 * @param deleteAtZero whether the item vanishes on its last use, or stays as a
 *                     spent husk
 */
public record Uses(boolean limited, int max, boolean deleteAtZero) {

    private static final Uses INFINITE = new Uses(false, -1, false);

    public static Uses infinite() {
        return INFINITE;
    }

    public static Uses limited(int max, boolean deleteAtZero) {
        return new Uses(true, Math.max(1, max), deleteAtZero);
    }

    /**
     * Limited-use items must not stack, or one counter is shared by the whole
     * stack. Sigil enforces that by giving each stack a unique id rather than
     * by setting a max stack size, which only exists from 1.20.5.
     */
    public boolean requiresUniqueStacks() {
        return limited;
    }
}
