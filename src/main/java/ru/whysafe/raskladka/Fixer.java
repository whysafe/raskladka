package ru.whysafe.raskladka;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds words typed in the wrong keyboard layout and fixes them.
 *
 * <p>Each whitespace-separated token is translated to the other layout and both
 * versions are scored by the language models. Clear cases are decided at once;
 * short or ambiguous tokens ("z", "b") follow their confident neighbours, because
 * a wrong layout usually spans a whole phrase rather than a single word.
 */
public final class Fixer {

    public enum Mode { AUTO, TO_RUSSIAN, TO_ENGLISH }

    public enum Verdict { KEEP, CONVERT, UNSURE, SKIP }

    /** One token with the evidence used to decide its fate. */
    public record Decision(int index, int start, int end, String original, String converted,
                           double originalScore, double convertedScore, Verdict verdict, String reason) {

        public boolean converts() {
            return verdict == Verdict.CONVERT;
        }
    }

    /** A fixed span. Translation keeps length, so offsets are valid in both texts. */
    public record Change(int tokenIndex, int start, int end, String original, String fixed,
                         boolean layout, boolean caps) {
    }

    public record Result(String text, List<Change> changes, int wordCount) {
    }

    /** Difference in average log-probability per letter needed for a confident decision. */
    static final double MARGIN = 0.45;
    private static final int MIN_LETTERS_FOR_SCORING = 3;

    private static final Pattern TOKEN = Pattern.compile("\\S+");
    private static final Pattern LOOKS_TECHNICAL = Pattern.compile(
            "://|^www\\.|\\p{L}@\\p{L}|^#\\w|^@\\w|\\d|\\.(com|ru|org|net|io|рф)\\b|[\\\\_]");

    private final LanguageModel russian;
    private final LanguageModel english;

    public Fixer(LanguageModel russian, LanguageModel english) {
        this.russian = russian;
        this.english = english;
    }

    public static Fixer withBundledModels() {
        return new Fixer(LanguageModel.russian(), LanguageModel.english());
    }

    public Result fix(String text, Mode mode, boolean fixCaps) {
        return fix(text, mode, fixCaps, Collections.emptySet());
    }

    /**
     * @param keepTokens indexes of tokens the user explicitly asked to leave untouched
     */
    public Result fix(String text, Mode mode, boolean fixCaps, Set<Integer> keepTokens) {
        List<Decision> decisions = analyze(text, mode);
        StringBuilder out = new StringBuilder(text);
        List<Change> changes = new ArrayList<>();
        int words = 0;
        for (Decision d : decisions) {
            if (d.verdict() == Verdict.SKIP) {
                continue;
            }
            words++;
            if (keepTokens.contains(d.index())) {
                continue;
            }
            boolean layout = d.converts();
            String fixed = layout ? d.converted() : d.original();
            boolean caps = fixCaps && hasInvertedCaps(fixed);
            if (caps) {
                fixed = swapCase(fixed);
            }
            if (layout || caps) {
                out.replace(d.start(), d.end(), fixed);
                changes.add(new Change(d.index(), d.start(), d.end(), d.original(), fixed, layout, caps));
            }
        }
        return new Result(out.toString(), changes, words);
    }

    public List<Decision> analyze(String text, Mode mode) {
        List<Decision> decisions = new ArrayList<>();
        Matcher m = TOKEN.matcher(text);
        int index = 0;
        while (m.find()) {
            decisions.add(judge(index++, m.start(), m.end(), m.group(), mode));
        }
        resolveUnsure(decisions);
        return decisions;
    }

    private Decision judge(int index, int start, int end, String token, Mode mode) {
        Script script = Script.of(token);
        if (script == Script.NONE || script == Script.MIXED || LOOKS_TECHNICAL.matcher(token).find()) {
            return new Decision(index, start, end, token, token, 0, 0, Verdict.SKIP, "не слово");
        }
        boolean latin = script == Script.LATIN;
        LanguageModel own = latin ? english : russian;
        LanguageModel other = latin ? russian : english;
        String converted = latin ? Layout.toRussian(token) : Layout.toEnglish(token);
        double ownScore = own.score(token);
        double otherScore = other.ownsLetters(converted) ? other.score(converted) : Double.NEGATIVE_INFINITY;

        if (mode != Mode.AUTO) {
            boolean wanted = (mode == Mode.TO_RUSSIAN) == latin;
            return new Decision(index, start, end, token, converted, ownScore, otherScore,
                    wanted ? Verdict.CONVERT : Verdict.KEEP, "ручной режим");
        }
        Verdict verdict;
        String reason;
        if (own.isKnownWord(token)) {
            verdict = Verdict.KEEP;
            reason = "словарь " + own.name();
        } else if (isAbbreviation(token) && !other.isKnownWord(converted)) {
            return new Decision(index, start, end, token, converted, ownScore, otherScore, Verdict.SKIP,
                    "аббревиатура");
        } else if (letterCount(token) < MIN_LETTERS_FOR_SCORING) {
            verdict = Verdict.UNSURE;
            reason = "короткое";
        } else if (otherScore > Double.NEGATIVE_INFINITY && other.isKnownWord(converted)) {
            verdict = Verdict.CONVERT;
            reason = "словарь " + other.name();
        } else if (otherScore - ownScore > MARGIN) {
            verdict = Verdict.CONVERT;
            reason = "триграммы";
        } else if (ownScore - otherScore > MARGIN) {
            verdict = Verdict.KEEP;
            reason = "триграммы";
        } else {
            verdict = Verdict.UNSURE;
            reason = "неясно";
        }
        return new Decision(index, start, end, token, converted, ownScore, otherScore, verdict, reason);
    }

    /**
     * Unsure tokens take the side of the nearest confident words around them.
     * Neighbours vote for the language the writer meant, not for "convert" or "keep":
     * a fixed Latin word next to a Cyrillic one means both were meant as Russian.
     */
    private void resolveUnsure(List<Decision> decisions) {
        List<Decision> snapshot = new ArrayList<>(decisions);
        for (int i = 0; i < decisions.size(); i++) {
            Decision d = decisions.get(i);
            if (d.verdict() != Verdict.UNSURE) {
                continue;
            }
            Script left = nearestIntendedScript(snapshot, i, -1);
            Script right = nearestIntendedScript(snapshot, i, +1);
            boolean convert;
            String reason;
            if (left == null && right == null) {
                convert = d.convertedScore() - d.originalScore() > MARGIN;
                reason = "без соседей";
            } else if (left == null || right == null || left == right) {
                Script meant = left != null ? left : right;
                // Long ambiguous words are often names (Tesla, Lada): neighbours only tip the scale.
                boolean leansOther = letterCount(d.original()) < MIN_LETTERS_FOR_SCORING
                        || d.convertedScore() > d.originalScore();
                convert = meant != Script.of(d.original()) && leansOther;
                reason = "по соседям";
            } else {
                convert = d.convertedScore() - d.originalScore() > MARGIN;
                reason = "соседи спорят";
            }
            decisions.set(i, new Decision(d.index(), d.start(), d.end(), d.original(), d.converted(),
                    d.originalScore(), d.convertedScore(), convert ? Verdict.CONVERT : Verdict.KEEP, reason));
        }
    }

    private static Script nearestIntendedScript(List<Decision> decisions, int from, int step) {
        for (int i = from + step; i >= 0 && i < decisions.size(); i += step) {
            Decision d = decisions.get(i);
            if (d.verdict() == Verdict.KEEP || d.verdict() == Verdict.CONVERT) {
                Script typed = Script.of(d.original());
                if (!d.converts()) {
                    return typed;
                }
                return typed == Script.LATIN ? Script.CYRILLIC : Script.LATIN;
            }
        }
        return null;
    }

    /** HTML, NASA, ГОСТ: trigram statistics say nothing about such words. */
    private static boolean isAbbreviation(String token) {
        int letters = letterCount(token);
        return letters >= 2 && letters <= 5 && token.chars().filter(Character::isLetter).allMatch(Character::isUpperCase);
    }

    private static int letterCount(String token) {
        int count = 0;
        for (int i = 0; i < token.length(); i++) {
            if (Character.isLetter(token.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    /** "пРИВЕТ": first letter lower, all the others upper — the Caps Lock signature. */
    static boolean hasInvertedCaps(String token) {
        int letters = 0;
        boolean firstLower = false;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (!Character.isLetter(c)) {
                continue;
            }
            if (letters == 0) {
                firstLower = Character.isLowerCase(c);
            } else if (!Character.isUpperCase(c)) {
                return false;
            }
            letters++;
        }
        return firstLower && letters >= 2;
    }

    static String swapCase(String token) {
        StringBuilder out = new StringBuilder(token.length());
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            out.append(Character.isUpperCase(c) ? Character.toLowerCase(c) : Character.toUpperCase(c));
        }
        return out.toString();
    }

    private enum Script {
        NONE, LATIN, CYRILLIC, MIXED;

        static Script of(String token) {
            boolean latin = false;
            boolean cyrillic = false;
            for (int i = 0; i < token.length(); i++) {
                char c = token.charAt(i);
                if (Layout.isLatinLetter(c)) {
                    latin = true;
                } else if (Layout.isCyrillicLetter(c)) {
                    cyrillic = true;
                } else if (Character.isLetter(c)) {
                    return MIXED;
                }
            }
            if (latin && cyrillic) {
                return MIXED;
            }
            return latin ? LATIN : cyrillic ? CYRILLIC : NONE;
        }
    }
}
