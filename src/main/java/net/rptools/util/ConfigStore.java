package net.rptools.util;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.rptools.data.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.rptools.data.Config.*;
import static net.rptools.data.Constants.OBJECT_MAPPER;

@SuppressWarnings("unused")
public class ConfigStore {
    private static final Logger log = LoggerFactory.getLogger(ConfigStore.class);
    private final Path CONFIG_PATH;
    private final ObjectNode ROOT;
    private static final ObjectNode DEFAULTS = OBJECT_MAPPER.createObjectNode();
    private final List<JsonPointer> KEYS = new ArrayList<>();

    private static File configFile;

    private final AtomicBoolean resetting = new AtomicBoolean(false);
    private final AtomicBoolean useBackingFile = new AtomicBoolean(true);
    private final AtomicBoolean loaded = new AtomicBoolean(false);
    private final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    public ConfigStore() {
        this(null);
    }
    public ConfigStore(Path configPath) {
        setDefaults();
        ROOT = DEFAULTS.deepCopy();
        ROOT.fieldNames().forEachRemaining(name -> KEYS.addAll(addKey(name, KEYS)));

        boolean validFile = configPath != null;
        if(validFile){
            CONFIG_PATH = configPath.toAbsolutePath();
        } else {
            CONFIG_PATH = null;
        }
        if (validFile) {
            validFile = getConfigFile() != null;
        }
        if (validFile) {
            validFile = load();
        }
        if (validFile) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::save));
            log.info("Config file is {}", configPath);
        }
        useBackingFile.set(validFile);
    }

    private void setDefaults() {
        DEFAULTS.set(RESET, JsonNodeFactory.instance.booleanNode(false));
        DEFAULTS.set(BACKGROUND, JsonNodeFactory.instance.textNode("Textures/Grass.png"));
        DEFAULTS.set(DATASET_NAME, JsonNodeFactory.instance.textNode("Basic"));
        DEFAULTS.set(DATASET_DEFAULT, JsonNodeFactory.instance.textNode("Basic"));
        DEFAULTS.set(HANDLEBARS_PORT, JsonNodeFactory.instance.numberNode(6781));
        DEFAULTS.set(SERVER_PORT, JsonNodeFactory.instance.numberNode(7891));
        DEFAULTS.set(LOCATION, JsonNodeFactory.instance.textNode(Constants.StatSheetLocation.BOTTOM_LEFT.className()));
        DEFAULTS.set(SHEET, JsonNodeFactory.instance.textNode("Simple"));
        DEFAULTS.set(THEME, JsonNodeFactory.instance.textNode("Aah"));
        DEFAULTS.set(TEMPLATE_FOLDER, JsonNodeFactory.instance.textNode(System.getProperty("user.home")));
        DEFAULTS.set(LIB_FILE, JsonNodeFactory.instance.booleanNode(false));
        DEFAULTS.set(VIEW_AS, JsonNodeFactory.instance.textNode("player"));
        DEFAULTS.set(WATCH_FOLDER, JsonNodeFactory.instance.booleanNode(true));

        try (InputStream is = ConfigStore.class.getResourceAsStream("/data/tokenPropertyTypes.json")) {
            DEFAULTS.set(DATASETS, OBJECT_MAPPER.readTree(is));
            ArrayNode arrayNode = OBJECT_MAPPER.createArrayNode();
            DEFAULTS.get(DATASETS).fieldNames().forEachRemaining(arrayNode::add);
            DEFAULTS.set(DATASET_NAMES, arrayNode);
        } catch (IOException e) {
            Utils.whoops(e);
            log.error(e.getLocalizedMessage(), e);
            DEFAULTS.set(DATASETS, JsonNodeFactory.instance.objectNode());
        }

        try (InputStream is = ConfigStore.class.getResourceAsStream("/data/themeCss.json")) {
            DEFAULTS.set(THEME_CSS, OBJECT_MAPPER.readTree(is));
        } catch (IOException e) {
            Utils.whoops(e);
            log.error(e.getLocalizedMessage(), e);
            DEFAULTS.set(THEME_CSS, JsonNodeFactory.instance.objectNode());
        }
    }
    private List<JsonPointer> addKey(String key, List<JsonPointer> keyList){
        key = key.startsWith("/") ? key : "/" + key;
        JsonPointer pointer = JsonPointer.compile(key);
        if(missingKey(pointer)){
            keyList.add(pointer);
        }
        JsonNode node = ROOT.at(pointer);
        if(node instanceof ObjectNode objectNode){
            objectNode.fieldNames().forEachRemaining(name -> addKey(pointer.appendProperty(name).toString(), keyList));
        } else if(node instanceof ArrayNode arrayNode){
            for (int i = 0; i < arrayNode.size(); i++) {
                addKey(pointer.appendIndex(i).toString(), keyList);
            }
        }
        return keyList;
    }
    private synchronized boolean load() {
        final File file = getConfigFile();
        if(useBackingFile.get() && file != null) {
            synchronized (file) {
                try {
                    JsonNode readNode = OBJECT_MAPPER.readTree(new FileInputStream(file));
                    if (readNode instanceof ObjectNode objectNode) {
                        ROOT.setAll(objectNode);
                        loaded.set(true);
                        return true;
                    }
                } catch (IOException e) {
                    Utils.whoops(e);
                    log.error(e.getLocalizedMessage(), e);
                }
            }
        }
        return false;
    }

    private synchronized void save() {
        if (!useBackingFile.get()) {
            return;
        }
        final File file = getConfigFile();
        if (file == null) {
            return;
        }
        synchronized (file) {
            ObjectNode properties = ROOT.deepCopy();
            try {
                if (file.exists()) {
                    OBJECT_MAPPER.writeValue(new FileOutputStream(file), properties);
                }
            } catch (IOException e) {
                Utils.whoops(e);
                log.error(e.getLocalizedMessage(), e);
            }
        }
    }

    public File getConfigFile() {
        if(useBackingFile.get()) {
            if (configFile == null){
                configFile = CONFIG_PATH.toFile();
            }
            try {
                if (resetting.get()) {
                    Files.deleteIfExists(CONFIG_PATH);
                }
                if (!configFile.exists()) {
                    configFile = Files.createFile(CONFIG_PATH).toFile();
                    save();
                }
            } catch (IOException e) {
                Utils.whoops(e);
                log.info(e.getLocalizedMessage(), e);
                return null;
            }
        }
        return configFile;
    }

    public synchronized void reset() {
        resetting.set(true);
        synchronized (ROOT) {
            ROOT.removeAll();
            DEFAULTS.fieldNames().forEachRemaining(name ->
                    set(name, DEFAULTS.get(name)));
        }
        resetting.set(false);
    }

    public boolean missingKey(JsonPointer key) {
        return !KEYS.contains(key);
    }


    public JsonNode get(String key) {
        return getOrDefault(DEFAULTS.get(key), key);
    }

    /**
     * @param keys array of strings to use as keys
     * @return found node or default as node
     */
    public synchronized JsonNode getOrDefault(Object defaultValue, String... keys) {
        if(useBackingFile.get() && !loaded.get()){
            int count = 20;
            while (!loaded.get() && count > 0) {
                try {
                    wait(50);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                count--;
            }
        }
        synchronized (ROOT) {
            for (String key : keys) {
                if(key == null) {
                    continue;
                }
                JsonNode value;
                try {
                    if (!key.startsWith("/")) {
                        key = "/" + key;
                    }
                    JsonPointer pointer = JsonPointer.compile(key);
                    if(missingKey(pointer)){
                        continue;
                    }
                    value = ROOT.at(pointer);
                    if (value != null && !value.isMissingNode()) {
                        return value;
                    }
                } catch (NullPointerException e) {
//                 continue;
                }
            }
        }
        return switch (defaultValue) {
            case null -> JsonNodeFactory.instance.nullNode();
            case JsonNode jsonNode -> jsonNode;
            case Boolean v -> JsonNodeFactory.instance.booleanNode(v);
            case byte[] v -> JsonNodeFactory.instance.binaryNode(v);
            case Double v -> JsonNodeFactory.instance.numberNode(v);
            case Float v -> JsonNodeFactory.instance.numberNode(v);
            case Integer v -> JsonNodeFactory.instance.numberNode(v);
            case Long v -> JsonNodeFactory.instance.numberNode(v);
            case String v -> JsonNodeFactory.instance.textNode(v);
            default -> JsonNodeFactory.instance.missingNode();
        };
    }

    public synchronized void set(String key, Object value) {
        synchronized (ROOT) {
            JsonNode node = ROOT;
            while (key.contains("/")) {
                node = node.get(key.substring(0, key.indexOf('/')));
                key = key.substring(key.indexOf('/') + 1);
            }
            if (node instanceof ObjectNode objectNode) {
                if (value instanceof Boolean v) {
                    objectNode.set(key, JsonNodeFactory.instance.booleanNode(v));
                } else if (value instanceof byte[] v) {
                    objectNode.set(key, JsonNodeFactory.instance.binaryNode(v));
                } else if (value instanceof Double v) {
                    objectNode.set(key, JsonNodeFactory.instance.numberNode(v));
                } else if (value instanceof Float v) {
                    objectNode.set(key, JsonNodeFactory.instance.numberNode(v));
                } else if (value instanceof Integer v) {
                    objectNode.set(key, JsonNodeFactory.instance.numberNode(v));
                } else if (value instanceof Long v) {
                    objectNode.set(key, JsonNodeFactory.instance.numberNode(v));
                } else if (value instanceof JsonNode v) {
                    objectNode.set(key, v);
                } else if (value instanceof String v) {
                    objectNode.set(key, JsonNodeFactory.instance.textNode(v));
                }
            }
        }
        save();
    }
}