package com.dan.thunder.app;

import com.dan.fcombobox.FComboBox;
import com.dan.fsegmentedcontrol.FSegmentedControl;
import com.dan.fsegmentedcontrol.FSegmentedControlItem;
import com.dan.fstyle.FFontRole;
import com.dan.fstyle.FTheme;
import com.dan.fstyle.FThemePreset;
import com.dan.ftogglebutton.FToggleButton;
import com.dan.thunder.model.Mood;
import com.dan.thunder.model.StormLevel;
import com.dan.thunder.model.ThunderData;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Bedienleiste unter der Szene – F-Komponenten in FTheme-Farben, live umschaltbar. */
public class ControlBar extends JPanel {

    /** Rückmeldungen an die App. */
    public interface Actions {
        void levelChanged(StormLevel level);

        void moodChanged(Mood mood);

        void rainChanged(boolean on);

        /** Gibt false zurück, wenn der Ton nicht gestartet werden konnte. */
        boolean soundChanged(boolean on);

        void softChanged(boolean on);

        /** Rückfluss aus Timmendorf (Phase 18): Segelboote und Flaggen. */
        default void seglerChanged(boolean on) { }
    }

    private static final int SEG_W = 122;
    private final FSegmentedControl levelControl = new FSegmentedControl("Wetterleuchten", "Gewitter", "Unwetter");
    private final FSegmentedControl moodControl = new FSegmentedControl(Mood.ABEND.getLabel(), Mood.NACHT.getLabel());
    private final FToggleButton rainToggle = new FToggleButton(true);
    private final FToggleButton soundToggle = new FToggleButton(false);
    private final FToggleButton softToggle = new FToggleButton(false);
    private final FToggleButton seglerToggle = new FToggleButton(true);
    private final FComboBox themeBox = new FComboBox(FThemePreset.values());
    private final List<JLabel> captions = new ArrayList<>();
    private final FTheme.ThemeListener themeListener = t -> applyTheme();
    private final Actions actions;
    private List<StormLevel> levels = List.of();
    private boolean updating;

    public ControlBar(Actions actions, Mood initialMood) {
        super(new BorderLayout(12, 0));
        this.actions = actions;
        setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        setOpaque(false);

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
        left.setOpaque(false);
        levelControl.setFixedSegmentWidth(SEG_W);
        // FSegmentedControl gibt unabhängig von der Segmentzahl 240 px vor – daher Breite selbst setzen
        levelControl.setPreferredSize(new Dimension(SEG_W * 3 + 4, 32));
        levelControl.setSelectedIndex(1);
        levelControl.setEnabled(false);
        levelControl.setToolTipText("Stärke des Gewitters");
        levelControl.getModel().addPropertyChangeListener(e -> {
            int i = levelControl.getSelectedIndex();
            if (!updating && i >= 0 && i < levels.size()) {
                actions.levelChanged(levels.get(i));
            }
        });
        left.add(levelControl);
        left.add(Box.createHorizontalStrut(14));
        moodControl.setFixedSegmentWidth(82);
        moodControl.setPreferredSize(new Dimension(82 * 2 + 4, 32));
        moodControl.setSelectedIndex(initialMood == Mood.ABEND ? 0 : 1);
        moodControl.setToolTipText("Abendgewitter mit Resthelligkeit (Thunder1.jpg) oder Nacht (Thunder.jpg)");
        moodControl.getModel().addPropertyChangeListener(e ->
                actions.moodChanged(moodControl.getSelectedIndex() == 0 ? Mood.ABEND : Mood.NACHT));
        left.add(moodControl);
        left.add(Box.createHorizontalStrut(14));
        left.add(toggle("Regen", rainToggle, actions::rainChanged));
        left.add(Box.createHorizontalStrut(6));
        left.add(toggle("Ton", soundToggle, on -> {
            if (!actions.soundChanged(on) && on) {
                soundToggle.setSelected(false);
            }
        }));
        left.add(Box.createHorizontalStrut(6));
        softToggle.setTooltipOn("Blitze gedämpft, ohne Flackern");
        softToggle.setTooltipOff("Blitze mit voller Helligkeit und Flackern");
        left.add(toggle("Sanfte Blitze", softToggle, actions::softChanged));
        left.add(Box.createHorizontalStrut(6));
        seglerToggle.setTooltipOn("Segelboote (sail) und Flaggen (cloth) aus Timmendorf");
        seglerToggle.setTooltipOff("ohne Segelboote und Flaggen");
        left.add(toggle("Segler", seglerToggle, actions::seglerChanged));
        left.add(Box.createHorizontalGlue());
        for (Component c : left.getComponents()) {
            if (c instanceof JComponent jc) {
                jc.setAlignmentY(Component.CENTER_ALIGNMENT);
                jc.setMaximumSize(jc.getPreferredSize());
            }
        }
        add(left, BorderLayout.CENTER);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
        themeBox.setPreferredSize(new Dimension(212, themeBox.getPreferredSize().height));
        themeBox.setSelectedItem(FTheme.getInstance().getCurrentPreset());
        themeBox.setToolTipText("Theme der Bedienelemente – die Szene behält ihren eigenen Look");
        themeBox.addActionListener(e -> {
            Object p = themeBox.getSelectedItem();
            if (p instanceof FThemePreset preset && preset != FTheme.getInstance().getCurrentPreset()) {
                FTheme.getInstance().applyPreset(preset);
            }
        });
        right.add(themeBox);
        add(right, BorderLayout.EAST);
        applyTheme();
    }

    /** Übernimmt die Stufen aus der Datenbank (Namen und Reihenfolge) und wählt {@code selected}. */
    public void setData(ThunderData data, StormLevel selected) {
        updating = true;
        try {
            levels = List.copyOf(data.levels());
            List<FSegmentedControlItem> items = new ArrayList<>();
            for (StormLevel l : levels) {
                items.add(new FSegmentedControlItem(l.name(), l.code()));
            }
            levelControl.setItems(items);
            Dimension d = new Dimension(SEG_W * items.size() + 4, levelControl.getPreferredSize().height);
            levelControl.setPreferredSize(d);
            levelControl.setMaximumSize(d);
            levelControl.setSelectedIndex(Math.max(0, levels.indexOf(selected)));
            levelControl.setEnabled(true);
            levelControl.setToolTipText(null);
        } finally {
            updating = false;
        }
        levelControl.getParent().revalidate();
        revalidate();
        repaint();
    }

    public void setSoundSelected(boolean on) {
        soundToggle.setSelected(on);
    }

    private JComponent toggle(String text, FToggleButton t, Consumer<Boolean> target) {
        t.getModel().addPropertyChangeListener(e -> {
            if ("selected".equals(e.getPropertyName())) {
                target.accept(t.isSelected());
            }
        });
        JLabel l = new JLabel(text);
        captions.add(l);
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.setOpaque(false);
        p.add(l);
        p.add(t);
        p.setMaximumSize(p.getPreferredSize());
        return p;
    }

    private void applyTheme() {
        FTheme th = FTheme.getInstance();
        for (JLabel l : captions) {
            l.setForeground(th.getText());
            l.setFont(th.getFont(FFontRole.LABEL));
        }
        for (FToggleButton t : new FToggleButton[] { rainToggle, soundToggle, softToggle }) {
            t.setOnColor(th.getPrimary());
            t.setGlowColor(th.getAccent());
        }
        for (FSegmentedControl s : new FSegmentedControl[] { levelControl, moodControl }) {
            s.setSelectedColor(th.getPrimary());
            s.setGlowColor(th.getAccent());
        }
        if (themeBox.getSelectedItem() != th.getCurrentPreset()) {
            themeBox.setSelectedItem(th.getCurrentPreset());
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        FTheme th = FTheme.getInstance();
        g.setPaint(new GradientPaint(0, 0, th.getSurface(), 0, getHeight(), th.getBackground()));
        g.fillRect(0, 0, getWidth(), getHeight());
        Color p = th.getPrimary();
        g.setColor(new Color(p.getRed(), p.getGreen(), p.getBlue(), 90));
        g.fillRect(0, 0, getWidth(), 1);
        g.dispose();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        FTheme.getInstance().addThemeListener(themeListener);
        applyTheme();
    }

    @Override
    public void removeNotify() {
        FTheme.getInstance().removeThemeListener(themeListener);
        super.removeNotify();
    }
}
