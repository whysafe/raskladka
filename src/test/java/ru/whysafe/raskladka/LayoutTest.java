package ru.whysafe.raskladka;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutTest {

    @Test
    void translatesLettersBothWays() {
        assertEquals("привет", Layout.toRussian("ghbdtn"));
        assertEquals("ghbdtn", Layout.toEnglish("привет"));
    }

    @Test
    void keepsCase() {
        assertEquals("ПРИВЕТ Мир", Layout.toRussian("GHBDTN Vbh"));
    }

    @Test
    void mapsKeysThatAreLettersOnlyInRussian() {
        assertEquals("хъжэбюё", Layout.toRussian("[];',.`"));
        assertEquals("ХЪЖЭБЮЁ", Layout.toRussian("{}:\"<>~"));
    }

    @Test
    void mapsRussianPunctuationKeys() {
        assertEquals("Привет, как дела?", Layout.toRussian("Ghbdtn? rfr ltkf&"));
        assertEquals("№;:.", Layout.toRussian("#$^/"));
    }

    @Test
    void leavesUnmappedCharactersAlone() {
        assertEquals("123 !-+= ", Layout.toRussian("123 !-+= "));
        assertEquals("123 !-+= ", Layout.toEnglish("123 !-+= "));
    }

    @Test
    void roundTripIsLossless() {
        String typed = "Ghbdtn? rfr ltkf& Z ;lfk nt,z [jhjij/";
        assertEquals(typed, Layout.toEnglish(Layout.toRussian(typed)));
    }

    @Test
    void recognisesScripts() {
        assertTrue(Layout.isLatinLetter('q'));
        assertTrue(Layout.isCyrillicLetter('ё'));
        assertFalse(Layout.isLatinLetter('й'));
        assertFalse(Layout.isCyrillicLetter('1'));
    }
}
