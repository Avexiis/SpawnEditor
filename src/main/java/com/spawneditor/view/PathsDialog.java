package com.spawneditor.view;

import com.spawneditor.io.Paths;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;

public class PathsDialog extends JDialog {
    private final JTextField tfSpawns = new JTextField(Paths.SPAWNS_JSON, 28);
    private final JTextField tfMap = new JTextField(Paths.MAP_IMAGE, 28);
    private final JTextField tfObjs = new JTextField(Paths.OBJECTS_JSON, 28);
    private final JTextField tfNpcs = new JTextField(Paths.NPCS_JSON, 28);
    private final JTextField tfDyn = new JTextField(Paths.DYNAMIC_NPCS_TXT, 28);
    private final JSpinner spRxMin = new JSpinner(new SpinnerNumberModel(Paths.RX_MIN, -10000, 10000, 1));
    private final JSpinner spRxMax = new JSpinner(new SpinnerNumberModel(Paths.RX_MAX, -10000, 10000, 1));
    private final JSpinner spRyMin = new JSpinner(new SpinnerNumberModel(Paths.RY_MIN, -10000, 10000, 1));
    private final JSpinner spRyMax = new JSpinner(new SpinnerNumberModel(Paths.RY_MAX, -10000, 10000, 1));
    private boolean accepted = false;

    public PathsDialog(Window owner) {
        super(owner, "Edit Paths & Ranges", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0; c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 4, 4, 4);
        addRow(p, c, "Map Image:", tfMap, this::browseFile);
        addRow(p, c, "Matrix NPC Spawns:", tfDyn, this::browseFile);
        addRow(p, c, "Main Spawns (JSON):", tfSpawns, this::browseJson);
        addRow(p, c, "Object List:", tfObjs, this::browseFile);
        addRow(p, c, "NPC List:", tfNpcs, this::browseFile);
        c.gridx = 0;
        p.add(new JLabel("RX_MIN:"), c); c.gridx = 1; p.add(spRxMin, c);
        c.gridx = 2; p.add(Box.createHorizontalStrut(18), c);
        c.gridx = 3; p.add(new JLabel("RX_MAX:"), c); c.gridx = 4; p.add(spRxMax, c);
        c.gridy++; c.gridx = 0;
        p.add(new JLabel("RY_MIN:"), c); c.gridx = 1; p.add(spRyMin, c);
        c.gridx = 2; p.add(Box.createHorizontalStrut(18), c);
        c.gridx = 3; p.add(new JLabel("RY_MAX:"), c); c.gridx = 4; p.add(spRyMax, c);
        c.gridy++;
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        buttons.add(ok); buttons.add(cancel);
        ok.addActionListener(e -> {
            Paths.SPAWNS_JSON = tfSpawns.getText().trim();
            Paths.MAP_IMAGE = tfMap.getText().trim();
            Paths.OBJECTS_JSON = tfObjs.getText().trim();
            Paths.NPCS_JSON = tfNpcs.getText().trim();
            Paths.DYNAMIC_NPCS_TXT = tfDyn.getText().trim();
            int rxMin = (Integer) spRxMin.getValue();
            int rxMax = (Integer) spRxMax.getValue();
            int ryMin = (Integer) spRyMin.getValue();
            int ryMax = (Integer) spRyMax.getValue();
            if (rxMin > rxMax || ryMin > ryMax) {
                JOptionPane.showMessageDialog(this, "RX_MIN must be <= RX_MAX and RY_MIN must be <= RY_MAX.", "Invalid Ranges", JOptionPane.WARNING_MESSAGE);
                return;
            }
            Paths.RX_MIN = rxMin; Paths.RX_MAX = rxMax;
            Paths.RY_MIN = ryMin; Paths.RY_MAX = ryMax;
            accepted = true;
            dispose();
        });
        cancel.addActionListener(e -> dispose());
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(p, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    public boolean isAccepted() {
        return accepted;
    }

    private interface Chooser {
        File choose(Component parent);
    }

    private void addRow(JPanel p, GridBagConstraints c, String label, JTextField tf, Chooser chooser) {
        c.gridx = 0; p.add(new JLabel(label), c);
        c.gridx = 1; c.gridwidth = 3; c.fill = GridBagConstraints.HORIZONTAL; p.add(tf, c);
        c.gridx = 4; c.gridwidth = 1; c.fill = GridBagConstraints.NONE;
        JButton browse = new JButton("Browse...");
        browse.addActionListener(e -> {
            File f = chooser.choose(p);
            if (f != null) tf.setText(f.getAbsolutePath());
        });
        p.add(browse, c);
        c.gridy++;
    }

    private File browseFile(Component parent) {
        JFileChooser ch = new JFileChooser();
        if (ch.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            return ch.getSelectedFile();
        }
        return null;
    }
    private File browseJson(Component parent) { return browseFile(parent); }
}
