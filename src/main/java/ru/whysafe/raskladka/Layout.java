package ru.whysafe.raskladka;

import java.util.HashMap;
import java.util.Map;

/**
 * Physical key correspondence between the US QWERTY and Russian ЙЦУКЕН layouts.
 * A character typed on one layout is translated to the character the same key
 * produces on the other one, so "ghbdtn" becomes "привет" and back.
 */
public final class Layout {

    // Same key, same position: every pair of characters is one key with and without Shift.
    private static final String EN = "`qwertyuiop[]asdfghjkl;'zxcvbnm,./"
            + "~QWERTYUIOP{}ASDFGHJKL:\"ZXCVBNM<>?" + "@#$^&";
    private static final String RU = "ёйцукенгшщзхъфывапролджэячсмитьбю."
            + "ЁЙЦУКЕНГШЩЗХЪФЫВАПРОЛДЖЭЯЧСМИТЬБЮ," + "\"№;:?";

    private static final Map<Character, Character> EN_TO_RU = new HashMap<>();
    private static final Map<Character, Character> RU_TO_EN = new HashMap<>();

    static {
        for (int i = 0; i < EN.length(); i++) {
            EN_TO_RU.put(EN.charAt(i), RU.charAt(i));
            RU_TO_EN.put(RU.charAt(i), EN.charAt(i));
        }
    }

    private Layout() {
    }

    public static String toRussian(String text) {
        return translate(text, EN_TO_RU);
    }

    public static String toEnglish(String text) {
        return translate(text, RU_TO_EN);
    }

    public static boolean isLatinLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    public static boolean isCyrillicLetter(char c) {
        return (c >= 'а' && c <= 'я') || (c >= 'А' && c <= 'Я') || c == 'ё' || c == 'Ё';
    }

    private static String translate(String text, Map<Character, Character> table) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(table.getOrDefault(c, c));
        }
        return out.toString();
    }
}
