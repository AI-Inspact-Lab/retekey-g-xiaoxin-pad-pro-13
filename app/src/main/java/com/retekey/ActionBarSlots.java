package com.retekey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What the bar above the keys carries, and in what order.
 *
 * <p>A slot is a built-in action, a piece of text the user wrote, or a key combination they
 * assembled — see {@link BarSlot}. This class is the list: what it holds by default, how it is read
 * back from preferences, and the one rule for moving a slot, shared by the arrows and by dragging so
 * a slot cannot land somewhere different depending on how it was picked up.
 *
 * <p>The bar is off until the user turns it on. A keyboard that grows a new strip on update, and so
 * becomes taller, is a keyboard that changed size for a reason its owner did not choose.
 */
final class ActionBarSlots {
    static final String KEY_SLOTS = "action_bar_slots";
    static final String KEY_ENABLED = "action_bar_enabled";
    /**
     * On by default in this Gboard-style build: Gboard always has its toolbar over the keys, and
     * it is most of what makes the keyboard read as Gboard at a glance.
     */
    static final boolean DEFAULT_ENABLED = true;

    /**
     * What the bar holds before anyone rearranges it, Gboard's way: the grid button on the left,
     * the editing tools in the middle, the gear on the right. The arrows are on the bottom row now.
     */
    static final List<BarAction> DEFAULT_ACTIONS = Arrays.asList(
        BarAction.MENU,
        BarAction.CLIPBOARD,
        BarAction.SELECT_ALL,
        BarAction.CUT,
        BarAction.COPY,
        BarAction.PASTE,
        BarAction.NOTEPAD,
        BarAction.SETTINGS);

    private ActionBarSlots() {
    }

    /** The default bar as slots. */
    static List<BarSlot> defaults() {
        List<BarSlot> slots = new ArrayList<>(DEFAULT_ACTIONS.size());
        for (BarAction action : DEFAULT_ACTIONS) {
            slots.add(BarSlot.of(action));
        }
        return slots;
    }

    /**
     * Reads a stored bar. An empty or unreadable one falls back to the default, which is what the
     * bar looked like the first time it was switched on; emptiness is stored as the bar being off,
     * not as a bar of nothing.
     */
    static List<BarSlot> parse(String stored) {
        List<BarSlot> slots = BarSlotCodec.decode(stored);
        return slots.isEmpty() ? defaults() : slots;
    }

    /** The stored form of a bar. */
    static String format(List<BarSlot> slots) {
        return BarSlotCodec.encode(slots);
    }

    /**
     * The list with the item at {@code from} put down at {@code to}, or the same list where either
     * index is not one of its own.
     */
    static List<BarSlot> moved(List<BarSlot> slots, int from, int to) {
        if (from == to || from < 0 || to < 0 || from >= slots.size() || to >= slots.size()) {
            return slots;
        }
        List<BarSlot> next = new ArrayList<>(slots);
        next.add(to, next.remove(from));
        return next;
    }
}
