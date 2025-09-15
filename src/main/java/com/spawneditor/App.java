package com.spawneditor;

import com.formdev.flatlaf.FlatDarkLaf;
import com.spawneditor.controller.EditorController;
import com.spawneditor.io.DynamicNpcStore;
import com.spawneditor.io.JsonStore;
import com.spawneditor.io.NpcIndex;
import com.spawneditor.io.ObjectIndex;
import com.spawneditor.io.Paths;
import com.spawneditor.model.SpawnProject;
import com.spawneditor.rpc.DiscordPresence;
import com.spawneditor.util.Images;
import com.spawneditor.util.Ui;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.InputStream;

public class App {
    public static void main(String[] args) {
        if (args.length >= 1) Paths.SPAWNS_JSON = args[0];
        if (args.length >= 2) Paths.MAP_IMAGE = args[1];
        if (args.length >= 3) Paths.OBJECTS_JSON = args[2];
        if (args.length >= 4) Paths.NPCS_JSON = args[3];
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Throwable t) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            }
            catch (Exception ignore) {
            }
        }
        EventQueue.invokeLater(() -> {
            Ui.installGlobalFont("Segoe UI", 13);
            final DiscordPresence presence = DiscordPresence.start();
            Splash splash = new Splash();
            splash.setVisible(true);
            ensurePathsOrPrompt(splash);
            boolean ok = new File(Paths.MAP_IMAGE).exists() && new File(Paths.OBJECTS_JSON).exists()
                    && new File(Paths.NPCS_JSON).exists() && new File(Paths.DYNAMIC_NPCS_TXT).exists();
            if (!ok) {
                splash.dispose();
                try {
                    presence.close(); }
                catch (Exception ignored) {
                }
                return;
            }
            new SwingWorker<EditorBits, Integer>() {
                @Override
                protected EditorBits doInBackground() {
                    setProgress(0);
                    splash.setProgress(0, "Preparing folders…");
                    File jsonFile = new File(Paths.SPAWNS_JSON);
                    File parent = jsonFile.getParentFile();
                    if (parent != null) parent.mkdirs();
                    int step = 0;
                    int total = 5;
                    SpawnProject project = new SpawnProject();
                    JsonStore store = new JsonStore(Paths.SPAWNS_JSON);
                    splash.setProgress(pct(++step, total), "Loading spawns…");
                    store.loadInto(project);
                    ObjectIndex objectIndex = new ObjectIndex(Paths.OBJECTS_JSON);
                    splash.setProgress(pct(++step, total), "Loading objects…");
                    objectIndex.load();
                    NpcIndex npcIndex = new NpcIndex(Paths.NPCS_JSON);
                    splash.setProgress(pct(++step, total), "Loading NPCs…");
                    npcIndex.load();
                    DynamicNpcStore dynStore = new DynamicNpcStore(Paths.DYNAMIC_NPCS_TXT, npcIndex::nameFor);
                    splash.setProgress(pct(++step, total), "Loading dynamic NPCs…");
                    dynStore.load();
                    splash.setProgress(pct(++step, total), "Starting editor…");
                    return new EditorBits(project, objectIndex, npcIndex, dynStore, presence);
                }
                private int pct(int step, int total) {
                    int p = (int)Math.floor((step * 100.0) / total);
                    return Math.max(0, Math.min(99, p));
                }
                @Override
                protected void done() {
                    try {
                        splash.setProgress(100, "Done");
                        EditorBits bits = get();
                        EditorController controller = new EditorController(bits.project, bits.objectIndex, bits.npcIndex, bits.dynStore, bits.presence);
                        controller.show();
                    } catch (Exception ex) {
                        Ui.error("Failed to start: " + ex.getMessage());
                        try {
                            presence.close();
                        } catch (Exception ignored) {
                        }
                    } finally {
                        splash.dispose();
                    }
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        try {
                            presence.close();
                        } catch (Exception ignored) {
                        }
                    }));
                }
            }.execute();
        });
    }

    private static void ensurePathsOrPrompt(Window owner){
        com.spawneditor.io.PathsConfig.applyToPaths(com.spawneditor.io.PathsConfig.loadOrNull());
        com.spawneditor.view.PathsDialog dlg = new com.spawneditor.view.PathsDialog(owner);
        dlg.setVisible(true);
        if (!dlg.isAccepted()) return;
        com.spawneditor.io.PathsConfig.saveFromCurrentPaths();
    }

    private static final class EditorBits {
        final SpawnProject project;
        final ObjectIndex objectIndex;
        final NpcIndex npcIndex;
        final DynamicNpcStore dynStore;
        final DiscordPresence presence;
        EditorBits(SpawnProject p, ObjectIndex oi, NpcIndex ni, DynamicNpcStore ds, DiscordPresence pr) {
            this.project = p; this.objectIndex = oi; this.npcIndex = ni; this.dynStore = ds; this.presence = pr;
        }
    }

    private static final class Splash extends JDialog {
        private final JLabel lbIcon = new JLabel();
        private final JLabel lbMsg  = new JLabel("Loading…", SwingConstants.CENTER);
        private final ThinProgressBar bar = new ThinProgressBar();
        private static final Color BG = new Color(0x30, 0x30, 0x30);
        private static final Color FG_TEXT = new Color(0xEE, 0xEE, 0xEE);
        Splash() {
            super((Frame) null, "Loading", ModalityType.MODELESS);
            setUndecorated(true);
            JPanel content = new JPanel();
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
            content.setBackground(BG);
            lbIcon.setAlignmentX(Component.CENTER_ALIGNMENT);
            lbMsg.setAlignmentX(Component.CENTER_ALIGNMENT);
            bar.setAlignmentX(Component.CENTER_ALIGNMENT);
            lbIcon.setHorizontalAlignment(SwingConstants.CENTER);
            lbIcon.setVerticalAlignment(SwingConstants.CENTER);
            setIconImageOnLabel(lbIcon, 96);
            lbMsg.setForeground(FG_TEXT);
            lbMsg.setFont(lbMsg.getFont().deriveFont(Font.PLAIN, 14f));
            bar.setPreferredSize(new Dimension(360, 8));
            content.add(lbIcon);
            content.add(Box.createVerticalStrut(12));
            content.add(lbMsg);
            content.add(Box.createVerticalStrut(10));
            content.add(bar);
            setContentPane(content);
            pack();
            setSize(new Dimension(Math.max(getWidth(), 420), Math.max(getHeight(), 160)));
            setLocationRelativeTo(null);
        }
        void setProgress(int pct, String message) {
            SwingUtilities.invokeLater(() -> {
                bar.setValue(pct);
                if (message == null || message.isEmpty()) {
                    lbMsg.setText(pct + "%");
                } else {
                    lbMsg.setText(message + "  " + pct + "%");
                }
            });
        }
        private static void setIconImageOnLabel(JLabel label, int target) {
            try (InputStream in = App.class.getResourceAsStream("icon.png")) {
                if (in != null) {
                    Image img = ImageIO.read(in);
                    if (img != null) {
                        int w = img.getWidth(null), h = img.getHeight(null);
                        if (w > 0 && h > 0) {
                            double s = Math.min(target / (double) w, target / (double) h);
                            int nw = Math.max(1, (int)Math.round(w * s));
                            int nh = Math.max(1, (int)Math.round(h * s));
                            Image scaled = Images.scale(img, nw, nh);
                            label.setIcon(new ImageIcon(scaled));
                        }
                    }
                }
            } catch (Exception ignore) {
            }
        }
    }

    private static final class ThinProgressBar extends JComponent {
        private int value;
        private static final Color FILL = new Color(0xBB, 0xBB, 0xBB);

        void setValue(int v) {
            int nv = Math.max(0, Math.min(100, v));
            if (nv != value) {
                value = nv;
                repaint();
            }
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(360, 8);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            int pw = (int) Math.round(w * (value / 100.0));
            g.setColor(FILL);
            g.fillRect(0, 0, pw, h);
            g.dispose();
        }
    }
}
