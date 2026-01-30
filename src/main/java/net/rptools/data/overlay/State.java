package net.rptools.data.overlay;

import java.awt.*;
import java.beans.XMLDecoder;
import java.io.FileInputStream;
import java.io.IOException;

public class State extends AbstractTokenOverlay {
    public enum Type {
        ColorDot,
        CornerImage,
        Cross,
        Diamond,
        FlowColorDot,
        FlowColorSquare,
        FlowDiamond,
        FlowImage,
        FlowTriangle,
        FlowYield,
        Image,
        O,
        Shaded,
        Triangle,
        X,
        Yield
    }
    public enum Corner { NORTH_WEST, NORTH_EAST, SOUTH_WEST, SOUTH_EAST }
    private String color = "rgb(0, 0, 0)";
    private String corner = "NORTH_WEST";
    private int grid = 1;
    private String group;
    private BasicStroke stroke;// dash__phase cap miterlimit join width

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getCorner() {
        return corner;
    }

    public void setCorner(Corner corner) {
        this.corner = corner.name();
    }

    public int getGrid() {
        return grid;
    }

    public void setGrid(int grid) {
        this.grid = grid;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public float getWidth() {
        return stroke.getLineWidth();
    }

    public void setWidth(float width) {
        this.stroke = new BasicStroke(width);
    }

    public BasicStroke getStroke() {
        return stroke;
    }

    public void setStroke(BasicStroke stroke) {
        this.stroke = stroke;
    }

    private static State deserializeFromXML() throws IOException {
        FileInputStream fis = new FileInputStream("settings.xml");
        XMLDecoder decoder = new XMLDecoder(fis);
        State decodedSettings = (State) decoder.readObject();
        decoder.close();
        fis.close();
        return decodedSettings;
    }
}
