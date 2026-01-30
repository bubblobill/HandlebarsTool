package net.rptools.data.config;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.rptools.data.Constants;
import net.rptools.util.Alerts;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.rptools.data.Constants.OBJECT_MAPPER;
import static net.rptools.data.config.Config.*;

@SuppressWarnings("unused")
public class ConfigStore {
    private static final Logger log = LoggerFactory.getLogger(ConfigStore.class);
    private static final Path FILE_PATH = Constants.USER_DIR.resolve(".config.json");
    private static final Path DEFAULT_ASSETS_PATH = Constants.USER_DIR.resolve(".assets");
    private static final String DEFAULT_CONFIG_PATH = "/data/configData.json";
    private static final String THEME_CSS_PATH = "/data/themeCss.json";
    private static final String DEFAULT_DATASETS_PATH = "/data/tokenPropertyTypes.json";
    private static final ObjectNode DEFAULTS;

    private static final AtomicBoolean useBackingFile = new AtomicBoolean(true);
    private static final AtomicBoolean loaded = new AtomicBoolean(false);
    private static final AtomicBoolean resetting = new AtomicBoolean(false);

    private static Path configFile;
    private static final ObjectNode ROOT;
    private final List<JsonPointer> KEYS = new ArrayList<>();

    static {
        ObjectNode defaultsNode;
        try {
            defaultsNode = (ObjectNode) OBJECT_MAPPER.readTree(IOUtils.resourceToString(DEFAULT_CONFIG_PATH, StandardCharsets.UTF_8));
            defaultsNode.set(Config.ALL_THEME_CSS, OBJECT_MAPPER.readTree(IOUtils.resourceToString(THEME_CSS_PATH, StandardCharsets.UTF_8)));
            defaultsNode.set(PROPERTY_TYPES, OBJECT_MAPPER.readTree(IOUtils.resourceToString(DEFAULT_DATASETS_PATH, StandardCharsets.UTF_8)));
            defaultsNode.put(TEMPLATE_FOLDER, Constants.USER_DIR.toString());
            defaultsNode.put(LAST_IMPORT_PATH, Constants.USER_DIR.toString());
            defaultsNode.put(ASSETS_FOLDER, DEFAULT_ASSETS_PATH.toString());
            ArrayNode propertyTypeNames = OBJECT_MAPPER.createArrayNode();
            defaultsNode.set(PROPERTY_TYPE_NAMES, propertyTypeNames);
            defaultsNode.get(Config.PROPERTY_TYPES).properties().forEach(entry -> propertyTypeNames.add(entry.getKey()));
            defaultsNode.set(CURRENT_PROPERTY_TYPE, propertyTypeNames.get(0));
            defaultsNode.set(DEFAULT_PROPERTY_TYPE, propertyTypeNames.get(0));
        } catch (IOException e) {
            log.error(e.getLocalizedMessage(), e);
            defaultsNode = OBJECT_MAPPER.createObjectNode();
        }
        DEFAULTS = defaultsNode;
        ObjectNode rootNode = OBJECT_MAPPER.valueToTree(DEFAULTS);
        if (!Files.exists(FILE_PATH)) {
            // create new config file
            try {
                configFile = Files.createFile(FILE_PATH);
                log.info("New config file created at {}.", FILE_PATH);
                try (OutputStream os = Files.newOutputStream(FILE_PATH)) {
                    OBJECT_MAPPER.writeValue(os, DEFAULTS);
                } catch (IOException e) {
                    useBackingFile.set(false);
                    log.error("Unable to write to new config file.\n{}, {}", e.getLocalizedMessage(), e);
                }
            } catch (IOException e) {
                useBackingFile.set(false);
                log.error("Unable to create config file.");
            }
        } else if (Files.isReadable(FILE_PATH)) {
            // load from existing config file
            try {
                rootNode = (ObjectNode) OBJECT_MAPPER.readTree(IOUtils.toString(FILE_PATH.toUri(), StandardCharsets.UTF_8));
                loaded.set(true);
            } catch (IOException e) {
                useBackingFile.set(false);
                rootNode.removeAll();
                log.error("Unable to read existing config file.\n{}, {}", e.getLocalizedMessage(), e);
            }
            if (Files.isWritable(FILE_PATH)) {
                // only set if writable.
                configFile = FILE_PATH;
            } else {
                useBackingFile.set(false);
                log.error("Unable to write to config file.");
            }
        } else {
            useBackingFile.set(false);
            rootNode = DEFAULTS.deepCopy();
        }
        ROOT = OBJECT_MAPPER.valueToTree(rootNode);
        FIELD_NAMES.forEach(name -> {
            JsonNode node = ROOT.get(name);
            if(node == null || node.isNull() || node.isMissingNode() ||
            (node.isContainerNode() && node.isEmpty())){
                ROOT.set(name, DEFAULTS.get(name));
            }
        });
    }


    public ConfigStore() {
        if(ROOT.get(RESET).asBoolean()){
            reset();
        }
        if(ROOT.get(CURRENT_PROPERTY_TYPE).asText().isBlank()){
            set(CURRENT_PROPERTY_TYPE, getOrDefault(ROOT.get(PROPERTY_TYPE_NAMES).get(0), ROOT.get(DEFAULT_PROPERTY_TYPE).asText()));
        }
        ROOT.properties().forEach(nodeEntry -> addKey(nodeEntry.getKey()));
    }

    private void addKey(String key) {
        key = key.startsWith("/") ? key : "/" + key;
        JsonPointer pointer = JsonPointer.compile(key);
        if (!KEYS.stream().map(JsonPointer::toString).toList().contains(key)) {
            KEYS.add(pointer);
        }
        JsonNode node = ROOT.at(pointer);
        if (node instanceof ObjectNode objectNode) {
            objectNode.properties().forEach(entry -> addKey(pointer.appendProperty(entry.getKey()).toString()));
        } else if (node instanceof ArrayNode arrayNode) {
            for (int i = 0; i < arrayNode.size(); i++) {
                addKey(pointer.appendIndex(i).toString());
            }
        }
    }

    private synchronized void save() {
        if (!useBackingFile.get()) {
            return;
        }
        final Path file = getConfigFile();
        if (file == null) {
            return;
        }
        synchronized (ROOT) {
            ROOT.remove(ALL_THEME_CSS);
            synchronized (file) {
                try {
                    if (Files.exists(file)) {
                        OBJECT_MAPPER.writeValue(new FileOutputStream(file.toFile()), ROOT);
                    }
                } catch (IOException e) {
                    Alerts.whoops(e);
                    log.error(e.getLocalizedMessage(), e);
                }
            }
        }
    }

    public Path getConfigFile() {
        if (useBackingFile.get()) {
            try {
                if (resetting.get()) {
                    Files.deleteIfExists(FILE_PATH);
                }
                if (!Files.exists(configFile)) {
                    configFile = Files.createFile(FILE_PATH);
                    log.debug("Pref file created.");
                    save();
                }
            } catch (IOException e) {
                Alerts.whoops(e);
                log.error(e.getLocalizedMessage(), e);
                return null;
            }
        }
        return configFile;
    }

    public synchronized void reset() {
        resetting.set(true);
        synchronized (ROOT) {
            ROOT.removeAll();
            ROOT.setAll(DEFAULTS);
        }
        resetting.set(false);
    }



    public JsonNode get(String key) {
        return getOrDefault(DEFAULTS.get(key), key);
    }

    /**
     * @param keys array of strings to use as keys
     * @return found node or default as node
     */
    public synchronized JsonNode getOrDefault(Object defaultValue, String... keys) {
        if (useBackingFile.get() && !loaded.get()) {
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
                if (key == null) {
                    continue;
                }
                JsonNode value;
                try {
                    if (!key.startsWith("/")) {
                        key = "/" + key;
                    }
                    JsonPointer pointer = JsonPointer.compile(key);
                    if (!KEYS.contains(pointer)) {
                        continue;
                    }
                    value = ROOT.at(pointer);
                    if (value != null && !value.isMissingNode()) {
                        return value;
                    }
                } catch (NullPointerException _) {
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
                switch (value) {
                    case Boolean v -> objectNode.set(key, JsonNodeFactory.instance.booleanNode(v));
                    case byte[] v -> objectNode.set(key, JsonNodeFactory.instance.binaryNode(v));
                    case Double v -> objectNode.set(key, JsonNodeFactory.instance.numberNode(v));
                    case Float v -> objectNode.set(key, JsonNodeFactory.instance.numberNode(v));
                    case Integer v -> objectNode.set(key, JsonNodeFactory.instance.numberNode(v));
                    case Long v -> objectNode.set(key, JsonNodeFactory.instance.numberNode(v));
                    case JsonNode v -> objectNode.set(key, v);
                    case String v -> objectNode.set(key, JsonNodeFactory.instance.textNode(v));
                    default -> objectNode.set(key, JsonNodeFactory.instance.textNode(value.toString()));
                }
                log.debug("Config set: {} -> {}", key, OBJECT_MAPPER.valueToTree(value));
            }
        }
        save();
    }
    public static ObjectNode getDefaults(){
        return DEFAULTS;
    }
}