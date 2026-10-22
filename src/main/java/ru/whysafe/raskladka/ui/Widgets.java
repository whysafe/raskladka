package ru.whysafe.raskladka.ui;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JToggleButton;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Small hand-drawn components, so the window looks the same on every platform. */
final class Widgets {

    private Widgets() {
    }

    static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g2;
    }

    static JButton button(String text, boolean primary) {
        JButton b = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                paintPill(this, g, primary ? Theme.INK : Theme.CARD, primary ? Theme.CARD : Theme.INK,
                        primary ? Theme.INK : Theme.LINE);
            }
        };
        style(b);
        return b;
    }

    static JToggleButton segment(String text) {
        JToggleButton b = new JToggleButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                boolean on = isSelected();
                paintPill(this, g, on ? Theme.ACCENT : Theme.CARD, on ? Color.WHITE : Theme.INK,
                        on ? Theme.ACCENT : Theme.LINE);
            }
        };
        style(b);
        return b;
    }

    private static void style(AbstractButton b) {
        b.setUI(new BasicButtonUI());
        b.setFont(Theme.ui(Font.BOLD, 14));
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        FontMetrics fm = b.getFontMetrics(b.getFont());
        b.setPreferredSize(new Dimension(fm.stringWidth(b.getText()) + 36, 38));
    }

    private static void paintPill(AbstractButton b, Graphics g, Color fill, Color text, Color border) {
        Graphics2D g2 = smooth(g);
        int w = b.getWidth();
        int h = b.getHeight();
        Color face = b.getModel().isRollover() && !b.isSelected() ? blend(fill, Theme.ACCENT_SOFT, 0.45) : fill;
        g2.setColor(face);
        g2.fillRoundRect(0, 0, w - 1, h - 1, h, h);
        g2.setColor(border);
        g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);
        if (b.isFocusOwner()) {
            g2.setColor(Theme.TEAL);
            g2.drawRoundRect(2, 2, w - 5, h - 5, h - 4, h - 4);
        }
        g2.setFont(b.getFont());
        g2.setColor(text);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(b.getText(), (w - fm.stringWidth(b.getText())) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }

    private static Color blend(Color a, Color b, double t) {
        return new Color(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    /** Two keycaps, "П" over "G", the emblem of a key that means two letters. */
    static final class Emblem extends JComponent {

        Emblem() {
            setPreferredSize(new Dimension(96, 52));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            drawKey(g2, 0, 4, "G", "П", Theme.INK);
            drawKey(g2, 50, 4, "?", ",", Theme.ACCENT);
            g2.dispose();
        }

        private static void drawKey(Graphics2D g2, int x, int y, String latin, String cyrillic, Color cyrColor) {
            int size = 44;
            g2.setColor(Theme.KEY_SHADOW);
            g2.fillRoundRect(x, y + 4, size, size - 2, 10, 10);
            g2.setColor(Theme.KEY_FACE);
            g2.fillRoundRect(x, y, size, size - 4, 10, 10);
            g2.setColor(Theme.LINE);
            g2.drawRoundRect(x, y, size, size - 4, 10, 10);
            g2.setFont(Theme.ui(Font.BOLD, 15));
            g2.setColor(Theme.MUTED);
            g2.drawString(latin, x + 8, y + 18);
            g2.setColor(cyrColor);
            g2.setFont(Theme.ui(Font.BOLD, 17));
            g2.drawString(cyrillic, x + size - 20, y + size - 12);
        }
    }
}
