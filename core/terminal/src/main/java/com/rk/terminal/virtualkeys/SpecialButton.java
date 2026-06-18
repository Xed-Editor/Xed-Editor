package com.rk.terminal.virtualkeys;

import androidx.annotation.NonNull;

import java.util.HashMap;

/**
 * The {@link Class} that implements special buttons for {@link VirtualKeysView}.
 *
 * @param key The special button key.
 */
public record SpecialButton(String key) {

    private static final HashMap<String, SpecialButton> map = new HashMap<>();
    public static final SpecialButton CTRL = new SpecialButton("CTRL");
    public static final SpecialButton ALT = new SpecialButton("ALT");
    public static final SpecialButton SHIFT = new SpecialButton("SHIFT");
    public static final SpecialButton FN = new SpecialButton("FN");

    /**
     * Initialize a {@link SpecialButton}.
     *
     * @param key The unique key name for the special button. The key is registered in {@link #map}
     *            with which the {@link SpecialButton} can be retrieved via a call to {@link
     *            #valueOf(String)}.
     */
    public SpecialButton(@NonNull final String key) {
        this.key = key;
        map.put(key, this);
    }

    /**
     * Get the {@link SpecialButton} for {@code key}.
     *
     * @param key The unique key name for the special button.
     */
    public static SpecialButton valueOf(String key) {
        return map.get(key);
    }

    /**
     * Get {@link #key} for this {@link SpecialButton}.
     */
    @Override
    public String key() {
        return key;
    }

    @NonNull
    @Override
    public String toString() {
        return key;
    }
}
