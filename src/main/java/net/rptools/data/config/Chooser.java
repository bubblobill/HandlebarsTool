package net.rptools.data.config;

import net.rptools.data.Constants;
import net.rptools.data.SheetsObject;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;

import static net.rptools.data.config.Config.ADD_ON_FOLDER;
import static net.rptools.data.config.Config.TEMPLATE_FOLDER;

public class Chooser {
    private static final String APPROVE_BUTTON_TEXT = "Select Folder";
    public static final JFileChooser FC;

    static {
        JFileChooser fileChooser;
        if (!Pref.getString(TEMPLATE_FOLDER).isBlank()) {
            fileChooser = new JFileChooser(Pref.getString(TEMPLATE_FOLDER));
        } else if(!Pref.getString(ADD_ON_FOLDER).isBlank()){
            fileChooser = new JFileChooser(Pref.getString(ADD_ON_FOLDER));
        } else {
            fileChooser = new JFileChooser(Constants.USER_DIR.toString());
        }
        FC = fileChooser;
        FC.setDialogTitle("Select sheet templates directory");
        FC.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        FC.setMultiSelectionEnabled(false);
    }


    public static boolean selectAddonRootFolder(Component component) {
        FC.setDialogTitle("Select Add-On root directory");
        if(!Pref.getString(ADD_ON_FOLDER).isBlank()){
            FC.setSelectedFile(Pref.getPath(ADD_ON_FOLDER).toFile());
        }
        if(FC.showDialog(component, APPROVE_BUTTON_TEXT) == JFileChooser.APPROVE_OPTION){
            Pref.set(ADD_ON_FOLDER, FC.getSelectedFile().toPath().toAbsolutePath());
            FC.setDialogTitle("Select sheet templates directory");
            return true;
        }
        return false;
    }


    public static boolean selectTemplateFolder(Component component) {
        if(!Pref.getString(TEMPLATE_FOLDER).isBlank()){
            FC.setSelectedFile(Pref.getPath(TEMPLATE_FOLDER).toFile());
        }
        if (FC.showDialog(component, APPROVE_BUTTON_TEXT) == JFileChooser.APPROVE_OPTION) {
            Path path = FC.getSelectedFile().toPath().toAbsolutePath();
            Pref.set(TEMPLATE_FOLDER, path);
            SheetsObject.setFolder(path);
            return true;
        }
        return false;
    }
}
