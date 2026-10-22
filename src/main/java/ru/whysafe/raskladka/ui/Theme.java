package ru.whysafe.raskladka.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Paper-and-ink palette with a vermilion accent, fonts picked from what the system has. */
final class Theme {

    static final Color PAPER = new Color(0xF3EFE6);
    static final Color CARD = new Color(0xFFFCF5);
    static final Color INK = new Color(0x1E2230);
    static final Color MUTED = new Color(0x6A6D78);
    static final Color LINE = new Color(0xE0D8C6);
    static final Color ACCENT = new Color(0xC8411A);
    static final Color ACCENT_SOFT = new Color(0xFBDCCB);
    static final Color TEAL = new Color(0x1C6A6D);
    static final Color TEAL_SOFT = new Color(0xD3ECEA);
    static final Color KEY_FACE = new Color(0xFFFFFF);
    static final Color KEY_SHADOW = new Color(0xCFC5B0);

    static final String UI_FAMILY = firstAvailable("Segoe UI", "Inter", "Noto Sans", "DejaVu Sans", Font.SANS_SERIF);
    static final String MONO_FAMILY = firstAvailable("JetBrains Mono", "Cascadia Mono", "Consolas",
            "DejaVu Sans Mono", Font.MONOSPACED);

    private Theme() {
    }

    static Font ui(int style, float size) {
        return new Font(UI_FAMILY, style, Math.round(size));
    }

    static Font mono(float size) {
        return new Font(MONO_FAMILY, Font.PLAIN, Math.round(size));
    }

    private static String firstAvailable(String... families) {
        Set<String> installed = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String family : families) {
            if (installed.contains(family)) {
                return family;
            }
        }
        return families[families.length - 1];
    }
}
