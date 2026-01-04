package net.rptools.data.config;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

public class Config {
    public static final String RESET = "reset";
    public static final String BACKGROUND = "background";
    public static final String DATASET_NAME = "datasetName";
    public static final String DATASET_NAMES = "datasetNames";
    public static final String DATASET_DEFAULT = "datasetDefault";
    public static final String DATASETS = "datasets";
    public static final String TEMPLATE_FOLDER = "folder";
    public static final String LIB_FILE = "libFile";
    public static final String SHEET = "sheet";
    public static final String LOCATION = "statSheetLocation";
    public static final String HANDLEBARS_PORT = "handlebarsPort";
    public static final String SERVER_PORT = "serverPort";
    public static final String THEME = "theme";
    public static final String THEME_CSS = "themeCss";
    public static final String VIEW_AS = "viewAs";
    public static final String WATCH_FOLDER = "watchFolder";

    public static final List<String> FIELD_NAMES;

    static {
        FIELD_NAMES = Arrays.stream(Config.class.getDeclaredFields())
                .filter(field -> field.getType().equals(String.class))
                .map(Field::getName)
                .toList();
    }
}
