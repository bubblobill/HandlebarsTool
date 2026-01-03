package net.rptools.ui;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import net.rptools.data.Config;
import net.rptools.data.Constants;
import net.rptools.data.SheetsObject;
import net.rptools.util.WatchFolder;
import net.rptools.servers.HandlebarsServer;
import net.rptools.servers.TestingServer;
import net.rptools.util.Bones;
import net.rptools.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.net.URI;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

public class Launcher extends JDialog {
    private static final Logger log = LoggerFactory.getLogger(Launcher.class);
    private static final int MAX_PORT = 65535;
    private JPanel contentPane;
    private JButton serverStart;
    private JButton buttonCancel;
    private JButton changeButton;
    private JComboBox<Constants.StatSheetLocation> locationCombo;
    private JButton hyperlink;
    private JTextArea selectedFolder;
    private JTextField port;
    //    private JButton serverStop;
    private JComboBox<String> tokenDataset;
    private JButton editButton;
    private JComboBox<String> themeCombo;
    //    private JButton serverRestart;
    private JLabel configFile;
    private JButton resetButton;
    private JButton createButton;
    private JCheckBox folderWatchCheckBox;

    private WatchFolder watchFolder;
    private FutureTask<?> hbTask;
    private FutureTask<?> tsTask;
    final ExecutorService threadPool = Executors.newFixedThreadPool(2, Executors.defaultThreadFactory());
    private URI uri;

    public Launcher() {
        $$$setupUI$$$();
        setTitle("MapTool Attribute-Sheet Handlebars BasicServer Launcher");
        setContentPane(contentPane);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getRootPane().setDefaultButton(serverStart);
        setModal(true);

        configFile.setText(Config.getConfigFile().getPath());
        setURI();
        initThemeCombo();
        initLocationCombo();
        configFile.setCursor(new Cursor(Cursor.HAND_CURSOR));
        configFile.addMouseListener(openFile);

        selectedFolder.setCursor(new Cursor(Cursor.HAND_CURSOR));
        selectedFolder.addMouseListener(openFile);

        hyperlink.setCursor(new Cursor(Cursor.HAND_CURSOR));
        hyperlink.addActionListener(this::onHyperlink);

        folderWatchCheckBox.setSelected(Config.getBoolean(Config.WATCH_FOLDER));
        folderWatchCheckBox.addActionListener(this::onWatchFolder);
        port.setText(String.valueOf(Config.getInt(Config.SERVER_PORT)));
        port.addPropertyChangeListener("value", this::onPortChange);
        changeButton.addActionListener(this::onSelectFolder);
        editButton.addActionListener(this::onEditData);
        createButton.addActionListener(this::onCreate);
        setSelectedFolder(Config.getPath(Config.TEMPLATE_FOLDER));

        resetButton.addActionListener(this::onReset);
        serverStart.addActionListener(this::onServerStart);
        buttonCancel.addActionListener(_ -> onCancel());

        // call onCancel() when cross is clicked
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });
        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(_ -> onCancel(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        pack();
        setLocationByPlatform(true);
    }

    private void initThemeCombo() {
        List<String> themeList = Config.getList(Config.THEME_CSS);
        ComboBoxModel<String> model = new DefaultComboBoxModel<>(themeList.toArray(String[]::new));
        themeCombo.setModel(model);
        themeCombo.setSelectedItem(Config.getString(Config.THEME));
        themeCombo.addItemListener((il) -> {
            if (il.getStateChange() == ItemEvent.SELECTED) {
                Config.set(Config.THEME, il.getItem());
            }
        });
    }

    private void initLocationCombo() {
        ComboBoxModel<Constants.StatSheetLocation> model = new DefaultComboBoxModel<>(Constants.StatSheetLocation.values());
        locationCombo.setModel(model);
        locationCombo.setSelectedItem(Arrays.stream(Constants.StatSheetLocation.values())
                .filter(statSheetLocation -> statSheetLocation.className().equalsIgnoreCase(Config.getString(Config.LOCATION)))
                .findFirst()
                .orElse(Constants.StatSheetLocation.BOTTOM_LEFT));
        locationCombo.addItemListener((il) -> {
            if (il.getStateChange() == ItemEvent.SELECTED) {
                Constants.StatSheetLocation location = (Constants.StatSheetLocation) il.getItem();
                Config.set(Config.LOCATION, location.className());
            }
        });
    }

    private static final MouseListener openFile = new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
            try {
                Path path;
                if (e.getSource() instanceof JLabel label) {
                    path = Path.of(label.getText());
                } else if (e.getSource() instanceof JTextComponent textComponent) {
                    path = Path.of(textComponent.getText());
                } else {
                    return;
                }
                Desktop.getDesktop().open(path.toFile());
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    };

    private void onWatchFolder(ActionEvent e) {
        Config.set(Config.WATCH_FOLDER, folderWatchCheckBox.isSelected());
    }

    private void onHyperlink(ActionEvent e) {
        try {
            Desktop.getDesktop().browse(uri);
        } catch (IOException ex) {
            //It looks like there's a problem
        }
    }

    private void onReset(ActionEvent e) {
        if (Utils.prompt(this, "This will reset everything and lose your custom data.\n You might want to consider editing the config file instead.\nContinue with reset?")) {
            Config.reset();
        }
    }

    private void onCreate(ActionEvent e) {
        if (Bones.createBones()) {
            setSelectedFolder(Bones.getSheetsFolder());
        }
    }

    private void onSelectFolder(ActionEvent e) {
        SheetsObject.selectFolder(this);
        setSelectedFolder(Config.getPath(Config.TEMPLATE_FOLDER));
    }

    private void setSelectedFolder(Path folderPath) {
        if (folderPath != null) {
            try {
                File folder = folderPath.toAbsolutePath().toFile();
                selectedFolder.setText(folder.toString());
                serverStart.setEnabled(true);
                Config.set(Config.TEMPLATE_FOLDER, folder);
                if (watchFolder != null) {
                    WatchFolder.stop();
                }
            } catch (SecurityException | IOError | InvalidPathException | UnsupportedOperationException e) {
                Utils.whoops(e);
                log.error(e.getLocalizedMessage(), e);
            }
        }
    }

    public void onEditData(ActionEvent e) {
        EditPropertyTypes editPropertyTypes = new EditPropertyTypes();
        editPropertyTypes.setVisible(true);
        List<String> datasetNames = Config.getList(Config.DATASETS);
        tokenDataset.setModel(new DefaultComboBoxModel<>(datasetNames.toArray(String[]::new)));
        String selectName = Config.getString(Config.DATASET_DEFAULT);
        if (selectName == null || selectName.isBlank()) {
            selectName = Config.getString(Config.DATASET_NAME);
        }
        if (datasetNames.contains(selectName)) {
            tokenDataset.getModel().setSelectedItem(selectName);
        } else {
            tokenDataset.setSelectedIndex(-1);
        }
    }

    private void onPortChange(PropertyChangeEvent e) {
        try {
            int i = Integer.parseInt(port.getText());
            if (i < 0) {
                port.setText("0");
                return;
            } else if (i > MAX_PORT) {
                port.setText(String.valueOf(MAX_PORT));
                return;
            }
            Config.set(Config.SERVER_PORT, i);
            setURI();
        } catch (NumberFormatException ex) {
            port.setText((String) e.getOldValue());
        }
    }

    private void setURI() {
        uri = URI.create(String.format("http://localhost:%d/testSpace.hbs", Config.getInt(Config.SERVER_PORT)));
    }

    private void onServerStart(ActionEvent e) {
        boolean success = start();

        hyperlink.setEnabled(success);

        serverStart.setEnabled(!success);
        changeButton.setEnabled(!success);
        tokenDataset.setEnabled(!success);
        editButton.setEnabled(!success);
        resetButton.setEnabled(!success);
        createButton.setEnabled(!success);
        port.setEnabled(!success);
        themeCombo.setEnabled(!success);
        locationCombo.setEnabled(!success);
    }

    private boolean start() {
        boolean success;
        try {
            watchFolder = new WatchFolder(Config.getPath(Config.TEMPLATE_FOLDER));
            success = watchFolder.start();
            watchFolder.addPropertyChangeListener(SheetsObject.propertyChangeListener);
            if (success) {
                if (hbTask != null && hbTask.state().equals(Future.State.RUNNING)) {
                    hbTask.cancel(true);
                }
                if (tsTask != null && tsTask.state().equals(Future.State.RUNNING)) {
                    tsTask.cancel(true);
                }

                tsTask = (FutureTask<?>) threadPool.submit(new Thread(TestingServer.testServerRunnable.get()));
                Future.State tsState = tsTask.state();
                hbTask = (FutureTask<?>) threadPool.submit(new Thread(HandlebarsServer.handlebarsRunnable.get()));
                Future.State hbState = hbTask.state();
                success = tsState.equals(Future.State.RUNNING) && hbState.equals(Future.State.RUNNING);
                log.info("start() -> {}, {}", tsState, hbState);
            }
        } catch (Exception e) {
            Utils.whoops(e);
            log.error(e.getLocalizedMessage(), e);
            success = false;
        }
        return success;
    }


    private void stop() {
        if (watchFolder != null) {
            watchFolder.removePropertyChangeListener(SheetsObject.propertyChangeListener);
            WatchFolder.stop();
        }
        if (hbTask != null && !hbTask.isDone()) {
            hbTask.cancel(true);
        }
        if (tsTask != null && !tsTask.isDone()) {
            tsTask.cancel(true);
        }
    }

    private void onCancel() {
        stop();
        dispose();
    }


    private void createUIComponents() {
        List<String> datasetNames = Config.getList(Config.DATASETS);
        tokenDataset = new JComboBox<>(new DefaultComboBoxModel<>(datasetNames.toArray(String[]::new)));
        tokenDataset.addItemListener((il) -> {
            if (il.getStateChange() == ItemEvent.SELECTED) {
                Config.set(Config.DATASET_NAME, il.getItem());
            }
        });
    }


    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        createUIComponents();
        contentPane = new JPanel();
        contentPane.setLayout(new GridLayoutManager(1, 1, new Insets(10, 10, 10, 10), -1, -1));
        contentPane.setMaximumSize(new Dimension(-1, -1));
        contentPane.setMinimumSize(new Dimension(-1, -1));
        contentPane.setPreferredSize(new Dimension(580, 380));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(13, 9, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("<html><h1>HandlebarsTool</h1></html>");
        panel1.add(label1, new GridConstraints(0, 0, 1, 9, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Default Location");
        panel1.add(label2, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        locationCombo = new JComboBox();
        final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
        locationCombo.setModel(defaultComboBoxModel1);
        panel1.add(locationCombo, new GridConstraints(6, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("Port");
        panel1.add(label3, new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        port = new JTextField();
        port.setColumns(5);
        port.setText("7890");
        panel1.add(port, new GridConstraints(7, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("Theme");
        panel1.add(label4, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        themeCombo = new JComboBox();
        panel1.add(themeCombo, new GridConstraints(5, 1, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("Dataset");
        panel1.add(label5, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel1.add(tokenDataset, new GridConstraints(4, 1, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(2, 1, new Insets(8, 8, 8, 8), -1, -1));
        panel1.add(panel2, new GridConstraints(5, 5, 3, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel2.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Server", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        serverStart = new JButton();
        serverStart.setEnabled(false);
        serverStart.setText("Start");
        panel2.add(serverStart, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        hyperlink = new JButton();
        hyperlink.setEnabled(false);
        hyperlink.setText("Open in Browser");
        panel2.add(hyperlink, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        configFile = new JLabel();
        Font configFileFont = this.$$$getFont$$$(null, Font.PLAIN, -1, configFile.getFont());
        if (configFileFont != null) configFile.setFont(configFileFont);
        configFile.setText("Label");
        panel1.add(configFile, new GridConstraints(9, 1, 1, 8, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label6 = new JLabel();
        label6.setText("Config File");
        panel1.add(label6, new GridConstraints(9, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label7 = new JLabel();
        label7.setText("Templates Folder");
        panel1.add(label7, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        selectedFolder = new JTextArea();
        selectedFolder.setAutoscrolls(false);
        selectedFolder.setColumns(50);
        selectedFolder.setDisabledTextColor(new Color(-16777216));
        selectedFolder.setEditable(false);
        selectedFolder.setEnabled(false);
        Font selectedFolderFont = this.$$$getFont$$$("Monospaced", Font.PLAIN, -1, selectedFolder.getFont());
        if (selectedFolderFont != null) selectedFolder.setFont(selectedFolderFont);
        selectedFolder.setLineWrap(true);
        selectedFolder.setRequestFocusEnabled(true);
        selectedFolder.setRows(4);
        selectedFolder.setSelectionEnd(20);
        selectedFolder.setSelectionStart(20);
        selectedFolder.setText("root:\\path\\to\\folder");
        selectedFolder.setVerifyInputWhenFocusTarget(false);
        selectedFolder.setWrapStyleWord(true);
        panel1.add(selectedFolder, new GridConstraints(1, 1, 2, 7, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        createButton = new JButton();
        createButton.setText("Create");
        panel1.add(createButton, new GridConstraints(2, 8, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        changeButton = new JButton();
        changeButton.setText("Change");
        panel1.add(changeButton, new GridConstraints(1, 8, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        editButton = new JButton();
        editButton.setEnabled(true);
        editButton.setText("Edit");
        panel1.add(editButton, new GridConstraints(4, 6, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonCancel = new JButton();
        buttonCancel.setText("Close");
        panel1.add(buttonCancel, new GridConstraints(11, 8, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(8, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel1.add(spacer2, new GridConstraints(8, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final Spacer spacer3 = new Spacer();
        panel1.add(spacer3, new GridConstraints(8, 4, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final Spacer spacer4 = new Spacer();
        panel1.add(spacer4, new GridConstraints(8, 5, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final Spacer spacer5 = new Spacer();
        panel1.add(spacer5, new GridConstraints(8, 7, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final Spacer spacer6 = new Spacer();
        panel1.add(spacer6, new GridConstraints(12, 1, 1, 8, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final Spacer spacer7 = new Spacer();
        panel1.add(spacer7, new GridConstraints(12, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        folderWatchCheckBox = new JCheckBox();
        folderWatchCheckBox.setSelected(true);
        folderWatchCheckBox.setText("Monitor for file changes");
        panel1.add(folderWatchCheckBox, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        resetButton = new JButton();
        resetButton.setText("Reset");
        panel1.add(resetButton, new GridConstraints(11, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer8 = new Spacer();
        panel1.add(spacer8, new GridConstraints(10, 8, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    private Font $$$getFont$$$(String fontName, int style, int size, Font currentFont) {
        if (currentFont == null) return null;
        String resultName;
        if (fontName == null) {
            resultName = currentFont.getName();
        } else {
            Font testFont = new Font(fontName, Font.PLAIN, 10);
            if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
                resultName = fontName;
            } else {
                resultName = currentFont.getName();
            }
        }
        Font font = new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
        boolean isMac = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("mac");
        Font fontWithFallback = isMac ? new Font(font.getFamily(), font.getStyle(), font.getSize()) : new StyleContext().getFont(font.getFamily(), font.getStyle(), font.getSize());
        return fontWithFallback instanceof FontUIResource ? fontWithFallback : new FontUIResource(fontWithFallback);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }

}
