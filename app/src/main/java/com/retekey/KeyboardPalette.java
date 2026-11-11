package com.retekey;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;

/**
 * The keyboard's colours, resolved to the device's theme following Android guidance:
 *
 * <ul>
 *   <li>honour the system light/dark mode ({@link Configuration#UI_MODE_NIGHT_MASK});</li>
 *   <li>map to Material colour roles — background ≈ surface, key ≈ elevated surface, label ≈
 *       onSurface, active key ≈ primary, press ≈ a primary state layer;</li>
 *   <li>on Android 12+ pull the user's Material You dynamic palette
 *       ({@code system_neutral*}/{@code system_accent*}) so the keyboard matches their theme,
 *       falling back to a hand-tuned light/dark palette on older versions.</li>
 * </ul>
 */
final class KeyboardPalette {
    final int background;
    final int keyFace;
    final int keyDisabled;
    /** A key held down until it is released again. The strongest state a key can be in. */
    final int keyAccent;
    /** A key armed for exactly one press. Distinct from held, and no longer a whisper. */
    final int keyAccentSoft;
    final int keyText;
    /** The label ink for fills the ordinary one would disappear into. */
    final int keyTextInverse;
    final int keyTextMuted;
    final int keyShadow;
    final int hint;
    final int pressTint;
    /**
     * The colour of choosing: the strip of alternates a hold raises, and the echo of what it types.
     * A hue of its own rather than the accent, which is the colour of pressing (see
     * {@link ChoiceHue}).
     */
    final int choiceAccent;
    /**
     * Keys drawn as an outline in {@link #keyShadow} round the face, instead of a face raised on a
     * lip: the monochrome palette's way of showing a key's edge without a second shade (issue #15).
     */
    final boolean outlined;
    /**
     * Gboard style: the face of a key that does something rather than types something — Shift,
     * backspace, Enter, Tab, the page keys, the modifiers. Gboard paints these in a tinted
     * container colour so the letters stand out as the white keys in the middle.
     */
    final int functionFace;

    private KeyboardPalette(int background, int keyFace, int keyDisabled, int keyAccent,
            int keyAccentSoft, int keyText, int keyTextMuted, int keyShadow, int hint, int pressTint,
            int functionFace) {
        this(background, keyFace, keyDisabled, keyAccent, keyAccentSoft, keyText, keyTextMuted,
            keyShadow, hint, pressTint,
            KeyLabelContrast.prefersDarkInk(Color.red(keyText), Color.green(keyText),
                Color.blue(keyText)) ? Color.rgb(248, 250, 253) : Color.rgb(18, 20, 24),
            functionFace);
    }

    private KeyboardPalette(int background, int keyFace, int keyDisabled, int keyAccent,
            int keyAccentSoft, int keyText, int keyTextMuted, int keyShadow, int hint, int pressTint,
            int keyTextInverse, int functionFace) {
        this(background, keyFace, keyDisabled, keyAccent, keyAccentSoft, keyText, keyTextMuted,
            keyShadow, hint, pressTint, keyTextInverse, ChoiceHue.of(keyAccent), false,
            functionFace);
    }

    private KeyboardPalette(int background, int keyFace, int keyDisabled, int keyAccent,
            int keyAccentSoft, int keyText, int keyTextMuted, int keyShadow, int hint, int pressTint,
            int keyTextInverse, int choiceAccent, boolean outlined, int functionFace) {
        this.functionFace = functionFace;
        this.keyTextInverse = keyTextInverse;
        this.background = background;
        this.keyFace = keyFace;
        this.keyDisabled = keyDisabled;
        this.keyAccent = keyAccent;
        this.keyAccentSoft = keyAccentSoft;
        this.keyText = keyText;
        this.keyTextMuted = keyTextMuted;
        this.keyShadow = keyShadow;
        this.hint = hint;
        this.pressTint = pressTint;
        // Derived rather than given for every palette with colour in it — hand-tuned or the
        // device's own Material You — so the choosing colour keeps its saturation and lightness
        // and reads in the same theme as everything around it. Monochrome gives a grey instead.
        this.choiceAccent = choiceAccent;
        this.outlined = outlined;
    }

    /**
     * The label colour for a key filled with {@code fill}. A latched key is painted strongly, and
     * "strongly" is dark in the light theme and vivid in the dark one, so the ink cannot be fixed
     * per theme — it follows the fill's own luminance.
     */
    int inkOn(int fill) {
        boolean fillWantsDarkInk =
            KeyLabelContrast.prefersDarkInk(Color.red(fill), Color.green(fill), Color.blue(fill));
        boolean ordinaryInkIsDark = KeyLabelContrast.prefersDarkInk(
            Color.red(keyText), Color.green(keyText), Color.blue(keyText));
        return fillWantsDarkInk == ordinaryInkIsDark ? keyTextInverse : keyText;
    }

    /**
     * The colour for the small corner marks on a key filled with {@code fill}: the hint colour
     * where the face is the ordinary one, and the face's own ink softened where it is not. A
     * latched key is painted in the ink colour itself, and the fixed hint colour disappears into
     * it.
     */
    /**
     * The face of a key that is latched down, and the ink over it: the same pairing the echo popup
     * uses for every keystroke — the accent as a background, the keyboard's own background colour
     * as the ink. Borrowing it means the strongest state a key can be in is painted in the one
     * combination the user already reads several times a second, rather than in a private scheme
     * they have to learn.
     */
    int keyLatchedFace() {
        return keyAccent;
    }

    /** The ink over a latched face: the popup's text colour, so the two match exactly. */
    int keyLatchedInk() {
        return background;
    }

    int hintOn(int fill) {
        if (fill == keyFace || fill == keyDisabled || fill == functionFace) {
            return hint;
        }
        return KeyPressTint.mix(inkOn(fill), fill, 0.35f);
    }

    /**
     * The face of a key under a finger, Gboard style: the key darkens a little in the light theme
     * and lightens a little in the dark one — a state layer of the label colour over the face —
     * rather than flashing toward the accent.
     */
    int pressedFace(int fill) {
        return KeyPressTint.mix(fill, keyText, outlined ? 0.30f : 0.12f);
    }

    /**
     * Whether the keyboard is painted dark: the user's own choice where they made one, and the
     * device's light/dark setting where they left it on {@link ThemeMode#SYSTEM}.
     */
    static boolean isNight(Context context) {
        return ScreenTheme.mode(context).night(systemNight(context));
    }

    /** What the device itself is set to, ignoring the app's own setting. */
    static boolean systemNight(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
    }

    static KeyboardPalette resolve(Context context) {
        boolean night = isNight(context);
        if (ScreenTheme.monochrome(context)) {
            return monochrome(night);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                return dynamic(context, night);
            } catch (RuntimeException useStatic) {
                // Dynamic colours unavailable; fall back to the hand-tuned palette.
            }
        }
        return night ? dark() : light();
    }

    private static int sys(Context context, int colorRes) {
        return Compat.getColor(context, colorRes);
    }

    private static KeyboardPalette dynamic(Context context, boolean night) {
        // Gboard's Material You mapping: the keyboard sits on a neutral surface, the letter keys
        // are the lightest (light theme) or a raised (dark theme) neutral, and the function keys
        // take the secondary container colour of the user's own wallpaper palette.
        if (night) {
            return new KeyboardPalette(
                sys(context, android.R.color.system_neutral1_900),   // background
                sys(context, android.R.color.system_neutral1_700),   // letter key
                sys(context, android.R.color.system_neutral1_800),   // disabled
                sys(context, android.R.color.system_accent1_200),    // held
                sys(context, android.R.color.system_accent1_700),    // armed
                sys(context, android.R.color.system_neutral1_50),    // label
                sys(context, android.R.color.system_neutral2_400),   // muted label
                sys(context, android.R.color.system_neutral1_900),   // (no lip: flat keys)
                sys(context, android.R.color.system_neutral2_300),   // hint
                sys(context, android.R.color.system_accent1_300),    // press tint
                sys(context, android.R.color.system_accent2_800));   // function key
        }
        return new KeyboardPalette(
            sys(context, android.R.color.system_neutral1_50),        // background
            sys(context, android.R.color.system_neutral1_10),        // letter key
            sys(context, android.R.color.system_neutral1_100),       // disabled
            sys(context, android.R.color.system_accent1_600),        // held
            sys(context, android.R.color.system_accent1_200),        // armed
            sys(context, android.R.color.system_neutral1_900),       // label
            sys(context, android.R.color.system_neutral2_500),       // muted label
            sys(context, android.R.color.system_neutral1_50),        // (no lip: flat keys)
            sys(context, android.R.color.system_neutral2_600),       // hint
            sys(context, android.R.color.system_accent1_300),        // press tint
            sys(context, android.R.color.system_accent2_100));       // function key
    }

    /** Paper and ink, and greys between them for the states (see {@link PlainDisplay}). */
    static KeyboardPalette monochrome(boolean night) {
        int[] c = PlainDisplay.monochrome(night);
        return new KeyboardPalette(
            c[PlainDisplay.BACKGROUND], c[PlainDisplay.KEY_FACE], c[PlainDisplay.KEY_DISABLED],
            c[PlainDisplay.HELD], c[PlainDisplay.ARMED], c[PlainDisplay.KEY_TEXT],
            c[PlainDisplay.KEY_TEXT_MUTED], c[PlainDisplay.EDGE], c[PlainDisplay.HINT],
            c[PlainDisplay.PRESS], c[PlainDisplay.BACKGROUND], c[PlainDisplay.CHOICE], true,
            c[PlainDisplay.KEY_FACE]);
    }

    /** Gboard's dark theme, for devices without Material You colours. */
    private static KeyboardPalette dark() {
        return new KeyboardPalette(
            Color.rgb(30, 31, 36),    // background (surface)
            Color.rgb(61, 62, 68),    // letter key
            Color.rgb(44, 45, 50),    // disabled
            Color.rgb(170, 199, 255), // held: the strongest state
            Color.rgb(58, 76, 118),   // armed for one key
            Color.rgb(227, 227, 232), // label (onSurface)
            Color.rgb(142, 144, 152), // muted label
            Color.rgb(30, 31, 36),    // (no lip: flat keys)
            Color.rgb(160, 162, 170), // long-press hint
            Color.rgb(120, 150, 220), // press tint
            Color.rgb(46, 54, 76));   // function key (secondary container)
    }

    /**
     * Gboard's light theme, measured off a Gboard screenshot on the same tablet: a lavender-grey
     * ground, white letter keys, and function keys in a pale blue container colour.
     */
    private static KeyboardPalette light() {
        return new KeyboardPalette(
            Color.rgb(238, 237, 244), // background (surface container)
            Color.rgb(255, 255, 255), // letter key
            Color.rgb(228, 228, 235), // disabled
            Color.rgb(64, 90, 145),   // held: deep, the strongest state
            Color.rgb(183, 198, 240), // armed for one key
            Color.rgb(26, 27, 32),    // label (onSurface)
            Color.rgb(120, 122, 130), // muted label
            Color.rgb(238, 237, 244), // (no lip: flat keys)
            Color.rgb(95, 97, 104),   // long-press hint
            Color.rgb(120, 150, 220), // press tint
            Color.rgb(220, 226, 249)); // function key (secondary container)
    }
}
