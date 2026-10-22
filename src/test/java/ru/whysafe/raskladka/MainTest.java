package ru.whysafe.raskladka;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    private int run(String... args) throws Exception {
        return Main.run(args,
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));
    }

    private String out() {
        return stdout.toString(StandardCharsets.UTF_8);
    }

    @Test
    void fixesArguments() throws Exception {
        assertEquals(0, run("Ghbdtn?", "rfr", "ltkf&"));
        assertEquals("Привет, как дела?" + System.lineSeparator(), out());
    }

    @Test
    void forcedDirection() throws Exception {
        run("--to", "en", "руддщ");
        assertEquals("hello", out().strip());
    }

    @Test
    void keepCapsFlag() throws Exception {
        run("--keep-caps", "пРИВЕТ");
        assertEquals("пРИВЕТ", out().strip());
    }

    @Test
    void explainPrintsATable() throws Exception {
        run("--explain", "ghbdtn", "мир");
        String table = out();
        assertTrue(table.contains("привет"));
        assertTrue(table.contains("исправить"));
        assertTrue(table.contains("оставить"));
    }

    @Test
    void doubleDashStopsOptionParsing() throws Exception {
        run("--", "--to");
        assertEquals("--to", out().strip());
    }

    @Test
    void helpAndVersion() throws Exception {
        run("--help");
        assertTrue(out().contains("--clip"));
        stdout.reset();
        run("--version");
        assertEquals("raskladka " + Main.VERSION, out().strip());
    }

    @Test
    void rejectsUnknownOptionsAndBadValues() {
        assertThrows(Main.UsageException.class, () -> run("--wat"));
        assertThrows(Main.UsageException.class, () -> run("--to", "de", "x"));
        assertThrows(Main.UsageException.class, () -> run("--to"));
    }
}
