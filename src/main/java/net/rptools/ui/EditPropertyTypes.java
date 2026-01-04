package net.rptools.ui;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import net.rptools.Main;
import net.rptools.data.config.Config;
import net.rptools.data.config.Pref;
import net.rptools.data.Constants;
import net.rptools.data.TokenProperty;
import net.rptools.util.ImportCampaign;


import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static net.rptools.data.Constants.OBJECT_MAPPER;

public class EditPropertyTypes extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JButton deleteButton;
    private JButton newButton;
    private JTabbedPane tabbedPane;
    private JRadioButton PCRadioButton;
    private JRadioButton NPCRadioButton;
    private JTextField name;
    private JTextField label;
    private JTextField gmName;
    private JTextField speechName;
    private JSpinner portraitWidth;
    private JSpinner portraitHeight;
    private JTextField portrait;
    private JTextField image;
    private JTextArea notes;
    private JTextArea gmNotes;
    private JComboBox<Constants.NoteType> notesType;
    private JComboBox<Constants.NoteType> gmNotesType;
    private JComboBox<String> dataSetCombo;
    private JTable properties;
    private JButton addRow;
    private JButton deleteRow;
    private JButton upButton;
    private JButton downButton;
    private JTextField handout;
    private JCheckBox defaultCheckBox;
    private JButton imageBrowse;
    private JButton portraitBrowse;
    private JButton handoutBrowse;
    private JButton importButton;
    private DefaultTableModel tableModel;

    private static String datasetName = Pref.getString("Basic", Pref.getString(Config.DATASET_DEFAULT), Config.DATASET_NAME);
    //    private static final ObjectNode DATASETS = Pref.getObjectNode(Pref.DATASETS);
    private static final List<String> DATA_SET_NAMES = Pref.getList(Config.DATASETS);
    private static final ObjectNode DEFAULT_DATA_OBJECT = Pref.getObjectNode(Config.DATASETS + "/" + datasetName, Config.DATASETS + "/Basic");
    private ObjectNode tokenData;

    public EditPropertyTypes() {
        $$$setupUI$$$();
        setContentPane(contentPane);
        setModal(true);
        setLocationByPlatform(true);
        getRootPane().setDefaultButton(buttonOK);


        loadTokenData(datasetName);
        setValues();
        addListeners();

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        pack();
    }

    private void addListeners() {
        buttonOK.addActionListener(_ -> onOK());
        buttonCancel.addActionListener(_ -> onCancel());
        contentPane.registerKeyboardAction(_ -> onCancel(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        importButton.addActionListener(this::onImport);
        dataSetCombo.addItemListener(dataSetListener);
        defaultCheckBox.addActionListener(this::onSetDefault);
        newButton.addActionListener(this::onNew);
        deleteButton.addActionListener(this::onDelete);

        addRow.addActionListener(this::onAddRow);
        deleteRow.addActionListener(this::onDeleteRow);
        upButton.addActionListener(this::onUp);
        downButton.addActionListener(this::onDown);

        PCRadioButton.addActionListener(this::onPC);
        NPCRadioButton.addActionListener(this::onPC);

        portraitHeight.setModel(new SpinnerNumberModel(tokenData.get("portraitHeight").asDouble(), 20, 500, 1));
        portraitWidth.setModel(new SpinnerNumberModel(tokenData.get("portraitWidth").asDouble(), 20, 500, 1));

        String defaultDataset = Pref.getString(Config.DATASET_DEFAULT);
        if (defaultDataset != null && !defaultDataset.isBlank()) {
            dataSetCombo.setSelectedItem(defaultDataset);
        }

        imageBrowse.addActionListener(this::onBrowse);
        portraitBrowse.addActionListener(this::onBrowse);
        handoutBrowse.addActionListener(this::onBrowse);
    }

    private void setValues() {
        if (tokenData.get("tokenType").asText().equalsIgnoreCase("pc")) {
            PCRadioButton.setSelected(true);
        } else {
            NPCRadioButton.setSelected(true);
        }
        name.setText(tokenData.get("name").asText());
        label.setText(tokenData.get("label").asText());
        gmName.setText(tokenData.get("gmName").asText());
        speechName.setText(tokenData.get("speechName").asText());
        portrait.setText(tokenData.get("portrait").asText());
        image.setText(tokenData.get("image").asText());
        handout.setText(tokenData.get("handout").asText());
        notes.setText(tokenData.get("notes").asText());
        gmNotes.setText(tokenData.get("gmNotes").asText());
        gmNotesType.setSelectedItem(Constants.NoteType.fromString(tokenData.get("gmNotesType").asText()));
        notesType.setSelectedItem(Constants.NoteType.fromString(tokenData.get("notesType").asText()));

        populateTable();
    }

    private void onImport(ActionEvent e) {
        SwingUtilities.invokeLater(() -> {
            ImportCampaign.importProps(this.getParent());
            Main.getLauncher().onEditData();
        });
        onOK();
    }

    private static final JFileChooser IMAGE_CHOOSER = new JFileChooser(Pref.getPath(Config.TEMPLATE_FOLDER).toFile());

    static {
        IMAGE_CHOOSER.setMultiSelectionEnabled(false);
        IMAGE_CHOOSER.setFileSelectionMode(JFileChooser.FILES_ONLY);
    }

    private void onBrowse(ActionEvent e) {
        final JButton button = (JButton) e.getSource();
        JTextComponent textComponent;
        if (button.equals(imageBrowse)) {
            textComponent = image;
        } else if (button.equals(portraitBrowse)) {
            textComponent = portrait;
        } else if (button.equals(handoutBrowse)) {
            textComponent = handout;
        } else {
            return;
        }
        File file = Pref.getPath(Config.TEMPLATE_FOLDER).resolve(textComponent.getText().isBlank() ? "images" : textComponent.getText()).toAbsolutePath().toFile();
        if (file.exists()) {
            IMAGE_CHOOSER.setSelectedFile(file);
        }
        if (IMAGE_CHOOSER.showDialog(this, "Select") == JFileChooser.APPROVE_OPTION) {
            file = IMAGE_CHOOSER.getSelectedFile();
            try {
                BufferedImage bi = ImageIO.read(file);
                portraitWidth.setValue((double) portraitHeight.getValue() * bi.getHeight() / bi.getWidth());
            } catch (IOException _) {
                return;
            }
            Path value = Pref.getPath(Config.TEMPLATE_FOLDER).relativize(file.toPath().toAbsolutePath());
            textComponent.setText("./" + value.toString().replaceAll(Matcher.quoteReplacement("\\"), "/"));
        }
    }

    private void populateTable() {
        for (int i = properties.getRowCount() - 1; i > -1; i--) {
            tableModel.removeRow(properties.convertRowIndexToModel(i));
        }

        ArrayNode props = (ArrayNode) tokenData.get("properties");
        if (!props.isEmpty()) {
            props.forEach(prop -> addPropertyRow(new TokenProperty(
                    prop.get("gmOnly").asBoolean(),
                    false, // prop.get("ownerOnly").asBoolean(),
                    prop.get("name").asText(),
                    prop.get("displayName").asText(),
                    prop.get("shortName").asText(),
                    prop.get("value").asText()
            )));
        }
    }

    private void onUp(ActionEvent e) {
        int idx = properties.getSelectedRow();
        if (idx > 0) {
            int row = properties.convertRowIndexToModel(idx);
            tableModel.moveRow(row, row, row - 1);
            properties.setRowSelectionInterval(row - 1, row - 1);
        }
    }

    private void onDown(ActionEvent e) {
        int idx = properties.getSelectedRow();
        if (idx > -1 && idx < properties.getRowCount() - 1) {
            int row = properties.convertRowIndexToModel(idx);
            tableModel.moveRow(row, row, row + 1);
            properties.setRowSelectionInterval(row + 1, row + 1);
        }
    }

    private void onPC(ActionEvent e) {
        tokenData.put("tokenType", PCRadioButton.isSelected() ? "pc" : "npc");
    }

    private void onAddRow(ActionEvent e) {
        addPropertyRow(new TokenProperty(false, false, null, null, null, null));
    }

    private void onDeleteRow(ActionEvent e) {
        int idx = properties.getSelectedRow();
        if (idx > -1) {
            tableModel.removeRow(properties.convertRowIndexToModel(idx));
        }
    }

    private void onDelete(ActionEvent e) {
        int idx = dataSetCombo.getSelectedIndex();
        if (idx > -1) {
            String currentName = dataSetCombo.getItemAt(idx);
            if (currentName.equalsIgnoreCase("Default")) {
                Pref.getObjectNode(Config.DATASETS).remove(currentName);
            }
            dataSetCombo.removeItemAt(idx);
            if (dataSetCombo.getItemCount() > idx) {
                dataSetCombo.setSelectedIndex(idx);
            } else {
                dataSetCombo.setSelectedIndex(dataSetCombo.getItemCount() - 1);
            }
        }
    }

    private void onNew(ActionEvent e) {
        String s = JOptionPane.showInputDialog(this, "Name");
        //If a useful string was returned.
        if (s != null && !s.isBlank()) {
            saveTokenData();
            newTokenData(s);
        }
    }

    private void onOK() {
        saveTokenData();
        dispose();
    }

    private void onCancel() {
        dispose();
    }

    private void onSetDefault(ActionEvent e) {
        if (defaultCheckBox.isSelected()) {
            Pref.set(Config.DATASET_DEFAULT, dataSetCombo.getSelectedItem());
        }
    }


    private void createUIComponents() {
        notesType = new JComboBox<>(new DefaultComboBoxModel<>(Constants.NoteType.values()));
        gmNotesType = new JComboBox<>(new DefaultComboBoxModel<>(Constants.NoteType.values()));
        dataSetCombo = new JComboBox<>(new DefaultComboBoxModel<>(DATA_SET_NAMES.toArray(new String[0])));

        tableModel = new PropertiesModel();
        properties = new JTable(tableModel);
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
        contentPane.setLayout(new GridLayoutManager(2, 1, new Insets(10, 10, 10, 10), -1, -1));
        contentPane.setMinimumSize(new Dimension(-1, -1));
        contentPane.setPreferredSize(new Dimension(570, 400));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1, true, false));
        panel1.add(panel2, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        buttonOK = new JButton();
        buttonOK.setText("OK");
        panel2.add(buttonOK, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonCancel = new JButton();
        buttonCancel.setText("Cancel");
        panel2.add(buttonCancel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        importButton = new JButton();
        importButton.setText("Import");
        panel1.add(importButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel3.add(panel4, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        tabbedPane = new JTabbedPane();
        panel4.add(tabbedPane, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        tabbedPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Property Values", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        tabbedPane.addTab("General", panel5);
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridLayoutManager(6, 6, new Insets(0, 0, 0, 0), -1, -1));
        panel5.add(panel6, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("tokenType");
        panel6.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        PCRadioButton = new JRadioButton();
        PCRadioButton.setHorizontalTextPosition(10);
        PCRadioButton.setName("pc");
        PCRadioButton.setText("PC");
        panel6.add(PCRadioButton, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        NPCRadioButton = new JRadioButton();
        NPCRadioButton.setName("npc");
        NPCRadioButton.setText("NPC");
        panel6.add(NPCRadioButton, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("name");
        panel6.add(label2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        name = new JTextField();
        name.setName("name");
        panel6.add(name, new GridConstraints(1, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("label");
        panel6.add(label3, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        label = new JTextField();
        label.setName("label");
        panel6.add(label, new GridConstraints(2, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("gmName");
        panel6.add(label4, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        gmName = new JTextField();
        gmName.setName("gmName");
        panel6.add(gmName, new GridConstraints(3, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("speechName");
        panel6.add(label5, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        speechName = new JTextField();
        speechName.setName("speechName");
        panel6.add(speechName, new GridConstraints(4, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label6 = new JLabel();
        label6.setText("portrait path");
        panel6.add(label6, new GridConstraints(1, 3, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label7 = new JLabel();
        label7.setText("image path");
        panel6.add(label7, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        portrait = new JTextField();
        portrait.setName("portrait");
        panel6.add(portrait, new GridConstraints(1, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        image = new JTextField();
        image.setName("image");
        panel6.add(image, new GridConstraints(0, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label8 = new JLabel();
        label8.setText("portraitWidth");
        panel6.add(label8, new GridConstraints(4, 3, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        portraitWidth = new JSpinner();
        portraitWidth.setName("portraitWidth");
        panel6.add(portraitWidth, new GridConstraints(4, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label9 = new JLabel();
        label9.setText("portraitHeight");
        panel6.add(label9, new GridConstraints(3, 3, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        portraitHeight = new JSpinner();
        portraitHeight.setName("portraitHeight");
        panel6.add(portraitHeight, new GridConstraints(3, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label10 = new JLabel();
        label10.setText("handout path");
        panel6.add(label10, new GridConstraints(2, 3, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        handout = new JTextField();
        handout.setName("handout");
        panel6.add(handout, new GridConstraints(2, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        imageBrowse = new JButton();
        imageBrowse.setText("...");
        panel6.add(imageBrowse, new GridConstraints(0, 5, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, 1, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(30, -1), new Dimension(30, -1), 0, false));
        portraitBrowse = new JButton();
        portraitBrowse.setText("...");
        panel6.add(portraitBrowse, new GridConstraints(1, 5, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, 1, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(30, -1), new Dimension(30, -1), 0, false));
        handoutBrowse = new JButton();
        handoutBrowse.setText("...");
        panel6.add(handoutBrowse, new GridConstraints(2, 5, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, 1, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(30, -1), new Dimension(30, -1), 0, false));
        final Spacer spacer2 = new Spacer();
        panel6.add(spacer2, new GridConstraints(5, 1, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final Spacer spacer3 = new Spacer();
        panel6.add(spacer3, new GridConstraints(5, 5, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final Spacer spacer4 = new Spacer();
        panel6.add(spacer4, new GridConstraints(5, 4, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final Spacer spacer5 = new Spacer();
        panel6.add(spacer5, new GridConstraints(5, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final Spacer spacer6 = new Spacer();
        panel6.add(spacer6, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JPanel panel7 = new JPanel();
        panel7.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        tabbedPane.addTab("Properties", panel7);
        final JScrollPane scrollPane1 = new JScrollPane();
        panel7.add(scrollPane1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        properties.setName("properties");
        scrollPane1.setViewportView(properties);
        final JPanel panel8 = new JPanel();
        panel8.setLayout(new GridLayoutManager(1, 5, new Insets(8, 5, 8, 5), -1, -1));
        panel7.add(panel8, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        addRow = new JButton();
        addRow.setDoubleBuffered(false);
        addRow.setText("Add");
        panel8.add(addRow, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL, 1, 1, null, null, null, 0, false));
        final Spacer spacer7 = new Spacer();
        panel8.add(spacer7, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        deleteRow = new JButton();
        deleteRow.setEnabled(true);
        deleteRow.setText("Delete");
        panel8.add(deleteRow, new GridConstraints(0, 4, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL, 1, 1, null, null, null, 0, false));
        upButton = new JButton();
        upButton.setText("▲");
        panel8.add(upButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTHEAST, GridConstraints.FILL_NONE, 1, 1, null, null, null, 0, false));
        downButton = new JButton();
        downButton.setText("▼");
        panel8.add(downButton, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_NONE, 1, 1, null, null, null, 0, false));
        final JPanel panel9 = new JPanel();
        panel9.setLayout(new GridLayoutManager(5, 4, new Insets(0, 0, 0, 0), -1, -1));
        tabbedPane.addTab("Notes", panel9);
        final Spacer spacer8 = new Spacer();
        panel9.add(spacer8, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JLabel label11 = new JLabel();
        label11.setText("notes");
        panel9.add(label11, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label12 = new JLabel();
        label12.setText("notesType");
        panel9.add(label12, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        notesType.setName("notesType");
        panel9.add(notesType, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer9 = new Spacer();
        panel9.add(spacer9, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JLabel label13 = new JLabel();
        label13.setText("gmNotes");
        panel9.add(label13, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label14 = new JLabel();
        label14.setText("gmNotesType");
        panel9.add(label14, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        gmNotesType.setName("gmNotesType");
        panel9.add(gmNotesType, new GridConstraints(2, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JScrollPane scrollPane2 = new JScrollPane();
        panel9.add(scrollPane2, new GridConstraints(1, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        notes = new JTextArea();
        notes.setName("notes");
        scrollPane2.setViewportView(notes);
        final JScrollPane scrollPane3 = new JScrollPane();
        panel9.add(scrollPane3, new GridConstraints(3, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        gmNotes = new JTextArea();
        gmNotes.setName("gmNotes");
        scrollPane3.setViewportView(gmNotes);
        final JPanel panel10 = new JPanel();
        panel10.setLayout(new GridLayoutManager(1, 5, new Insets(8, 8, 8, 8), -1, -1));
        panel4.add(panel10, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel10.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Token Property Types", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final Spacer spacer10 = new Spacer();
        panel10.add(spacer10, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        panel10.add(dataSetCombo, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        deleteButton = new JButton();
        deleteButton.setText("Delete");
        panel10.add(deleteButton, new GridConstraints(0, 4, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        newButton = new JButton();
        newButton.setText("New");
        panel10.add(newButton, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        defaultCheckBox = new JCheckBox();
        defaultCheckBox.setText("Default");
        panel10.add(defaultCheckBox, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        ButtonGroup buttonGroup;
        buttonGroup = new ButtonGroup();
        buttonGroup.add(PCRadioButton);
        buttonGroup.add(PCRadioButton);
        buttonGroup.add(NPCRadioButton);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }

    public void addPropertyRow(TokenProperty property) {
        tableModel.insertRow(tableModel.getRowCount(), new Object[]{property.gmOnly(), property.ownerOnly(), property.name(), property.displayName(), property.shortName(), property.value()});
    }

    private void saveTokenData() {
        tokenData.put("tokenType", PCRadioButton.isSelected() ? "pc" : "npc");
        tokenData.put("name", name.getText());
        tokenData.put("label", label.getText());
        tokenData.put("gmName", gmName.getText());
        tokenData.put("speechName", speechName.getText());
        tokenData.put("portrait", portrait.getText());
        tokenData.put("image", image.getText());
        tokenData.put("handout", handout.getText());
        tokenData.put("notes", notes.getText());
        tokenData.put("gmNotes", gmNotes.getText());
        tokenData.put("notesType", ((Constants.NoteType) Objects.requireNonNull(notesType.getSelectedItem())).getType());
        tokenData.put("gmNotesType", ((Constants.NoteType) Objects.requireNonNull(gmNotesType.getSelectedItem())).getType());

        int rows = properties.getModel().getRowCount();
        int cols = properties.getModel().getColumnCount();
        ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
        for (int row = 0; row < rows; row++) {
            ObjectNode node = JsonNodeFactory.instance.objectNode();
            for (int col = 0; col < cols; col++) {
                String propertyName = switch (col) {
                    case 0 -> "gmOnly";
                    case 1 -> "ownerOnly";
                    case 2 -> "name";
                    case 3 -> "displayName";
                    case 4 -> "shortName";
                    default -> "value";
                };
                if (propertyName.equalsIgnoreCase("gmOnly") ||
                        propertyName.equalsIgnoreCase("ownerOnly")) {
                    node.put(propertyName, (boolean) tableModel.getValueAt(row, col));
                } else {
                    node.put(propertyName, (String) tableModel.getValueAt(row, col));
                }
            }
            arrayNode.add(node);
        }
        tokenData.set("properties", arrayNode);
        Pref.set(Config.DATASETS + "/" + datasetName, tokenData);
        ArrayNode names = OBJECT_MAPPER.createArrayNode();
        Pref.getObjectNode(Config.DATASETS).fieldNames().forEachRemaining(names::add);
        Pref.set(Config.DATASET_NAMES, names);
    }

    private void newTokenData(String name) {
        name = name.strip().replaceAll(" ", "_");
        saveTokenData();
        DATA_SET_NAMES.add(name);
        dataSetCombo.addItem(name);
        dataSetCombo.setSelectedItem(name);
    }

    private void loadTokenData(String name) {
        datasetName = name.strip().replaceAll(" ", "_");
        boolean exists = !DATA_SET_NAMES.stream().filter(datasetName::equalsIgnoreCase).collect(Collectors.toSet()).isEmpty();

        ObjectNode data;
        if (exists) {
            data = Pref.getObjectNode(Config.DATASETS + "/" + datasetName);
            if (data.isEmpty()) {
                data = DEFAULT_DATA_OBJECT.deepCopy();
            }
        } else {
            data = DEFAULT_DATA_OBJECT.deepCopy();
        }

        tokenData = data;
        populateTable();
        Pref.set(Config.DATASET_NAME, datasetName);
    }

    private final ItemListener dataSetListener = (e) -> {
        if (e.getStateChange() == ItemEvent.SELECTED) {
            saveTokenData();
            String datasetName = (String) e.getItem();
            defaultCheckBox.setSelected(datasetName.equalsIgnoreCase(Pref.getString(Config.DATASET_DEFAULT)));
            loadTokenData(datasetName);
        }
    };

    private static class PropertiesModel extends DefaultTableModel {
        PropertiesModel() {
            super(new String[]{"GM Only", "Owner Only", "Name", "Display Name", "Short Name", "Value"}, 0);
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex < 2) {
                return Boolean.class;
            } else {
                return String.class;
            }
        }
    }
}
