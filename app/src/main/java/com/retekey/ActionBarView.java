package com.retekey;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The strip above the keys: the actions that are not letters, visible while you type.
 *
 * <p>Everything on it exists elsewhere already — the menu page has tiles, the pads have overlays —
 * and what none of those has is being reachable without hiding the text you are working on. The bar
 * costs the height of one short row and is never a mode.
 *
 * <p>Three kinds of slot sit side by side, and holding one does something different for each:
 * a built-in action does it once — except the keys, arrows to page keys, which repeat — text
 * repeats while the finger is down the way a held key does,
 * and a key combination latches — pressed and left down until the slot is pressed again, which is
 * the only way an on-screen key can be held at all.
 */
final class ActionBarView extends HorizontalScrollView {
    /** What the bar asks the service to do. */
    interface Listener {
        void onAction(BarAction action);

        void onText(String text);

        /** A chord pressed and released at once. */
        void onChord(BarSlot slot);

        /** A chord pressed and left down, or let up again. */
        void onChordLatch(BarSlot slot, boolean down);

        /**
         * A finger has come down on a slot that types ({@code true}) or lifted off it again. What
         * modifiers are down is read once, here, so a held arrow keeps its Ctrl for every repeat
         * and a one-shot toggle is spent by the press rather than by its first repeat.
         */
        void onPress(boolean down);
    }

    private static final int GAP_DP = 3;
    /** The height the bar's labels were sized for. */
    private static final int USUAL_HEIGHT_DP = 40;
    private static final int LABEL_SP = 15;

    private final LinearLayout row;
    private final KeyboardPalette palette;
    /** No press shade when the keys are to stay still (issue #15). */
    private final boolean still;
    /** The bar's height while its setting is untouched, and as set for this orientation. */
    private final int usualHeightPx;
    private final int heightPx;
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** The chords held down right now. They stay down until pressed again. */
    private final Set<BarSlot> latched = new HashSet<>();
    private Listener listener;
    private int repeatDelayMs = KeyRepeatSettings.DEFAULT_DELAY_MS;
    private int repeatIntervalMs = KeyRepeatSettings.DEFAULT_INTERVAL_MS;

    ActionBarView(Context context) {
        super(context);
        palette = KeyboardPalette.resolve(context);
        still = ScreenTheme.still(context);
        android.content.SharedPreferences prefs =
            context.getSharedPreferences("retekey_view", Context.MODE_PRIVATE);
        ScreenOrientation orientation = OrientedPrefs.current(context);
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        usualHeightPx = ActionBarHeightSettings.usualPx(
            OrientedPrefs.getInt(prefs, ActionBarHeightSettings.LEGACY_KEY_PERCENT, orientation,
                ActionBarHeightSettings.LEGACY_DEFAULT_PERCENT),
            metrics.density);
        int percent = OrientedPrefs.getInt(prefs, ActionBarHeightSettings.KEY_SCREEN_PERCENT,
            orientation, ActionBarHeightSettings.UNSET);
        heightPx = percent < 0
            ? usualHeightPx
            : ActionBarHeightSettings.heightPx(
                percent, KeyboardHeightPrefs.screenHeightPx(orientation, metrics));
        setFillViewport(true);
        setHorizontalScrollBarEnabled(false);
        setBackgroundColor(palette.background);
        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addView(row, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    /** The auto-repeat timings the keyboard itself uses, so a held slot feels like a held key. */
    void setRepeatTimings(int delayMs, int intervalMs) {
        repeatDelayMs = delayMs;
        repeatIntervalMs = intervalMs;
    }

    /** Fills the bar. Called again whenever the slot list changes. */
    void setSlots(List<BarSlot> slots) {
        row.removeAllViews();
        latched.clear();
        for (int i = 0; i < slots.size(); i++) {
            // Gboard sets its first and last buttons apart with a thin rule; so does this bar,
            // once there are enough slots for there to be a middle.
            boolean ruled = slots.size() >= 3;
            row.addView(button(slots.get(i), ruled && i == 0, ruled && i == slots.size() - 1),
                slotParams());
        }
    }

    /** The strip's height in pixels, so the frame around it can account for it. */
    int barHeightPx() {
        return heightPx;
    }

    /**
     * Gboard spreads its toolbar buttons across the whole width; the slots share it equally here,
     * and only scroll when there are more of them than fit.
     */
    private LinearLayout.LayoutParams slotParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f);
        params.setMargins(dp(GAP_DP), 0, dp(GAP_DP), 0);
        return params;
    }

    private View button(BarSlot slot, boolean ruleAfter, boolean ruleBefore) {
        SlotView view = new SlotView(getContext(), slot, ruleAfter, ruleBefore);
        float usual = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, LABEL_SP, getResources().getDisplayMetrics());
        view.setLabelSize(ActionBarHeightSettings.labelPx(
            heightPx, dp(USUAL_HEIGHT_DP), usual, barHeightPx() - 2 * dp(GAP_DP)) * 0.92f);
        view.setMinimumWidth(dp(48));
        view.setClickable(true);
        view.setFocusable(true);
        view.setContentDescription(slot.label() == null ? "" : slot.label());
        view.setOnTouchListener(new SlotTouch(slot, view));
        return view;
    }

    /** The Gboard icon for a built-in action, or null where the slot says what it is in words. */
    static KeyIcons.Icon iconFor(BarSlot slot) {
        if (slot.kind() != BarSlot.Kind.BUILT_IN || slot.action() == null) {
            return null;
        }
        switch (slot.action()) {
            case MENU:
                return KeyIcons.Icon.GRID;
            case CLIPBOARD:
                return KeyIcons.Icon.CLIPBOARD;
            case NOTEPAD:
                return KeyIcons.Icon.NOTE;
            case SETTINGS:
                return KeyIcons.Icon.SETTINGS;
            case COPY:
                return KeyIcons.Icon.COPY;
            case PASTE:
                return KeyIcons.Icon.PASTE;
            case CUT:
                return KeyIcons.Icon.CUT;
            case SELECT_ALL:
                return KeyIcons.Icon.SELECT_ALL;
            case SWITCH_IME:
                return KeyIcons.Icon.KEYBOARD;
            case LEFT:
                return KeyIcons.Icon.LEFT;
            case RIGHT:
                return KeyIcons.Icon.RIGHT;
            default:
                return null;
        }
    }

    /**
     * One toolbar button, drawn the way Gboard draws its own: an icon (or a short word) in the
     * keyboard's ink straight on the toolbar's ground, with a rounded highlight only while it is
     * pressed, and the accent while a modifier on it is held down.
     */
    private final class SlotView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF pill = new RectF();
        private final KeyIcons icons = new KeyIcons();
        private final KeyIcons.Icon icon;
        private final String label;
        private final boolean ruleAfter;
        private final boolean ruleBefore;
        private boolean pressedState;
        private boolean latchedState;

        SlotView(Context context, BarSlot slot, boolean ruleAfter, boolean ruleBefore) {
            super(context);
            this.icon = iconFor(slot);
            // Never null: a slot whose label is missing still measures and draws as an empty word.
            String named = slot.label();
            this.label = named == null ? "" : named;
            this.ruleAfter = ruleAfter;
            this.ruleBefore = ruleBefore;
            paint.setTextAlign(Paint.Align.CENTER);
        }

        void setLabelSize(float px) {
            paint.setTextSize(px);
            requestLayout();
        }

        void setState(boolean pressed, boolean latched) {
            pressedState = pressed;
            latchedState = latched;
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int natural = icon != null
                ? dp(48)
                : Math.round(paint.measureText(label)) + dp(24);
            int width = resolveSize(Math.max(getSuggestedMinimumWidth(), natural),
                widthMeasureSpec);
            // A parent that leaves the height open gets the bar's own height rather than zero.
            int height = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
                ? barHeightPx()
                : MeasureSpec.getSize(heightMeasureSpec);
            setMeasuredDimension(width, height);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            float cx = w * 0.5f;
            float cy = h * 0.5f;
            int ink = KeyPressTint.mix(palette.keyText, palette.background, 0.12f);
            if (latchedState || (pressedState && !still)) {
                // Gboard's pressed state: a rounded highlight hugging the icon.
                float half = Math.min(h * 0.42f, Math.max(dp(18), Math.min(w * 0.5f - dp(2),
                    icon != null ? h * 0.55f : paint.measureText(label) * 0.5f + dp(12))));
                float vertical = h * 0.40f;
                pill.set(cx - half, cy - vertical, cx + half, cy + vertical);
                if (icon == null) {
                    pill.left = Math.max(dp(2), cx - paint.measureText(label) * 0.5f - dp(12));
                    pill.right = Math.min(w - dp(2), cx + paint.measureText(label) * 0.5f + dp(12));
                }
                int fill = latchedState
                    ? palette.keyAccent
                    : KeyPressTint.mix(palette.background, palette.keyText, 0.10f);
                paint.setColor(fill);
                canvas.drawRoundRect(pill, vertical, vertical, paint);
                if (latchedState) {
                    ink = palette.keyLatchedInk();
                }
            }
            if (icon != null) {
                icons.draw(canvas, paint, icon, cx, cy, Math.min(h * 0.46f, dp(22)), ink);
            } else {
                paint.setColor(ink);
                canvas.drawText(label, cx,
                    cy - (paint.descent() + paint.ascent()) / 2.0f, paint);
            }
            if (ruleAfter || ruleBefore) {
                paint.setColor(KeyPressTint.mix(palette.background, palette.keyText, 0.18f));
                float stroke = Math.max(1.0f, dp(1) * 0.75f);
                // Drawn from inside the slot, just inside the edge it shares with its neighbour.
                float x = ruleAfter ? w - stroke : stroke;
                canvas.drawRect(x - stroke * 0.5f, h * 0.25f, x + stroke * 0.5f, h * 0.75f, paint);
            }
        }
    }

    /**
     * A slot's press, hold and release.
     *
     * <p>Written by hand rather than as a click listener, because "what a hold does" is the whole
     * point of two of the three kinds and a click listener cannot see a hold at all.
     */
    private final class SlotTouch implements View.OnTouchListener {
        private final BarSlot slot;
        private final SlotView view;
        private final Runnable repeat = new Runnable() {
            @Override
            public void run() {
                repeated = true;
                if (listener != null) {
                    if (slot.kind() == BarSlot.Kind.TEXT) {
                        listener.onText(slot.text());
                    } else {
                        listener.onAction(slot.action());
                    }
                }
                handler.postDelayed(this, Math.max(20, repeatIntervalMs));
            }
        };
        private final Runnable latch = new Runnable() {
            @Override
            public void run() {
                held = true;
                setLatched(true);
            }
        };
        private boolean held;

        SlotTouch(BarSlot slot, SlotView view) {
            this.slot = slot;
            this.view = view;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    press();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    release(event.getActionMasked() == MotionEvent.ACTION_UP);
                    return true;
                default:
                    return true;
            }
        }

        /** Whether this slot types something a hold should repeat. */
        private boolean repeats() {
            return slot.kind() == BarSlot.Kind.TEXT
                || (slot.kind() == BarSlot.Kind.BUILT_IN && slot.action() != null
                    && slot.action().rawKey() != null);
        }

        private boolean pressing;
        /** Set once a hold has repeated: the lift then types nothing more, as with a real key. */
        private boolean repeated;

        private void press() {
            held = false;
            repeated = false;
            paint(true);
            if (repeats() && listener != null) {
                pressing = true;
                listener.onPress(true);
            }
            if (repeats()) {
                handler.postDelayed(repeat, Math.max(50, repeatDelayMs));
            } else if (slot.canLatch() && !latched.contains(slot)) {
                handler.postDelayed(latch, Math.max(50, repeatDelayMs));
            }
        }

        private void release(boolean insideThePress) {
            handler.removeCallbacks(repeat);
            handler.removeCallbacks(latch);
            paint(false);
            try {
                deliver(insideThePress);
            } finally {
                if (pressing && listener != null) {
                    pressing = false;
                    listener.onPress(false);
                }
            }
        }

        private void deliver(boolean insideThePress) {
            if (!insideThePress || listener == null) {
                return;
            }
            if (held || repeated) {
                // The hold already did what it does; the lift only ends it.
                return;
            }
            switch (slot.kind()) {
                case BUILT_IN:
                    if (slot.canLatch() && latched.contains(slot)) {
                        // A locked modifier: this press lets it up. Going through onAction would
                        // clear the latch the keyboard holds and leave this view still believing
                        // the slot was down — painted locked, and never latchable again.
                        setLatched(false);
                        break;
                    }
                    listener.onAction(slot.action());
                    break;
                case TEXT:
                    listener.onText(slot.text());
                    break;
                default:
                    if (latched.contains(slot)) {
                        setLatched(false);
                    } else {
                        listener.onChord(slot);
                    }
                    break;
            }
        }

        private void setLatched(boolean down) {
            if (down) {
                latched.add(slot);
            } else {
                latched.remove(slot);
            }
            paint(false);
            if (listener != null) {
                listener.onChordLatch(slot, down);
            }
        }

        /** A pressed slot is highlighted; a latched one keeps the accent until it is let up. */
        private void paint(boolean pressed) {
            view.setState(pressed, latched.contains(slot));
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // A frame that has worked out the bar's band says so exactly, and is believed: it may be
        // less than the bar asks for, where the keys would otherwise be left with nothing.
        int height = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY
            ? Math.min(barHeightPx(), MeasureSpec.getSize(heightMeasureSpec))
            : barHeightPx();
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
