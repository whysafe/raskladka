package ru.whysafe.raskladka;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Character trigram model of one language plus a list of its frequent words.
 * Scores tell how much a word "looks like" the language: real words and
 * plausible names score high, keyboard mash from the wrong layout scores low.
 */
public final class LanguageModel {

    private static final char BOUNDARY = '^';
    private static final double SMOOTHING = 0.1;

    private final String name;
    private final IntPredicate isAlphabetLetter;
    private final Map<String, Integer> trigrams = new HashMap<>();
    private final Map<String, Integer> contexts = new HashMap<>();
    private final Set<String> words = new HashSet<>();
    private final int vocabularySize;

    private LanguageModel(String name, IntPredicate isAlphabetLetter, int alphabetSize) {
        this.name = name;
        this.isAlphabetLetter = isAlphabetLetter;
        this.vocabularySize = alphabetSize + 1;
    }

    public static LanguageModel russian() {
        return fromResources("ru", c -> Layout.isCyrillicLetter((char) c), 33);
    }

    public static LanguageModel english() {
        return fromResources("en", c -> Layout.isLatinLetter((char) c), 26);
    }

    /** Builds a model from in-memory text, used by tests and by the resource loader. */
    public static LanguageModel train(String name, IntPredicate isAlphabetLetter, int alphabetSize,
                                      String corpus, String wordList) {
        LanguageModel model = new LanguageModel(name, isAlphabetLetter, alphabetSize);
        for (String word : model.splitWords(wordList)) {
            model.words.add(word);
            model.learn(word);
        }
        for (String word : model.splitWords(corpus)) {
            model.learn(word);
        }
        return model;
    }

    public String name() {
        return name;
    }

    public boolean isKnownWord(String word) {
        return words.contains(normalize(word));
    }

    /** True when every letter of the word belongs to this language's alphabet. */
    public boolean ownsLetters(String word) {
        boolean hasLetters = false;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (Character.isLetter(c)) {
                if (!isAlphabetLetter.test(c)) {
                    return false;
                }
                hasLetters = true;
            }
        }
        return hasLetters;
    }

    /**
     * Average log-probability per character, boundaries included.
     * Comparable between models, because each one is normalised by its own alphabet.
     */
    public double score(String word) {
        String letters = normalize(word);
        if (letters.isEmpty()) {
            return Double.NEGATIVE_INFINITY;
        }
        String padded = "" + BOUNDARY + BOUNDARY + letters + BOUNDARY;
        double total = 0;
        for (int i = 2; i < padded.length(); i++) {
            String context = padded.substring(i - 2, i);
            String trigram = padded.substring(i - 2, i + 1);
            double count = trigrams.getOrDefault(trigram, 0) + SMOOTHING;
            double contextCount = contexts.getOrDefault(context, 0) + SMOOTHING * vocabularySize;
            total += Math.log(count / contextCount);
        }
        return total / (padded.length() - 2);
    }

    /** Lower-cased letters of the word that belong to the alphabet, everything else dropped. */
    String normalize(String word) {
        StringBuilder out = new StringBuilder(word.length());
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (isAlphabetLetter.test(c)) {
                char lower = Character.toLowerCase(c);
                out.append(lower == 'ё' ? 'е' : lower);
            }
        }
        return out.toString();
    }

    private void learn(String word) {
        String padded = "" + BOUNDARY + BOUNDARY + word + BOUNDARY;
        for (int i = 2; i < padded.length(); i++) {
            trigrams.merge(padded.substring(i - 2, i + 1), 1, Integer::sum);
            contexts.merge(padded.substring(i - 2, i), 1, Integer::sum);
        }
    }

    private List<String> splitWords(String text) {
        List<String> result = new ArrayList<>();
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^\\p{L}]+")) {
            String word = normalize(raw);
            if (!word.isEmpty()) {
                result.add(word);
            }
        }
        return result;
    }

    private static LanguageModel fromResources(String code, IntPredicate letters, int alphabetSize) {
        return train(code, letters, alphabetSize,
                readResource("/raskladka/" + code + "-corpus.txt"),
                readResource("/raskladka/" + code + "-words.txt"));
    }

    private static String readResource(String path) {
        InputStream in = LanguageModel.class.getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("Missing resource " + path);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append('\n');
            }
            return text.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
