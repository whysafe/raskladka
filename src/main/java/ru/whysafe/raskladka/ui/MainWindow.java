package ru.whysafe.raskladka.ui;

import ru.whysafe.raskladka.Fixer;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Two panes: what was typed on the left, what was meant on the right.
 * Fixed words are highlighted; clicking one puts the original back.
 */
public final class MainWindow {

    private static final String SAMPLE = "Ghbdtn! Crbym? gj;fkeqcnf? afqk c ghjtrnjv lj dtxthf? z gjcvjnh./\n"
            + "Встреча в пятницу, ыуу нщг ещьщккщц!\n"
            + "А этот текст набран правильно, его программа не трогает.\n"
            + "Nfr xnj ghjcnj dcnfdm b gjkexb cdjq ntrcn/\n"
            + "пРОВЕРКА cAPS LOCK: ytn ghj,ktv";

    private final Fixer fixer = Fixer.withBundledModels();
    private final JFrame frame = new JFrame("Раскладка");
    private final JTextArea input = new JTextArea();
    private final JTextPane output = new JTextPane();
    private final JLabel status = new JLabel(" ");
    private final JCheckBox capsBox = new JCheckBox("Чинить Caps Lock", true);
    private final Set<Integer> keptByUser = new HashSet<>();
    private final Timer debounce = new Timer(120, e -> refresh());
    private Fixer.Mode mode = Fixer.Mode.AUTO;
    private List<Fixer.Change> changes = List.of();

    private MainWindow() {
        debounce.setRepeats(false);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(940, 560));
        frame.setContentPane(buildContent());
        bindShortcuts();
    }

    public static void open() throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> {
            MainWindow window = new MainWindow();
            window.frame.setSize(1100, 700);
            window.frame.setLocationRelativeTo(null);
            window.frame.setVisible(true);
            window.input.requestFocusInWindow();
        });
    }

    /** Renders the window with sample text into a PNG, used for the README and in CI. */
    public static void saveScreenshot(Path file) throws IOException {
        BufferedImage[] image = new BufferedImage[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                MainWindow window = new MainWindow();
                window.input.setText(SAMPLE);
                window.refresh();
                window.frame.pack();
                window.frame.setSize(1280, 800);
                window.frame.validate();
                JComponent content = (JComponent) window.frame.getContentPane();
                content.setSize(1280, 800);
                layoutTree(content);
                image[0] = new BufferedImage(1280, 800, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image[0].createGraphics();
                content.printAll(g);
                g.dispose();
                window.frame.dispose();
            });
        } catch (InterruptedException | InvocationTargetException e) {
            throw new IOException("Не удалось отрисовать окно", e);
        }
        ImageIO.write(image[0], "png", file.toFile());
    }

    private static void layoutTree(java.awt.Container c) {
        c.doLayout();
        for (java.awt.Component child : c.getComponents()) {
            if (child instanceof java.awt.Container container) {
                layoutTree(container);
            }
        }
    }

    private JComponent buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 18));
        root.setBackground(Theme.PAPER);
        root.setBorder(BorderFactory.createEmptyBorder(22, 28, 20, 28));
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildPanes(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        return root;
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(18, 12));
        header.setOpaque(false);

        JPanel brand = new JPanel(new BorderLayout(14, 0));
        brand.setOpaque(false);
        brand.add(new Widgets.Emblem(), BorderLayout.WEST);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Раскладка");
        title.setFont(Theme.ui(Font.BOLD, 28));
        title.setForeground(Theme.INK);
        JLabel subtitle = new JLabel("ghbdtn → привет: исправляет текст, набранный не в той раскладке");
        subtitle.setFont(Theme.ui(Font.PLAIN, 14));
        subtitle.setForeground(Theme.MUTED);
        titles.add(title);
        titles.add(subtitle);
        brand.add(titles, BorderLayout.CENTER);
        header.add(brand, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        controls.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        addModeButton(controls, group, "Авто", Fixer.Mode.AUTO, true);
        addModeButton(controls, group, "Всё в RU", Fixer.Mode.TO_RUSSIAN, false);
        addModeButton(controls, group, "Всё в EN", Fixer.Mode.TO_ENGLISH, false);
        capsBox.setOpaque(false);
        capsBox.setFont(Theme.ui(Font.PLAIN, 14));
        capsBox.setForeground(Theme.INK);
        capsBox.setFocusPainted(false);
        capsBox.addActionListener(e -> refresh());
        controls.add(Box.createHorizontalStrut(6));
        controls.add(capsBox);
        header.add(controls, BorderLayout.EAST);
        return header;
    }

    private void addModeButton(JPanel panel, ButtonGroup group, String text, Fixer.Mode value, boolean selected) {
        JToggleButton b = Widgets.segment(text);
        b.setSelected(selected);
        b.addActionListener(e -> {
            mode = value;
            keptByUser.clear();
            refresh();
        });
        group.add(b);
        panel.add(b);
    }

    private JComponent buildPanes() {
        JPanel panes = new JPanel(new GridLayout(1, 2, 18, 0));
        panes.setOpaque(false);

        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        styleText(input);
        input.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                inputChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                inputChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                inputChanged();
            }
        });

        output.setEditable(false);
        styleText(output);
        output.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleChangeAt(output.viewToModel2D(e.getPoint()));
            }
        });
        output.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Fixer.Change c = changeAt(output.viewToModel2D(e.getPoint()));
                output.setCursor(Cursor.getPredefinedCursor(c != null ? Cursor.HAND_CURSOR : Cursor.TEXT_CURSOR));
                output.setToolTipText(c != null ? "Было: " + c.original() + " · щелчок вернёт как было" : null);
            }
        });

        panes.add(card("Как набралось", "вставьте или напечатайте текст", input));
        panes.add(card("Как задумано", "щелчок по слову вернёт оригинал", output));
        return panes;
    }

    private static void styleText(JComponent text) {
        text.setFont(Theme.mono(17));
        text.setForeground(Theme.INK);
        text.setBackground(Theme.CARD);
        text.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        if (text instanceof javax.swing.text.JTextComponent tc) {
            tc.setCaretColor(Theme.ACCENT);
            tc.setSelectionColor(Theme.TEAL_SOFT);
            tc.setSelectedTextColor(Theme.INK);
        }
    }

    private static JComponent card(String title, String hint, JComponent body) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Theme.CARD);
        card.setBorder(BorderFactory.createLineBorder(Theme.LINE));

        JPanel head = new JPanel(new BorderLayout(10, 0));
        head.setBackground(Theme.CARD);
        head.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                BorderFactory.createEmptyBorder(12, 16, 10, 16)));
        JLabel label = new JLabel(title.toUpperCase());
        label.setFont(Theme.ui(Font.BOLD, 13));
        label.setForeground(Theme.ACCENT);
        JLabel hintLabel = new JLabel(hint);
        hintLabel.setFont(Theme.ui(Font.PLAIN, 12));
        hintLabel.setForeground(Theme.MUTED);
        head.add(label, BorderLayout.WEST);
        head.add(hintLabel, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.CARD);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
        status.setFont(Theme.ui(Font.PLAIN, 14));
        status.setForeground(Theme.MUTED);
        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        legend.setOpaque(false);
        legend.add(chip("раскладка", Theme.ACCENT_SOFT, Theme.ACCENT.darker()));
        legend.add(Box.createHorizontalStrut(8));
        legend.add(chip("Caps Lock", Theme.TEAL_SOFT, Theme.TEAL));
        legend.setAlignmentX(0f);
        status.setAlignmentX(0f);
        info.add(legend);
        info.add(Box.createVerticalStrut(6));
        info.add(status);
        footer.add(info, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton clear = Widgets.button("Очистить", false);
        clear.addActionListener(e -> input.setText(""));
        JButton paste = Widgets.button("Вставить из буфера", false);
        paste.setToolTipText("Ctrl+Shift+V");
        paste.addActionListener(e -> pasteFromClipboard());
        JButton copy = Widgets.button("Скопировать результат", true);
        copy.setToolTipText("Ctrl+Enter");
        copy.addActionListener(e -> copyResult());
        buttons.add(clear);
        buttons.add(paste);
        buttons.add(copy);
        footer.add(buttons, BorderLayout.EAST);
        return footer;
    }

    private static JLabel chip(String text, Color background, Color foreground) {
        JLabel chip = new JLabel(text);
        chip.setOpaque(true);
        chip.setBackground(background);
        chip.setForeground(foreground);
        chip.setFont(Theme.mono(13).deriveFont(Font.BOLD));
        chip.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        return chip;
    }

    private void bindShortcuts() {
        int menu = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JComponent root = (JComponent) frame.getContentPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, menu), "copy");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_V, menu | InputEvent.SHIFT_DOWN_MASK), "paste");
        root.getActionMap().put("copy", action(this::copyResult));
        root.getActionMap().put("paste", action(this::pasteFromClipboard));
    }

    private static javax.swing.Action action(Runnable body) {
        return new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                body.run();
            }
        };
    }

    private void inputChanged() {
        keptByUser.clear();
        debounce.restart();
    }

    private void refresh() {
        Fixer.Result result = fixer.fix(input.getText(), mode, capsBox.isSelected(), keptByUser);
        changes = result.changes();
        renderOutput(result);
        if (result.wordCount() == 0) {
            status.setText("Напечатайте что-нибудь слева: ghbdtn, rfr ltkf?");
        } else {
            String kept = keptByUser.isEmpty() ? "" : " · оставлено вручную: " + keptByUser.size();
            status.setText("Исправлено слов: " + changes.size() + " из " + result.wordCount() + kept);
        }
    }

    private void renderOutput(Fixer.Result result) {
        StyledDocument doc = output.getStyledDocument();
        try {
            doc.remove(0, doc.getLength());
            doc.insertString(0, result.text(), plain());
            for (Fixer.Change c : changes) {
                doc.setCharacterAttributes(c.start(), c.end() - c.start(), highlight(c), false);
            }
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        output.setCaretPosition(0);
    }

    private static SimpleAttributeSet plain() {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, Theme.INK);
        return a;
    }

    private static SimpleAttributeSet highlight(Fixer.Change c) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        Color background = c.layout() ? Theme.ACCENT_SOFT : Theme.TEAL_SOFT;
        StyleConstants.setBackground(a, background);
        StyleConstants.setForeground(a, c.layout() ? Theme.ACCENT.darker() : Theme.TEAL);
        StyleConstants.setBold(a, true);
        return a;
    }

    private Fixer.Change changeAt(int offset) {
        for (Fixer.Change c : changes) {
            if (offset >= c.start() && offset < c.end()) {
                return c;
            }
        }
        return null;
    }

    private void toggleChangeAt(int offset) {
        Fixer.Change c = changeAt(offset);
        if (c != null) {
            keptByUser.add(c.tokenIndex());
            refresh();
        }
    }

    private void pasteFromClipboard() {
        try {
            Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            if (data != null) {
                input.setText(data.toString());
                refresh();
            }
        } catch (Exception e) {
            status.setText("В буфере обмена нет текста");
        }
    }

    private void copyResult() {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(output.getText()), null);
        status.setText("Скопировано в буфер обмена: " + changes.size() + " исправл.");
    }
}
