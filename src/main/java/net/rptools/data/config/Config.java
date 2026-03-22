package net.rptools.data.config;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class Config {
    public static final String RESET = "reset";
    public static final String BACKGROUND = "background";
    public static final String VIEW_AS = "viewAs";
    public static final String BARS = "bars";
    public static final String SHOW_PORTRAIT = "showPortrait";
    public static final String STATES = "states";
    public static final String CURRENT_PROPERTY_TYPE = "currentPropertyName";
    public static final String PROPERTY_TYPE_NAMES = "propertyTypeNames";
    public static final String DEFAULT_PROPERTY_TYPE = "propertyTypeDefault";
    public static final String PROPERTY_TYPES = "propertyTypes";
    public static final String CURRENT_SHEET_NAME = "sheet";
    public static final String SHEET_LOCATION = "statSheetLocation";
    public static final String SERVER_PORT = "serverPort";
    public static final String ADD_ON_FOLDER = "addonFolder";
    public static final String USE_ADD_ON_JSON_FILE = "useLibFile";
    public static final String TEMPLATE_FOLDER = "templateFolder";
    public static final String ASSETS_FOLDER = "assetsFolder";
    public static final String TOKEN_IMAGES_FOLDER = "tokenImagesFolder";
    public static final String LAST_IMPORT_PATH = "lastImportPath";
    public static final String WATCH_FOLDER = "watchFolder";
    public static final String CURRENT_THEME = "theme";
    public static final String ALL_THEME_CSS = "themeCss";

    public static final List<String> FIELD_NAMES;

    static {
        List<String> list = new ArrayList<>();
        for (Field field : Config.class.getDeclaredFields()) {
            if (field.getType().isAssignableFrom(String.class)) {
                try {
                    list.add(field.get(field).toString());
                } catch (IllegalAccessException | IllegalArgumentException _) {
                }
            }
        }
        FIELD_NAMES = list;
    }
}
