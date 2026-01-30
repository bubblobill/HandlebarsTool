package net.rptools.data.overlay;

import java.util.ArrayList;
import java.util.List;

import static net.rptools.data.overlay.BarTokenOverlay.Side.TOP;

public class BarTokenOverlay extends AbstractTokenOverlay {

    public enum Type {
        Drawn,
        MultipleImage,
        SingleImage,
        TwoImage,
        TwoTone
    }
    public enum Side { TOP, BOTTOM, LEFT, RIGHT }
    private List<String> assetIds = new ArrayList<>();
    private List<Double> assetIdsAspectRatio = new ArrayList<>();
    private String bottomAssetId;
    private double bottomAssetIdAspectRatio = 1;
    private String topAssetId;
    private double topAssetIdAspectRatio = 1;
    private String barColor;
    private String bgColor;
    private int increments = 0;
    private String side = TOP.name();
    private int thickness = 0;
    private double value = 1;

    public void setType(Type type) {
        super.setType(type.name());
    }

    public List<String> getAssetIds() {
        return assetIds;
    }

    public void setAssetIds(List<String> assetIds) {
        this.assetIds = assetIds;
    }

    public List<Double> getAssetIdsAspectRatio() {
        return assetIdsAspectRatio;
    }

    public void setAssetIdsAspectRatio(List<Double> assetIdsAspectRatio) {
        this.assetIdsAspectRatio = assetIdsAspectRatio;
    }

    public String getBottomAssetId() {
        return bottomAssetId;
    }

    public void setBottomAssetId(String bottomAssetId) {
        this.bottomAssetId = bottomAssetId;
    }

    public double getBottomAssetIdAspectRatio() {
        return bottomAssetIdAspectRatio;
    }

    public void setBottomAssetIdAspectRatio(double bottomAssetIdAspectRatio) {
        this.bottomAssetIdAspectRatio = bottomAssetIdAspectRatio;
    }

    public String getTopAssetId() {
        return topAssetId;
    }

    public void setTopAssetId(String topAssetId) {
        this.topAssetId = topAssetId;
    }

    public double getTopAssetIdAspectRatio() {
        return topAssetIdAspectRatio;
    }

    public void setTopAssetIdAspectRatio(double topAssetIdAspectRatio) {
        this.topAssetIdAspectRatio = topAssetIdAspectRatio;
    }

    public String getBarColor() {
        return barColor;
    }

    public void setBarColor(String barColor) {
        this.barColor = barColor;
    }

    public String getBgColor() {
        return bgColor;
    }

    public void setBgColor(String bgColor) {
        this.bgColor = bgColor;
    }

    public int getIncrements() {
        return increments;
    }

    public void setIncrements(int increments) {
        this.increments = increments;
    }

    public String getSide() {
        return side;
    }

    public void setSide(Side side) {
        this.side = side.name();
    }

    public int getThickness() {
        return thickness;
    }

    public void setThickness(int thickness) {
        this.thickness = thickness;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }
}
