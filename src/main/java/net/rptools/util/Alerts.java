package net.rptools.util;

import javax.swing.*;
import java.awt.*;

public class Alerts {
    private static final JOptionPane optionPane = new JOptionPane(null, JOptionPane.ERROR_MESSAGE, JOptionPane.DEFAULT_OPTION);
    private static final JPanel messagePanel = new JPanel();
    private static JDialog dialogue = null;
    static {
        optionPane.setOptions(new Object[]{"Okay"});
        optionPane.setMessage(messagePanel);
        BoxLayout box = new BoxLayout(messagePanel, BoxLayout.PAGE_AXIS);
        messagePanel.setLayout(box);
        optionPane.setVisible(false);
    }

    public static void alert(String notice, String... messages) {
        if (optionPane.isVisible()) {
            return;
        }
        messagePanel.removeAll();
        messagePanel.add(new JLabel(String.format("<html><h2 color=\"blue\">%s</h2></html>", notice)));
        StringBuilder builder = new StringBuilder("<html>");
        for (String message : messages) {
            builder.append("<p>").append(message).append("</p>");
        }
        builder.append("</html>");
        messagePanel.add(new JLabel(builder.toString()));
        new Thread(() -> {
            JOptionPane.showMessageDialog(null, messagePanel);
            optionPane.setVisible(false);
        }).start();
    }

    public static boolean prompt(Component parent, String message) {
        return JOptionPane.showConfirmDialog(parent, message, null, JOptionPane.YES_NO_OPTION) == JOptionPane.OK_OPTION;
    }

    public static void whoops(Throwable e) {
        if (dialogue != null || optionPane.isVisible()) {
            return;
        }
        messagePanel.removeAll();
        messagePanel.add(new JLabel(e.getLocalizedMessage()));
        StackTraceElement[] elements = e.getStackTrace();
        for (int i = 0; i < Math.min(12, elements.length); i++) {
            messagePanel.add(new JLabel(String.format("%s", elements[i].toString())));
        }
        optionPane.setVisible(true);
        dialogue = optionPane.createDialog("Error");
        dialogue.setModal(true);
        dialogue.pack();
        new Thread(() -> {
            dialogue.setVisible(true);
            dialogue = null;
            optionPane.setVisible(false);
        }).start();
    }
}
