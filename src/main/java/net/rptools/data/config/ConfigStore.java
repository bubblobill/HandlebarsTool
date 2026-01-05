package net.rptools.data.config;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.rptools.data.Constants;
import net.rptools.util.Utils;
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
            defaultsNode.set(Config.THEME_CSS, OBJECT_MAPPER.readTree(IOUtils.resourceToString(THEME_CSS_PATH, StandardCharsets.UTF_8)));
            defaultsNode.set(DATASETS, OBJECT_MAPPER.readTree(IOUtils.resourceToString(DEFAULT_DATASETS_PATH, StandardCharsets.UTF_8)));
            defaultsNode.put(TEMPLATE_FOLDER, Constants.USER_DIR.toString());
            ArrayNode datasetNames = OBJECT_MAPPER.createArrayNode();
            defaultsNode.set(DATASET_NAMES, datasetNames);
            defaultsNode.get(Config.DATASETS).fields().forEachRemaining(entry -> datasetNames.add(entry.getKey()));
            defaultsNode.set(DATASET_NAME, datasetNames.get(0));
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
        ROOT = OBJECT_MAPPER.valueToTree(rootNode); rootNode.deepCopy();
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
        if(ROOT.get(DATASET_NAME).asText().isBlank()){
            set(DATASET_NAME, getOrDefault(ROOT.get(DATASET_NAMES).get(0), ROOT.get(DATASET_DEFAULT).asText()));
        }
        ROOT.fields().forEachRemaining(nodeEntry -> addKey(nodeEntry.getKey()));
    }

   /* private void setDefaults() {
        DEFAULTS.set(Config.RESET, JsonNodeFactory.instance.booleanNode(false));
        DEFAULTS.set(Config.BACKGROUND, JsonNodeFactory.instance.textNode("Textures/Grass.png"));
        DEFAULTS.set(Config.DATASET_NAME, JsonNodeFactory.instance.textNode("Basic"));
        DEFAULTS.set(Config.DATASET_DEFAULT, JsonNodeFactory.instance.textNode("Basic"));
        DEFAULTS.set(Config.HANDLEBARS_PORT, JsonNodeFactory.instance.numberNode(6781));
        DEFAULTS.set(Config.SERVER_PORT, JsonNodeFactory.instance.numberNode(7891));
        DEFAULTS.set(Config.LOCATION, JsonNodeFactory.instance.textNode(Constants.StatSheetLocation.BOTTOM_LEFT.className()));
        DEFAULTS.set(Config.SHEET, JsonNodeFactory.instance.textNode("Simple"));
        DEFAULTS.set(Config.THEME, JsonNodeFactory.instance.textNode("Aah"));
        DEFAULTS.set(Config.TEMPLATE_FOLDER, JsonNodeFactory.instance.textNode(System.getProperty("user.home")));
        DEFAULTS.set(Config.LIB_FILE, JsonNodeFactory.instance.booleanNode(false));
        DEFAULTS.set(Config.VIEW_AS, JsonNodeFactory.instance.textNode("player"));
        DEFAULTS.set(Config.WATCH_FOLDER, JsonNodeFactory.instance.booleanNode(true));

        try (InputStream is = ConfigStore.class.getResourceAsStream("/data/tokenPropertyTypes.json")) {
            DEFAULTS.set(Config.DATASETS, OBJECT_MAPPER.readTree(is));
            ArrayNode arrayNode = OBJECT_MAPPER.createArrayNode();
            DEFAULTS.get(Config.DATASETS).fieldNames().forEachRemaining(arrayNode::add);
            DEFAULTS.set(Config.DATASET_NAMES, arrayNode);
        } catch (IOException e) {
            Utils.whoops(e);
            log.error(e.getLocalizedMessage(), e);
            DEFAULTS.set(Config.DATASETS, JsonNodeFactory.instance.objectNode());
        }

        try (InputStream is = ConfigStore.class.getResourceAsStream("/data/themeCss.json")) {
            DEFAULTS.set(Config.THEME_CSS, OBJECT_MAPPER.readTree(is));
        } catch (IOException e) {
            Utils.whoops(e);
            log.error(e.getLocalizedMessage(), e);
            DEFAULTS.set(Config.THEME_CSS, JsonNodeFactory.instance.objectNode());
        }
    }
*/
    private void addKey(String key) {
        key = key.startsWith("/") ? key : "/" + key;
        JsonPointer pointer = JsonPointer.compile(key);
        if (!KEYS.stream().map(JsonPointer::toString).toList().contains(key)) {
            KEYS.add(pointer);
        }
        JsonNode node = ROOT.at(pointer);
        if (node instanceof ObjectNode objectNode) {
            objectNode.fields().forEachRemaining(entry -> addKey(pointer.appendProperty(entry.getKey()).toString()));
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
            ROOT.remove(THEME_CSS);
            synchronized (file) {
                try {
                    if (Files.exists(file)) {
                        OBJECT_MAPPER.writeValue(new FileOutputStream(file.toFile()), ROOT);
                    }
                } catch (IOException e) {
                    Utils.whoops(e);
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
                    log.info("Pref file created.");
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
                log.info("Config set: {} -> {}", key, OBJECT_MAPPER.valueToTree(value));
            }
        }
        save();
    }
}