package ru.whysafe.raskladka;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixerTest {

    private static Fixer fixer;

    @BeforeAll
    static void load() {
        fixer = Fixer.withBundledModels();
    }

    private static String auto(String text) {
        return fixer.fix(text, Fixer.Mode.AUTO, true).text();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "ghbdtn | привет",
            "Ghbdtn? rfr ltkf& | Привет, как дела?",
            "Crbym afqk lj dtxthf | Скинь файл до вечера",
            "z gjcvjnh. pfdnhf | я посмотрю завтра",
            "руддщ цщкдв | hello world",
            "ыуу нщг ещьщккщц | see you tomorrow",
            "Gthtgjlrk.xtybt hfcrkflrb | Переподключение раскладки",
    })
    void fixesWrongLayout(String typed, String meant) {
        assertEquals(meant, auto(typed));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "Привет, как дела?",
            "See you tomorrow, my friend.",
            "Я купил iPhone и Tesla",
            "Обновите Windows и перезагрузите компьютер",
            "plan b",
            "Мы поехали на Lada Vesta в Kazan",
    })
    void leavesCorrectTextAlone(String text) {
        assertEquals(text, auto(text));
    }

    @Test
    void fixesOnlyTheWrongPartOfMixedText() {
        assertEquals("Встреча в пятницу, see you!", auto("Встреча в пятницу, ыуу нщг!"));
        assertEquals("Ок, я понял тебя", auto("Ок, z gjyzk nt,z"));
    }

    @Test
    void shortWordsFollowTheirNeighbours() {
        assertEquals("и я тоже", auto("b z nj;t"));
        assertEquals("Hi, I am here", auto("Hi, I am here"));
    }

    @Test
    void skipsUrlsEmailsNumbersAndCode() {
        String text = "https://ghbdtn.ru mail@ghbdtn.ru 2026 snake_case #ghbdtn";
        assertEquals(text, auto(text));
    }

    @Test
    void fixesCapsLock() {
        assertEquals("Привет Мир", auto("пРИВЕТ мИР"));
        assertEquals("Привет", auto("gHBDTN"));
    }

    @Test
    void capsFixCanBeTurnedOff() {
        assertEquals("пРИВЕТ", fixer.fix("пРИВЕТ", Fixer.Mode.AUTO, false).text());
    }

    @Test
    void allCapsWordsAreNotCapsLock() {
        assertEquals("СРОЧНО НУЖЕН NASA", auto("СРОЧНО НУЖЕН NASA"));
    }

    @Test
    void abbreviationsStayUnlessTheyAreKnownWords() {
        assertEquals("Учу HTML, API и ГОСТ", auto("Учу HTML, API и ГОСТ"));
        assertEquals("НЕТ ПРИВЕТ", auto("YTN GHBDTN"));
    }

    @Test
    void manualModesConvertEverythingInOneDirection() {
        assertEquals("hello? world", fixer.fix("руддщ, цщкдв", Fixer.Mode.TO_ENGLISH, true).text());
        assertEquals("ыуу привет", fixer.fix("ыуу ghbdtn", Fixer.Mode.TO_RUSSIAN, true).text());
    }

    @Test
    void reportsChangesWithOffsets() {
        Fixer.Result result = fixer.fix("Ok ghbdtn пРИВЕТ", Fixer.Mode.AUTO, true);
        assertEquals(3, result.wordCount());
        List<Fixer.Change> changes = result.changes();
        assertEquals(2, changes.size());

        Fixer.Change layout = changes.get(0);
        assertEquals(3, layout.start());
        assertEquals(9, layout.end());
        assertEquals("ghbdtn", layout.original());
        assertEquals("привет", layout.fixed());
        assertTrue(layout.layout());
        assertFalse(layout.caps());

        Fixer.Change caps = changes.get(1);
        assertTrue(caps.caps());
        assertFalse(caps.layout());
        assertEquals("Привет", result.text().substring(caps.start(), caps.end()));
    }

    @Test
    void keptTokensAreLeftAsTyped() {
        Fixer.Result result = fixer.fix("ghbdtn vbh", Fixer.Mode.AUTO, true, Set.of(1));
        assertEquals("привет vbh", result.text());
        assertEquals(1, result.changes().size());
    }

    @Test
    void preservesWhitespaceAndLineBreaks() {
        assertEquals("привет\n\n  мир\t!", auto("ghbdtn\n\n  vbh\t!"));
    }

    @Test
    void emptyTextHasNoWords() {
        Fixer.Result result = fixer.fix("", Fixer.Mode.AUTO, true);
        assertEquals("", result.text());
        assertEquals(0, result.wordCount());
    }

    @Test
    void explainsEachDecision() {
        List<Fixer.Decision> decisions = fixer.analyze("ghbdtn 42", Fixer.Mode.AUTO);
        assertEquals(Fixer.Verdict.CONVERT, decisions.get(0).verdict());
        assertEquals("привет", decisions.get(0).converted());
        assertEquals(Fixer.Verdict.SKIP, decisions.get(1).verdict());
    }

    @Test
    void detectsInvertedCapsSignature() {
        assertTrue(Fixer.hasInvertedCaps("пРИВЕТ,"));
        assertFalse(Fixer.hasInvertedCaps("ПРИВЕТ"));
        assertFalse(Fixer.hasInvertedCaps("привет"));
        assertFalse(Fixer.hasInvertedCaps("iPhone"));
        assertFalse(Fixer.hasInvertedCaps("я"));
    }
}
