package net.rptools.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.rptools.data.config.Config;
import net.rptools.data.config.Pref;
import net.rptools.util.Alerts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.rptools.data.Constants.OBJECT_MAPPER;

public class LibraryJSON {
    private static final Logger log = LoggerFactory.getLogger(LibraryJSON.class);
    private static final String PROPERTY_NAME = "statSheets";
    private static final ObjectNode libObject = OBJECT_MAPPER.createObjectNode();
    private static final ArrayNode libArray = OBJECT_MAPPER.createArrayNode();
    private static final Map<String, ObjectNode> LOOKUP = new HashMap<>();
    private static Path jsonFilePath;


    static void createLibFile() {
        if (!Pref.getBoolean(Config.USE_ADD_ON_JSON_FILE)) {
            return;
        }
        if (Alerts.prompt(null, "Create associated Add-On JSON file in root folder?")) {
            try {
                Files.createFile(jsonFilePath);
                libObject.set(PROPERTY_NAME, libArray);
                writeLibFile();
            } catch (Exception e) {
                Alerts.whoops(e);
                log.error(e.getLocalizedMessage(), e);
                Pref.set(Config.USE_ADD_ON_JSON_FILE, false);
            }
        } else {
            Pref.set(Config.USE_ADD_ON_JSON_FILE, false);
        }
    }

    static void writeLibFile() {
        if (!Pref.getBoolean(Config.USE_ADD_ON_JSON_FILE)) {
            return;
        }
        try {
            OBJECT_MAPPER.writeValue(new FileOutputStream(jsonFilePath.toFile()), libObject);
            Pref.set(Config.ADD_ON_FOLDER, jsonFilePath);
        } catch (Exception e) {
            Alerts.whoops(e);
            log.error(e.getLocalizedMessage(), e);
            Pref.set(Config.USE_ADD_ON_JSON_FILE, false);
        }
    }

    public static void setJsonFilePath(Path childFolder) {
        Path parent = childFolder.getParent();
        while (parent != null && parent.getFileName() != null && !parent.getFileName().toString().equalsIgnoreCase("library")) {
            parent = parent.getParent();
        }
        if (parent != null && parent.getParent() != null){
            parent = parent.getParent();
            try{
                jsonFilePath = parent.resolve("stat_sheets.json");
                if(jsonFilePath.toFile().exists()){
                    try{
                        JsonNode node = OBJECT_MAPPER.readTree(new FileInputStream(jsonFilePath.toFile()));
                        if(node instanceof ObjectNode objectNode){
                            JsonNode values = objectNode.get(PROPERTY_NAME);
                            if(values instanceof ArrayNode arrayNode){
                                setSheets(arrayNode);
                            }
                        }
                    } catch (Exception e){
                        Alerts.whoops(e);
                    }
                } else {
                    createLibFile();
                }
            } catch (Exception e){
                Alerts.whoops(e);
                log.error(e.getLocalizedMessage(), e);
            }
        }
    }
    public static ArrayNode getSheets(){
        return libArray;
    }
    public static void addSheets(ArrayNode arrayNode){
        final AtomicBoolean exists = new AtomicBoolean();
        arrayNode.forEach(jsonNode -> {
            final ObjectNode objectNode = (ObjectNode) jsonNode;
            exists.set(false);
            libArray.forEach(existing -> {
                if(existing.equals(jsonNode) || existing.get("entry").asText().equalsIgnoreCase(objectNode.get("entry").asText().toLowerCase())){
                    objectNode.set("name", existing.get("name"));
                    objectNode.set("description", existing.get("description"));
                    objectNode.set("propertyTypes", existing.get("propertyTypes"));
                    exists.set(true);
                }
            });
            if(!exists.get()){
                libArray.add(objectNode);
            }
        });
    }
    public static void setSheets(ArrayNode arrayNode){
        while (!libArray.isEmpty()) {
            libArray.remove(0);
        }
        libArray.addAll(arrayNode);
        arrayNode.forEach(jsonNode -> {
            if(jsonNode instanceof ObjectNode objectNode){
                List<String> values = new ArrayList<>();
                objectNode.properties().forEach(nodeEntry -> values.add(nodeEntry.getValue().asText()));
                values.forEach(value -> LOOKUP.putIfAbsent(value, objectNode));
            }

        });
        writeLibFile();
    }
}
