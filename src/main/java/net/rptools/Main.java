package net.rptools;

import net.rptools.ui.Launcher;

public class Main {
    private static Launcher launcher;

    public static void main(String[] args) {
        launcher = new Launcher();
        launcher.setVisible(true);
        System.exit(0);
    }
    public static Launcher getLauncher(){
        return launcher;
    }

}