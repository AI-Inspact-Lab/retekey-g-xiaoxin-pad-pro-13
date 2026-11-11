package com.retekey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/** The Gboard-style pages: Gboard's tablet frame, on an eighths-of-a-key grid. */
public class GboardLayoutTest {

    private static KeyboardLayout gboard(KeyboardLayoutId id, boolean shifted) {
        boolean before = KeyboardLayouts.gboardArrangement();
        KeyboardLayouts.setGboardArrangement(true);
        try {
            return KeyboardLayouts.of(id, shifted);
        } finally {
            KeyboardLayouts.setGboardArrangement(before);
        }
    }

    private static KeyboardLayout symbols(boolean more) {
        boolean before = KeyboardLayouts.gboardArrangement();
        KeyboardLayouts.setGboardArrangement(true);
        try {
            return KeyboardLayouts.specialChars(more);
        } finally {
            KeyboardLayouts.setGboardArrangement(before);
        }
    }

    @Test
    public void theClassicGridIsWhatTheJvmSeesUntilAViewAsks() {
        assertFalse(KeyboardLayouts.gboardArrangement());
        assertEquals(KeyboardLayouts.COLUMNS, KeyboardLayouts.of(KeyboardLayoutId.KO_DUBEOLSIK,
            false).columns());
    }

    @Test
    public void everyGboardPageIsTwelveKeysAcrossInEighths() {
        KeyboardLayout[] pages = {
            gboard(KeyboardLayoutId.KO_DUBEOLSIK, false), gboard(KeyboardLayoutId.KO_DUBEOLSIK, true),
            gboard(KeyboardLayoutId.EN_QWERTY, false), gboard(KeyboardLayoutId.EN_QWERTY, true),
            symbols(false), symbols(true),
        };
        for (KeyboardLayout page : pages) {
            assertEquals(KeyboardLayouts.G_COLUMNS, page.columns());
            assertEquals(KeyboardLayouts.G_UNIT, page.unit());
            assertEquals(12, page.keyColumns());
            assertEquals(4, page.rows().size());
        }
    }

    @Test
    public void twoBeolsikSitsInGboardsTabletFrame() {
        KeyboardLayout ko = gboard(KeyboardLayoutId.KO_DUBEOLSIK, false);
        List<SoftwareKeySpec> top = ko.rows().get(0);
        assertEquals("touch.edit.tab", top.get(0).stableKeyId());
        assertEquals("ㅂ", top.get(1).label());
        assertEquals("ㅔ", top.get(10).label());
        assertEquals("touch.edit.backspace", top.get(11).stableKeyId());
        // The top row's letters hold the digits, Gboard's corner numbers.
        assertEquals("1", top.get(1).longPressTexts().get(0));
        assertEquals("0", top.get(10).longPressTexts().get(0));

        List<SoftwareKeySpec> home = ko.rows().get(1);
        assertSame(ControlKey.SHIFT_LOCK, home.get(0).control());
        assertEquals(10, home.get(0).columnSpan());
        assertEquals("ㅁ", home.get(1).label());
        assertEquals("touch.edit.enter", home.get(home.size() - 1).stableKeyId());
        assertEquals(14, home.get(home.size() - 1).columnSpan());

        List<SoftwareKeySpec> third = ko.rows().get(2);
        assertSame(ControlKey.SHIFT, third.get(0).control());
        assertSame(ControlKey.SHIFT, third.get(third.size() - 1).control());
        assertEquals(",", third.get(8).label());
        assertEquals(".", third.get(9).label());

        List<SoftwareKeySpec> bottom = ko.rows().get(3);
        assertSame(ControlKey.SPECIAL_CHARS_LAYER, bottom.get(0).control());
        assertSame(ControlKey.CTRL, bottom.get(1).control());
        assertSame(ControlKey.LAYOUT_TOGGLE, bottom.get(2).control());
        assertEquals("touch.text.space", bottom.get(3).stableKeyId());
        assertSame(ControlKey.HANJA, bottom.get(6).control());
    }

    @Test
    public void theRowsStaggerByAQuarterKeyAsGboardsDo() {
        KeyboardLayout ko = gboard(KeyboardLayoutId.KO_DUBEOLSIK, false);
        assertEquals(8, ko.startColumn(0, 1));   // ㅂ: one key in, after Tab
        assertEquals(10, ko.startColumn(1, 1));  // ㅁ: 1¼ keys in, after Caps
        assertEquals(14, ko.startColumn(2, 1));  // ㅋ: 1¾ keys in, after Shift
    }

    @Test
    public void stableIdsAreUniqueOnEveryGboardPage() {
        KeyboardLayout[] pages = {
            gboard(KeyboardLayoutId.KO_DUBEOLSIK, false), gboard(KeyboardLayoutId.EN_QWERTY, true),
            symbols(false), symbols(true),
        };
        for (KeyboardLayout page : pages) {
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (List<SoftwareKeySpec> row : page.rows()) {
                for (SoftwareKeySpec key : row) {
                    assertTrue(key.stableKeyId(), seen.add(key.stableKeyId()));
                }
            }
        }
    }

    @Test
    public void theSymbolsPageHasTheDigitsOnTopAndTurnsToASecondHalf() {
        KeyboardLayout first = symbols(false);
        assertEquals("1", first.rows().get(0).get(1).label());
        assertEquals("!", first.rows().get(0).get(1).longPressTexts().get(0));
        assertEquals("0", first.rows().get(0).get(10).label());
        assertSame(ControlKey.MORE_SYMBOLS, first.rows().get(2).get(0).control());
        assertSame(ControlKey.PREVIOUS_LAYER, first.rows().get(3).get(0).control());
        KeyboardLayout second = symbols(true);
        assertNotNull(second.findById("touch.sym.backslash"));
        assertNotNull(second.findById("touch.sym.lbrace"));
    }

    @Test
    public void aHoldStripIsLaidOutInWholeKeys() {
        // A key in the middle of the 12-key-wide top row with four alternates: the strip runs
        // right, from the key's own column, measured in keys rather than in eighths.
        HoldPicker picker = HoldPicker.place(12, 1, 5, 1, 4);
        assertEquals(0, picker.stripRow);
        assertEquals(5, picker.stripStart);
    }

    private static KeyboardLayout menu() {
        boolean before = KeyboardLayouts.gboardArrangement();
        KeyboardLayouts.setGboardArrangement(true);
        try {
            return KeyboardLayouts.menu();
        } finally {
            KeyboardLayouts.setGboardArrangement(before);
        }
    }

    @Test
    public void theMenuHasNoDeadCellsAndEveryTileDoesSomething() {
        KeyboardLayout page = menu();
        assertEquals(KeyboardLayouts.G_COLUMNS, page.columns());
        java.util.Set<ControlKey> tiles = java.util.EnumSet.noneOf(ControlKey.class);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (List<SoftwareKeySpec> row : page.rows()) {
            for (SoftwareKeySpec key : row) {
                assertTrue(key.stableKeyId(), key.enabled() || key.isControl());
                assertTrue(key.stableKeyId(), seen.add(key.stableKeyId()));
                if (key.stableKeyId().startsWith(KeyboardLayouts.G_TILE_PREFIX)) {
                    assertTrue(key.isControl());
                    tiles.add(key.control());
                }
            }
        }
        for (ControlKey wanted : new ControlKey[] {
            ControlKey.UNDO, ControlKey.REDO, ControlKey.CLIPBOARD, ControlKey.NOTEPAD,
            ControlKey.HEIGHT_DOWN, ControlKey.HEIGHT_UP, ControlKey.FLOATING_TOGGLE,
            ControlKey.THEME_CYCLE, ControlKey.INSERT_DATE, ControlKey.UNICODE_INPUT,
            ControlKey.SWITCH_IME, ControlKey.OPEN_SETTINGS, ControlKey.CUT, ControlKey.COPY,
            ControlKey.PASTE, ControlKey.SELECT_ALL}) {
            assertTrue(wanted.name(), tiles.contains(wanted));
        }
        // The bottom row is the letters' own: the way back, Ctrl, the globe, space, ◀ ▶.
        List<SoftwareKeySpec> bottom = page.rows().get(3);
        assertSame(ControlKey.PREVIOUS_LAYER, bottom.get(0).control());
        assertSame(ControlKey.CTRL, bottom.get(1).control());
        assertEquals("touch.text.space", bottom.get(3).stableKeyId());
    }
}
