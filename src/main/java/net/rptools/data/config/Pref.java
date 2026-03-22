package net.rptools.data.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import net.rptools.data.Constants;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;

@SuppressWarnings("unused")
public class Pref {
    private static final Logger log = LoggerFactory.getLogger(Pref.class);

    private static final ConfigStore CONFIG_STORE = new ConfigStore();

    public static Path getConfigFile(){
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
        get(key)
                .fieldNames()
                .forEachRemaining(list::add);
        return list;
    }

    public static ArrayNode getArrayNode(ArrayNode defaultValue, String... keys) {
        return (ArrayNode) getOrDefault(defaultValue, keys);
    }

    public static ArrayNode getArrayNode(String key) {
        return (ArrayNode) get(key);
    }

    public static ObjectNode getObjectNode(ObjectNode defaultValue, String... keys) {
        return (ObjectNode) getOrDefault(defaultValue, keys);
    }

    public static ObjectNode getObjectNode(String... keys) {
        return getObjectNode((ObjectNode) get(keys[0]), keys);
    }

    public static Path getPath(String key) {
        return Path.of(getString(key));
    }

    public  static <T> Object getPref(String... lookup){
        try{
            return getPref_(lookup);
        } catch (Exception e){
            log.error(e.getLocalizedMessage(), e);
            return lookup;
        }
    }
    public  static <T> Object getPref(String lookup){
        try{
            return getPref_(lookup);
        } catch (Exception e){
            log.error(e.getLocalizedMessage(), e);
            return lookup;
        }
    }
    private static <T> Object getPref_(String... lookup) throws IllegalArgumentException, ClassCastException{
        Type type = Type.lookupType(lookup[0]);
        if(type == null){
            throw new IllegalArgumentException("Unknown preference Type.");
        }
        JsonNode got = CONFIG_STORE.get(Strings.join(Arrays.asList(lookup), '/'));
        Object value;
        if(!got.getClass().isAssignableFrom(type.nodeClass)) {
            throw new ClassCastException("Wrong type returned.");
        }
        if(type.valueClass.equals(Boolean.class)) {
            value = got.asBoolean();
        } else if(type.valueClass.equals(Integer.class)) {
            value = got.asInt();
        } else if(type.valueClass.equals(Double.class)) {
            value = got.asDouble();
        } else if(type.valueClass.equals(Path.class)) {
            value = Path.of(got.asText());
        } else if(type.valueClass.equals(String.class)) {
            value = got.asText();
        } else if(type.valueClass.equals(ObjectNode.class) && got instanceof ObjectNode objectNode) {
            value = objectNode;
        } else if(type.valueClass.equals(ArrayNode.class) && got instanceof ArrayNode arrayNode) {
            value = arrayNode;
        } else {
            value = got.asText();
        }
        if(!value.getClass().isAssignableFrom(type.valueClass)){
            throw new ClassCastException("Wrong value class.");
        } else {
            return (T) value;
        }
    }
    private enum Type {
        RESET                ("reset",               ValueNode.class,  Boolean.class),
        BACKGROUND           ("background",          ValueNode.class,  String.class),
        VIEW_AS              ("viewAs",              ValueNode.class,  String.class),
        BARS                 ("bars",                ObjectNode.class, ObjectNode.class),
        STATES               ("states",              ObjectNode.class, ObjectNode.class),
        CURRENT_PROPERTY_TYPE("currentPropertyName", ValueNode.class,  String.class),
        PROPERTY_TYPE_NAMES  ("propertyTypeNames",   ArrayNode.class,  ArrayNode.class),
        DEFAULT_PROPERTY_TYPE("propertyTypeDefault", ValueNode.class,  String.class),
        PROPERTY_TYPES       ("propertyTypes",       ObjectNode.class, ObjectNode.class),
        CURRENT_SHEET_NAME   ("sheet",               ValueNode.class,  String.class),
        SHEET_LOCATION       ("statSheetLocation",   ValueNode.class,  Constants.StatSheetLocation.class),
        SHOW_PORTRAIT        ("showPortrait",        ValueNode.class,  Boolean.class),
        SERVER_PORT          ("serverPort",          ValueNode.class,  Integer.class),
        ADD_ON_FOLDER        ("addonFolder",         ValueNode.class,  Path.class),
        USE_ADD_ON_JSON_FILE ("useLibFile",          ValueNode.class,  Boolean.class),
        TEMPLATE_FOLDER      ("templateFolder",      ValueNode.class,  Path.class),
        ASSETS_FOLDER        ("assetsFolder",        ValueNode.class,  Path.class),
        TOKEN_IMAGES_FOLDER  ("tokenImagesFolder",   ValueNode.class,  Path.class),
        WATCH_FOLDER         ("watchFolder",         ValueNode.class,  Path.class),
        CURRENT_THEME        ("theme",               ValueNode.class,  String.class),
        ALL_THEME_CSS        ("themeCss",            ObjectNode.class, ObjectNode.class)
        ;
        private final String keyString;
        private final Class<?> nodeClass;
        private final Class<?> valueClass;
        Type(String keyString, Class<?> nodeClass, Class<?> valueClass){
            this.keyString = keyString;
            this.nodeClass = nodeClass;
            this.valueClass = valueClass;
        }

        private static Type lookupType(String lookup){
            for(Type type: Type.values()){
                if(type.keyString.equals(lookup)){
                    return type;
                }
            }
            return null;
        }
    }
}