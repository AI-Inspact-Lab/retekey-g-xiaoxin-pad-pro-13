package com.retekey;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * The function-key icons, drawn as vector paths in the manner of Gboard's own: line icons with
 * round caps on a key face, sized from the face rather than from a font.
 *
 * <p>Drawn rather than typed because the glyphs that stand for these keys (⌫ ⏎ ⇧ ⇥) come out in
 * whatever symbol font the device has, at whatever weight it has them, and that mismatch is most
 * of what made the old keyboard look home-made. A path looks the same on every device.
 */
final class KeyIcons {
    enum Icon {
        BACKSPACE,
        SHIFT,
        /** Shift armed for one key: the same arrow, filled. */
        SHIFT_ACTIVE,
        /** Shift locked: filled, with a bar under it. */
        SHIFT_LOCKED,
        CAPS_LOCK,
        ENTER,
        TAB,
        GLOBE,
        LEFT,
        RIGHT,
        GRID,
        CLIPBOARD,
        NOTE,
        SETTINGS,
        COPY,
        PASTE,
        CUT,
        SELECT_ALL,
        KEYBOARD,
        UNDO,
        REDO,
        FLOATING,
        THEME,
        DATE,
        SIZE_UP,
        SIZE_DOWN,
        UNICODE
    }

    private final Path path = new Path();
    private final RectF oval = new RectF();

    /**
     * Draws {@code icon} centred on ({@code cx}, {@code cy}), {@code size} pixels across at its
     * widest, in {@code color}. The paint's style, stroke and colour are restored afterwards so the
     * caller can go on drawing labels with it.
     */
    void draw(Canvas canvas, Paint paint, Icon icon, float cx, float cy, float size, int color) {
        Paint.Style style = paint.getStyle();
        float stroke = paint.getStrokeWidth();
        Paint.Cap cap = paint.getStrokeCap();
        Paint.Join join = paint.getStrokeJoin();
        int oldColor = paint.getColor();
        paint.setColor(color);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(Math.max(1.5f, size * 0.085f));
        paint.setStyle(Paint.Style.STROKE);
        float h = size * 0.5f;
        path.reset();
        switch (icon) {
            case BACKSPACE:
                backspace(canvas, paint, cx, cy, size);
                break;
            case SHIFT:
            case SHIFT_ACTIVE:
            case SHIFT_LOCKED:
                shift(canvas, paint, cx, icon == Icon.SHIFT_LOCKED ? cy - size * 0.08f : cy, size * 0.9f,
                    icon != Icon.SHIFT);
                if (icon == Icon.SHIFT_LOCKED) {
                    float y = cy + size * 0.42f;
                    canvas.drawLine(cx - size * 0.22f, y, cx + size * 0.22f, y, paint);
                }
                break;
            case CAPS_LOCK: {
                // Gboard's caps lock: a chevron over a bar.
                float w = size * 0.32f;
                path.moveTo(cx - w, cy + size * 0.02f);
                path.lineTo(cx, cy - size * 0.30f);
                path.lineTo(cx + w, cy + size * 0.02f);
                canvas.drawPath(path, paint);
                float y = cy + size * 0.30f;
                canvas.drawLine(cx - w, y, cx + w, y, paint);
                break;
            }
            case ENTER: {
                // ↵: down the right-hand side, along to the left, an arrowhead there.
                float right = cx + h * 0.80f;
                float top = cy - h * 0.62f;
                float baseY = cy + h * 0.18f;
                float left = cx - h * 0.80f;
                path.moveTo(right, top);
                path.lineTo(right, baseY);
                path.lineTo(left, baseY);
                canvas.drawPath(path, paint);
                path.reset();
                float head = h * 0.38f;
                path.moveTo(left + head, baseY - head);
                path.lineTo(left, baseY);
                path.lineTo(left + head, baseY + head);
                canvas.drawPath(path, paint);
                break;
            }
            case TAB: {
                // →| : an arrow running into a bar.
                float left = cx - h * 0.85f;
                float right = cx + h * 0.55f;
                canvas.drawLine(left, cy, right, cy, paint);
                float head = h * 0.38f;
                path.moveTo(right - head, cy - head);
                path.lineTo(right, cy);
                path.lineTo(right - head, cy + head);
                canvas.drawPath(path, paint);
                float bar = cx + h * 0.85f;
                canvas.drawLine(bar, cy - h * 0.55f, bar, cy + h * 0.55f, paint);
                break;
            }
            case GLOBE: {
                float r = h * 0.85f;
                canvas.drawCircle(cx, cy, r, paint);
                oval.set(cx - r * 0.42f, cy - r, cx + r * 0.42f, cy + r);
                canvas.drawOval(oval, paint);
                canvas.drawLine(cx - r, cy, cx + r, cy, paint);
                float lat = r * 0.52f;
                float half = (float) Math.sqrt(r * r - lat * lat);
                canvas.drawLine(cx - half, cy - lat, cx + half, cy - lat, paint);
                canvas.drawLine(cx - half, cy + lat, cx + half, cy + lat, paint);
                break;
            }
            case LEFT:
            case RIGHT: {
                // Gboard's cursor keys: a small solid triangle.
                paint.setStyle(Paint.Style.FILL_AND_STROKE);
                paint.setStrokeWidth(Math.max(1.0f, size * 0.05f));
                float w = h * 0.42f;
                float tall = h * 0.50f;
                float tip = icon == Icon.LEFT ? cx - w : cx + w;
                float back = icon == Icon.LEFT ? cx + w : cx - w;
                path.moveTo(tip, cy);
                path.lineTo(back, cy - tall);
                path.lineTo(back, cy + tall);
                path.close();
                canvas.drawPath(path, paint);
                break;
            }
            case GRID: {
                // Four rounded squares: Gboard's "more" button, here the menu page.
                paint.setStyle(Paint.Style.FILL);
                float cell = h * 0.70f;
                float gap = h * 0.22f;
                float rr = cell * 0.30f;
                for (int i = 0; i < 4; i++) {
                    float x = i % 2 == 0 ? cx - gap * 0.5f - cell : cx + gap * 0.5f;
                    float y = i < 2 ? cy - gap * 0.5f - cell : cy + gap * 0.5f;
                    oval.set(x, y, x + cell, y + cell);
                    canvas.drawRoundRect(oval, rr, rr, paint);
                }
                break;
            }
            case CLIPBOARD:
            case PASTE: {
                float w = h * 0.70f;
                float top = cy - h * 0.78f;
                float bottom = cy + h * 0.85f;
                oval.set(cx - w, top, cx + w, bottom);
                canvas.drawRoundRect(oval, w * 0.25f, w * 0.25f, paint);
                // The clip at the top.
                oval.set(cx - w * 0.45f, top - h * 0.14f, cx + w * 0.45f, top + h * 0.20f);
                paint.setStyle(Paint.Style.FILL_AND_STROKE);
                canvas.drawRoundRect(oval, w * 0.12f, w * 0.12f, paint);
                paint.setStyle(Paint.Style.STROKE);
                if (icon == Icon.CLIPBOARD) {
                    for (int i = 0; i < 3; i++) {
                        float y = cy - h * 0.15f + i * h * 0.32f;
                        canvas.drawLine(cx - w * 0.5f, y, cx + w * 0.5f, y, paint);
                    }
                } else {
                    // Paste: an arrow into the board.
                    canvas.drawLine(cx, cy - h * 0.18f, cx, cy + h * 0.52f, paint);
                    path.moveTo(cx - h * 0.28f, cy + h * 0.24f);
                    path.lineTo(cx, cy + h * 0.52f);
                    path.lineTo(cx + h * 0.28f, cy + h * 0.24f);
                    canvas.drawPath(path, paint);
                }
                break;
            }
            case NOTE: {
                // A page with a folded corner and lines on it.
                float w = h * 0.70f;
                float top = cy - h * 0.85f;
                float bottom = cy + h * 0.85f;
                float fold = w * 0.55f;
                path.moveTo(cx - w, top);
                path.lineTo(cx + w - fold, top);
                path.lineTo(cx + w, top + fold);
                path.lineTo(cx + w, bottom);
                path.lineTo(cx - w, bottom);
                path.close();
                canvas.drawPath(path, paint);
                for (int i = 0; i < 3; i++) {
                    float y = cy - h * 0.10f + i * h * 0.32f;
                    canvas.drawLine(cx - w * 0.5f, y, cx + w * 0.5f, y, paint);
                }
                break;
            }
            case SETTINGS:
                gear(canvas, paint, cx, cy, h * 0.92f);
                break;
            case COPY: {
                float w = h * 0.58f;
                oval.set(cx - w * 0.55f, cy - w * 1.15f, cx + w * 1.15f, cy + w * 0.55f);
                canvas.drawRoundRect(oval, w * 0.22f, w * 0.22f, paint);
                path.moveTo(cx - w * 0.75f + w * 0.0f, cy - w * 0.55f);
                path.lineTo(cx - w * 1.15f, cy - w * 0.55f);
                path.lineTo(cx - w * 1.15f, cy + w * 1.15f);
                path.lineTo(cx + w * 0.55f, cy + w * 1.15f);
                path.lineTo(cx + w * 0.55f, cy + w * 0.75f);
                canvas.drawPath(path, paint);
                break;
            }
            case CUT: {
                float r = h * 0.26f;
                float y = cy + h * 0.52f;
                canvas.drawCircle(cx - h * 0.42f, y, r, paint);
                canvas.drawCircle(cx + h * 0.42f, y, r, paint);
                canvas.drawLine(cx - h * 0.28f, y - r * 0.9f, cx + h * 0.45f, cy - h * 0.85f, paint);
                canvas.drawLine(cx + h * 0.28f, y - r * 0.9f, cx - h * 0.45f, cy - h * 0.85f, paint);
                break;
            }
            case SELECT_ALL: {
                // A dashed square: everything selected.
                float w = h * 0.80f;
                float step = w * 2.0f / 5.0f;
                for (int i = 0; i < 5; i += 2) {
                    float a = cx - w + i * step;
                    float b = a + step;
                    canvas.drawLine(a, cy - w, b, cy - w, paint);
                    canvas.drawLine(a, cy + w, b, cy + w, paint);
                    canvas.drawLine(cx - w, a - cx + cy, cx - w, b - cx + cy, paint);
                    canvas.drawLine(cx + w, a - cx + cy, cx + w, b - cx + cy, paint);
                }
                break;
            }
            case KEYBOARD: {
                float w = h * 0.95f;
                float t = h * 0.62f;
                oval.set(cx - w, cy - t, cx + w, cy + t);
                canvas.drawRoundRect(oval, t * 0.25f, t * 0.25f, paint);
                paint.setStyle(Paint.Style.FILL);
                float dot = Math.max(1.0f, size * 0.045f);
                for (int row = 0; row < 2; row++) {
                    for (int i = 0; i < 4; i++) {
                        float x = cx - w * 0.6f + i * w * 0.4f;
                        float y = cy - t * 0.38f + row * t * 0.42f;
                        canvas.drawCircle(x, y, dot, paint);
                    }
                }
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawLine(cx - w * 0.45f, cy + t * 0.45f, cx + w * 0.45f, cy + t * 0.45f,
                    paint);
                break;
            }
            case UNDO:
            case REDO: {
                // ↶ / ↷: an arc over the top, the arrowhead pointing down at the near end.
                boolean undo = icon == Icon.UNDO;
                float r = h * 0.62f;
                float oy = cy + h * 0.22f;
                oval.set(cx - r, oy - r, cx + r, oy + r);
                canvas.drawArc(oval, undo ? 180 : 0, undo ? 200 : -200, false, paint);
                float tipX = undo ? cx - r : cx + r;
                float head = h * 0.34f;
                path.moveTo(tipX - head, oy - head);
                path.lineTo(tipX, oy);
                path.lineTo(tipX + head, oy - head);
                canvas.drawPath(path, paint);
                break;
            }
            case FLOATING: {
                // A window with a smaller one floating over its corner.
                float w = h * 0.95f;
                float t = h * 0.70f;
                oval.set(cx - w, cy - t, cx + w, cy + t);
                canvas.drawRoundRect(oval, t * 0.22f, t * 0.22f, paint);
                paint.setStyle(Paint.Style.FILL);
                oval.set(cx + w * 0.05f, cy - t * 0.05f, cx + w * 0.78f, cy + t * 0.62f);
                canvas.drawRoundRect(oval, t * 0.14f, t * 0.14f, paint);
                break;
            }
            case THEME: {
                // Half a circle filled: light and dark.
                float r = h * 0.82f;
                canvas.drawCircle(cx, cy, r, paint);
                paint.setStyle(Paint.Style.FILL);
                oval.set(cx - r, cy - r, cx + r, cy + r);
                canvas.drawArc(oval, -90, 180, true, paint);
                break;
            }
            case DATE: {
                // A calendar page: binding rings, a header rule, a day marked.
                float w = h * 0.80f;
                float top = cy - h * 0.62f;
                float bottom = cy + h * 0.85f;
                oval.set(cx - w, top, cx + w, bottom);
                canvas.drawRoundRect(oval, w * 0.18f, w * 0.18f, paint);
                canvas.drawLine(cx - w, top + h * 0.45f, cx + w, top + h * 0.45f, paint);
                canvas.drawLine(cx - w * 0.45f, top - h * 0.22f, cx - w * 0.45f, top + h * 0.15f, paint);
                canvas.drawLine(cx + w * 0.45f, top - h * 0.22f, cx + w * 0.45f, top + h * 0.15f, paint);
                paint.setStyle(Paint.Style.FILL);
                float day = h * 0.22f;
                oval.set(cx + w * 0.15f, cy + h * 0.22f, cx + w * 0.15f + day * 1.6f,
                    cy + h * 0.22f + day * 1.6f);
                canvas.drawRoundRect(oval, day * 0.3f, day * 0.3f, paint);
                break;
            }
            case SIZE_UP:
            case SIZE_DOWN: {
                // A bar with chevrons above and below it: pointing away to grow, toward to shrink.
                boolean up = icon == Icon.SIZE_UP;
                float w = h * 0.80f;
                canvas.drawLine(cx - w, cy, cx + w, cy, paint);
                float chev = h * 0.36f;
                float near = h * 0.30f;
                float far = near + chev;
                // Upper chevron.
                path.moveTo(cx - chev, up ? cy - near : cy - far);
                path.lineTo(cx, up ? cy - far : cy - near);
                path.lineTo(cx + chev, up ? cy - near : cy - far);
                // Lower chevron.
                path.moveTo(cx - chev, up ? cy + near : cy + far);
                path.lineTo(cx, up ? cy + far : cy + near);
                path.lineTo(cx + chev, up ? cy + near : cy + far);
                canvas.drawPath(path, paint);
                break;
            }
            case UNICODE: {
                // "U+", the way a code point is written.
                paint.setStyle(Paint.Style.FILL);
                Paint.Align align = paint.getTextAlign();
                float textSize = paint.getTextSize();
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(size * 0.78f);
                paint.setFakeBoldText(true);
                canvas.drawText("U+", cx, cy - (paint.descent() + paint.ascent()) / 2.0f, paint);
                paint.setFakeBoldText(false);
                paint.setTextSize(textSize);
                paint.setTextAlign(align);
                break;
            }
            default:
                break;
        }
        paint.setStyle(style);
        paint.setStrokeWidth(stroke);
        paint.setStrokeCap(cap);
        paint.setStrokeJoin(join);
        paint.setColor(oldColor);
    }

    /** Gboard's backspace: a tag pointing left with an × in it. */
    private void backspace(Canvas canvas, Paint paint, float cx, float cy, float size) {
        float w = size * 0.56f;
        float t = size * 0.34f;
        float left = cx - w;
        float right = cx + w;
        float point = left + t * 0.95f;
        float r = t * 0.32f;
        path.moveTo(left, cy);
        path.lineTo(point, cy - t);
        path.lineTo(right - r, cy - t);
        path.quadTo(right, cy - t, right, cy - t + r);
        path.lineTo(right, cy + t - r);
        path.quadTo(right, cy + t, right - r, cy + t);
        path.lineTo(point, cy + t);
        path.close();
        canvas.drawPath(path, paint);
        float xc = (point + right) * 0.5f + t * 0.05f;
        float x = t * 0.40f;
        canvas.drawLine(xc - x, cy - x, xc + x, cy + x, paint);
        canvas.drawLine(xc - x, cy + x, xc + x, cy - x, paint);
    }

    /** The outlined up-arrow Gboard uses for Shift; filled while Shift is on. */
    private void shift(Canvas canvas, Paint paint, float cx, float cy, float size, boolean filled) {
        float h = size * 0.5f;
        float stem = h * 0.42f;
        float shoulder = cy + h * 0.02f;
        float top = cy - h * 0.92f;
        float bottom = cy + h * 0.70f;
        path.moveTo(cx, top);
        path.lineTo(cx + h * 0.88f, shoulder);
        path.lineTo(cx + stem, shoulder);
        path.lineTo(cx + stem, bottom);
        path.lineTo(cx - stem, bottom);
        path.lineTo(cx - stem, shoulder);
        path.lineTo(cx - h * 0.88f, shoulder);
        path.close();
        if (filled) {
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
        }
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.STROKE);
    }

    /** A gear: a ring with eight teeth and a hole. */
    private void gear(Canvas canvas, Paint paint, float cx, float cy, float r) {
        Paint.Style style = paint.getStyle();
        paint.setStyle(Paint.Style.FILL);
        int teeth = 8;
        float outer = r;
        float inner = r * 0.74f;
        path.reset();
        for (int i = 0; i < teeth * 2; i++) {
            double a0 = Math.PI * 2 * i / (teeth * 2) - Math.PI / (teeth * 2);
            double a1 = Math.PI * 2 * (i + 1) / (teeth * 2) - Math.PI / (teeth * 2);
            float rad = i % 2 == 0 ? outer : inner;
            float x0 = cx + (float) Math.cos(a0) * rad;
            float y0 = cy + (float) Math.sin(a0) * rad;
            float x1 = cx + (float) Math.cos(a1) * rad;
            float y1 = cy + (float) Math.sin(a1) * rad;
            if (i == 0) {
                path.moveTo(x0, y0);
            } else {
                path.lineTo(x0, y0);
            }
            path.lineTo(x1, y1);
        }
        path.close();
        path.addCircle(cx, cy, r * 0.32f, Path.Direction.CCW);
        path.setFillType(Path.FillType.EVEN_ODD);
        canvas.drawPath(path, paint);
        path.setFillType(Path.FillType.WINDING);
        paint.setStyle(style);
    }
}
