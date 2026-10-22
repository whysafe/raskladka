package ru.whysafe.raskladka;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageModelTest {

    private static LanguageModel ru;
    private static LanguageModel en;

    @BeforeAll
    static void load() {
        ru = LanguageModel.russian();
        en = LanguageModel.english();
    }

    @Test
    void realWordsScoreHigherThanKeyboardMash() {
        assertTrue(ru.score("привет") > ru.score(Layout.toRussian("hello")));
        assertTrue(en.score("hello") > en.score(Layout.toEnglish("привет")));
    }

    @Test
    void unseenButPlausibleWordsStillLookNative() {
        assertTrue(ru.score("переподключение") > en.score(Layout.toEnglish("переподключение")));
        assertTrue(en.score("reconnection") > ru.score(Layout.toRussian("reconnection")));
    }

    @Test
    void knowsFrequentWordsIgnoringCaseAndPunctuation() {
        assertTrue(ru.isKnownWord("Спасибо!"));
        assertTrue(ru.isKnownWord("ещё"));
        assertTrue(en.isKnownWord("THANKS,"));
        assertFalse(en.isKnownWord("ghbdtn"));
    }

    @Test
    void ownsOnlyItsAlphabet() {
        assertTrue(ru.ownsLetters("слово."));
        assertFalse(ru.ownsLetters("word"));
        assertFalse(en.ownsLetters("123"));
    }

    @Test
    void scoreOfTextWithoutLettersIsNegativeInfinity() {
        assertEquals(Double.NEGATIVE_INFINITY, en.score("42!"));
    }

    @Test
    void trainsFromTextInMemory() {
        LanguageModel tiny = LanguageModel.train("xx", c -> Layout.isLatinLetter((char) c), 26, "abab abab", "ab");
        assertTrue(tiny.isKnownWord("AB"));
        assertTrue(tiny.score("abab") > tiny.score("zqzq"));
    }
}
