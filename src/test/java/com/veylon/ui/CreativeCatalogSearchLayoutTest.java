package com.veylon.ui;

import org.junit.jupiter.api.Test;

import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

/** R27: wide queries stay inside the search field while keeping the edited end visible. */
class CreativeCatalogSearchLayoutTest {

    @Test
    void wideQueryShowsItsFittingTailAndReservesSpaceForTheCaret() {
        String query = "W".repeat(30) + "ab";
        ToDoubleFunction<String> width = text -> text.length() * 12d;
        String tail = CreativeCatalogScreen.fittedQueryTail(query, width);
        assertTrue(width.applyAsDouble(tail) <= 296,
                "R27: query text leaves both padding and caret space inside the 320px field");
        assertTrue(width.applyAsDouble("W" + tail) > 296,
                "R27: the visible tail retains as much query text as fits");
        assertTrue(tail.endsWith("ab"), "R27: the most recently edited characters remain visible");
        assertEquals(32, query.length(), "R16: fitting does not shorten the actual search query");
    }

    @Test
    void fittingKeepsWholeCodePointsAndReusesAlreadyFittingText() {
        String query = "\uD83D\uDE00".repeat(32);
        ToDoubleFunction<String> width = text -> text.codePointCount(0, text.length()) * 12d;
        String tail = CreativeCatalogScreen.fittedQueryTail(query, width);
        assertEquals(24, tail.codePointCount(0, tail.length()),
                "R27: fitting counts full Unicode code points");
        assertTrue(Character.isHighSurrogate(tail.charAt(0)),
                "R27: fitting cannot leave an unmatched trailing surrogate");
        String shortQuery = "iron";
        assertSame(shortQuery, CreativeCatalogScreen.fittedQueryTail(shortQuery, width),
                "R27: an already fitting query needs no replacement string");
    }
}
