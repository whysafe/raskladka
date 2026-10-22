package ru.whysafe.raskladka;

import ru.whysafe.raskladka.ui.MainWindow;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Main {

    static final String VERSION = "1.0.0";

    private static final String USAGE = """
            Раскладка %s: исправляет текст, набранный не в той раскладке.

            Использование:
              raskladka                       открыть окно
              raskladka ТЕКСТ...              исправить текст из аргументов
              raskladka -                     исправить текст из stdin
              raskladka --clip                исправить текст в буфере обмена на месте

            Параметры:
              --to ru|en       переводить всё в одну раскладку, без автоопределения
              --keep-caps      не исправлять слова, набранные с нажатым Caps Lock
              --explain        показать решение и оценки по каждому слову
              --screenshot F   сохранить снимок окна с примером в PNG и выйти
              -h, --help       эта справка
              -v, --version    версия
            """;

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
        try {
            System.exit(run(args, out, err));
        } catch (UsageException e) {
            err.println("raskladka: " + e.getMessage());
            err.println("Справка: raskladka --help");
            System.exit(2);
        }
    }

    static int run(String[] args, PrintStream out, PrintStream err) throws Exception {
        Options options = Options.parse(args);
        if (options.help) {
            out.print(USAGE.formatted(VERSION));
            return 0;
        }
        if (options.version) {
            out.println("raskladka " + VERSION);
            return 0;
        }
        if (options.screenshot != null) {
            MainWindow.saveScreenshot(Path.of(options.screenshot));
            out.println("Снимок сохранён: " + options.screenshot);
            return 0;
        }
        boolean noInput = options.words.isEmpty() && !options.stdin && !options.clipboard;
        if (noInput && !GraphicsEnvironment.isHeadless()) {
            MainWindow.open();
            return 0;
        }

        Fixer fixer = Fixer.withBundledModels();
        String text = readInput(options);
        if (options.explain) {
            printExplanation(fixer.analyze(text, options.mode), out);
            return 0;
        }
        Fixer.Result result = fixer.fix(text, options.mode, options.fixCaps);
        if (options.clipboard) {
            systemClipboard().setContents(new StringSelection(result.text()), null);
            err.printf("Исправлено слов: %d из %d%n", result.changes().size(), result.wordCount());
        }
        out.print(result.text());
        if (!options.stdin && !result.text().endsWith("\n")) {
            out.println();
        }
        return 0;
    }

    private static String readInput(Options options) throws IOException {
        if (options.clipboard) {
            try {
                Object data = systemClipboard().getData(DataFlavor.stringFlavor);
                return data == null ? "" : data.toString();
            } catch (Exception e) {
                throw new UsageException("в буфере обмена нет текста");
            }
        }
        if (options.stdin || options.words.isEmpty()) {
            return new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        }
        return String.join(" ", options.words);
    }

    private static Clipboard systemClipboard() {
        if (GraphicsEnvironment.isHeadless()) {
            throw new UsageException("буфер обмена недоступен без графической среды");
        }
        return Toolkit.getDefaultToolkit().getSystemClipboard();
    }

    private static void printExplanation(List<Fixer.Decision> decisions, PrintStream out) {
        out.printf("%-18s %-18s %8s %8s  %-9s %s%n", "слово", "вариант", "своё", "чужое", "решение", "почему");
        for (Fixer.Decision d : decisions) {
            out.printf(Locale.ROOT, "%-18s %-18s %8s %8s  %-9s %s%n",
                    clip(d.original()), clip(d.converted()), format(d.originalScore()), format(d.convertedScore()),
                    verdictName(d.verdict()), d.reason());
        }
    }

    private static String verdictName(Fixer.Verdict verdict) {
        return switch (verdict) {
            case KEEP -> "оставить";
            case CONVERT -> "исправить";
            case UNSURE -> "неясно";
            case SKIP -> "пропуск";
        };
    }

    private static String format(double score) {
        return Double.isInfinite(score) ? "—" : String.format(Locale.ROOT, "%.2f", score);
    }

    private static String clip(String s) {
        return s.length() <= 18 ? s : s.substring(0, 17) + "…";
    }

    static final class UsageException extends RuntimeException {
        UsageException(String message) {
            super(message);
        }
    }

    static final class Options {
        Fixer.Mode mode = Fixer.Mode.AUTO;
        boolean fixCaps = true;
        boolean explain;
        boolean clipboard;
        boolean stdin;
        boolean help;
        boolean version;
        String screenshot;
        final List<String> words = new ArrayList<>();

        static Options parse(String[] args) {
            Options o = new Options();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "-h", "--help" -> o.help = true;
                    case "-v", "--version" -> o.version = true;
                    case "-c", "--clip" -> o.clipboard = true;
                    case "--explain" -> o.explain = true;
                    case "--keep-caps" -> o.fixCaps = false;
                    case "-" -> o.stdin = true;
                    case "--to" -> o.mode = parseMode(valueAfter(args, ++i, arg));
                    case "--screenshot" -> o.screenshot = valueAfter(args, ++i, arg);
                    case "--" -> {
                        for (i++; i < args.length; i++) {
                            o.words.add(args[i]);
                        }
                    }
                    default -> {
                        if (arg.startsWith("--")) {
                            throw new UsageException("неизвестный параметр " + arg);
                        }
                        o.words.add(arg);
                    }
                }
            }
            return o;
        }

        private static String valueAfter(String[] args, int i, String flag) {
            if (i >= args.length) {
                throw new UsageException("после " + flag + " нужно значение");
            }
            return args[i];
        }

        private static Fixer.Mode parseMode(String value) {
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "ru" -> Fixer.Mode.TO_RUSSIAN;
                case "en" -> Fixer.Mode.TO_ENGLISH;
                default -> throw new UsageException("--to принимает ru или en, а не " + value);
            };
        }
    }
}
