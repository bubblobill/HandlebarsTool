package net.rptools.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.rptools.util.ConfigStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

@SuppressWarnings("unused")
public class Config {
    private static final Logger log = LoggerFactory.getLogger(Config.class);
    private static final Path CONFIG_PATH = Path.of(System.getProperty("user.dir"), File.separator, ".config.json").toAbsolutePath();
    public static final String SYSTEM_PROPERTY_FILE = Config.class.getName() + ".file";

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

    private static final ConfigStore CONFIG_STORE = new ConfigStore(CONFIG_PATH);

    public static File getConfigFile(){
        return CONFIG_STORE.getConfigFile();
    }
    public static void reset(){
        CONFIG_STORE.reset();
    }

    public static JsonNode get(String key) {
        return CONFIG_STORE.get(key);
    }

    /**
     * @param keys array of strings to use as keys
     * @return found node or default as node
     */
    public static JsonNode getOrDefault(Object defaultValue, String... keys) {
        return CONFIG_STORE.getOrDefault(defaultValue, keys);
    }

    public static synchronized void set(String key, Object value) {
        CONFIG_STORE.set(key, value);
    }

    public static boolean getBoolean(boolean defaultValue, String... keys) {
        return getOrDefault(defaultValue, keys).asBoolean();
    }

    public static boolean getBoolean(String key) {
        return get(key).asBoolean();
    }

    public static int getInt(int defaultValue, String... keys) {
        return getOrDefault(defaultValue, keys).asInt();
    }

    public static int getInt(String key) {
        return get(key).asInt();
    }

    public static long getLong(long defaultValue, String... keys) {
        return getOrDefault(defaultValue, keys).asInt();
    }

    public static long getLong(String key) {
        return get(key).asLong();
    }

    public static float getFloat(float defaultValue, String... keys) {
        return (float) getOrDefault(defaultValue, keys).asDouble();
    }

    public static float getFloat(String key) {
        return (float) get(key).asDouble();
    }

    public static String getString(String defaultValue, String... keys) {
        return getOrDefault(defaultValue, keys).asText();
    }

    public static String getString(String key) {
        return get(key).asText();
    }

    public static byte[] getByteArray(byte[] defaultValue, String... keys) {
        return getOrDefault(defaultValue, keys).asToken().asByteArray();
    }

    public static byte[] getByteArray(String key) {
        return getByteArray(new byte[0], key);
    }

    public static List<String> getList(String key) {
        List<String> list = new ArrayList<>();
        getObjectNode(key)
                .fieldNames()
                .forEachRemaining(list::add);
        return list;
    }

    public static ArrayNode getArrayNode(ArrayNode defaultValue, String... keys) {
        return (ArrayNode) getOrDefault(defaultValue, keys);
    }

    public static ArrayNode getArrayNode(String key) {
        return getArrayNode(JsonNodeFactory.instance.arrayNode(), key);
    }

    public static ObjectNode getObjectNode(ObjectNode defaultValue, String... keys) {
        return (ObjectNode) getOrDefault(defaultValue, keys);
    }

    public static ObjectNode getObjectNode(String... keys) {
        return getObjectNode(JsonNodeFactory.instance.objectNode(), keys);
    }

    public static Path getPath(String key) {
        return Path.of(getString(key));
    }
}