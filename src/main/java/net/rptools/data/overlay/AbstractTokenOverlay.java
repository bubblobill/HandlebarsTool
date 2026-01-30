package net.rptools.data.overlay;

public abstract class AbstractTokenOverlay {
    String name = "";
    String type;
    String assetId = "";
    double assetIdAspectRatio = 1;
    double opacity = 1;
    int order = 0;
    boolean mouseover = false;
    boolean showGM = true;
    boolean showOthers = true;
    boolean showOwner = true;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public double getAssetIdAspectRatio() {
        return assetIdAspectRatio;
    }

    public void setAssetIdAspectRatio(double assetIdAspectRatio) {
        this.assetIdAspectRatio = assetIdAspectRatio;
    }

    public double getOpacity() {
        return opacity;
    }

    public void setOpacity(double opacity) {
        this.opacity = opacity;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public boolean isMouseover() {
        return mouseover;
    }

    public void setMouseover(boolean mouseover) {
        this.mouseover = mouseover;
    }

    public boolean isShowGM() {
        return showGM;
    }

    public void setShowGM(boolean showGM) {
        this.showGM = showGM;
    }

    public boolean isShowOthers() {
        return showOthers;
    }

    public void setShowOthers(boolean showOthers) {
        this.showOthers = showOthers;
    }

    public boolean isShowOwner() {
        return showOwner;
    }

    public void setShowOwner(boolean showOwner) {
        this.showOwner = showOwner;
    }

    protected String getType() {
        return type;
    }

    protected void setType(String type) {
        this.type = type;
    }
}
