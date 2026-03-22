package net.rptools.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import net.rptools.data.config.Config;
import net.rptools.data.config.Pref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static net.rptools.data.Constants.OBJECT_MAPPER;
import static net.rptools.data.config.Config.*;

public class TemplateData {
    private static final Logger log = LoggerFactory.getLogger(TemplateData.class);
    public static final ObjectNode TEMPLATE_DATA = Constants.OBJECT_MAPPER.createObjectNode();
    private static final ObjectNode DATA_SETS = OBJECT_MAPPER.createObjectNode();
    private static String currentPropertyName = "";
    private static String viewAs = "";
    private static Map<String, String> portraitInfo = new HashMap<>();
    private static final List<String> IGNORE = List.of(
            ADD_ON_FOLDER,
            ASSETS_FOLDER,
            PROPERTY_TYPES,
            USE_ADD_ON_JSON_FILE,
            RESET,
            SERVER_PORT,
            ALL_THEME_CSS,
            WATCH_FOLDER
    );
    private static final List<String> KEEP = FIELD_NAMES.stream().filter(s -> !IGNORE.contains(s)).toList();

    public static boolean initialiseTemplateData() {
        DATA_SETS.removeAll();
        DATA_SETS.setAll((ObjectNode) OBJECT_MAPPER.valueToTree(Pref.getObjectNode(Config.PROPERTY_TYPES)));

        try {
            TEMPLATE_DATA.removeAll();
            TEMPLATE_DATA.put(CURRENT_THEME, "Aah");
            TEMPLATE_DATA.setAll(SheetsObject.getJson());
            for (String key : KEEP) {
                TEMPLATE_DATA.set(key, Pref.get(key));
            }
            TEMPLATE_DATA.setAll((ObjectNode) OBJECT_MAPPER.valueToTree(DATA_SETS.get(Pref.getString(CURRENT_PROPERTY_TYPE))));

            portraitInfo.put("portrait", TEMPLATE_DATA.get("portrait").asText());
            portraitInfo.put("image", TEMPLATE_DATA.get("image").asText());
            portraitInfo.put("portraitHeight", TEMPLATE_DATA.get("portraitHeight").asText());
            portraitInfo.put("portraitWidth", TEMPLATE_DATA.get("portraitWidth").asText());

            JsonNode barsNode = TEMPLATE_DATA.get(BARS);
            if (barsNode instanceof ObjectNode objectNode) {
                objectNode.fieldNames().forEachRemaining(s -> {
                    JsonNode barNode = objectNode.get(s);
                    if (barNode instanceof ObjectNode objectNode_) {
                        objectNode_.put("value", Math.random());
                    }
                });
            }
            TEMPLATE_DATA.set("themes", OBJECT_MAPPER.valueToTree(Pref.getList(ALL_THEME_CSS)));
            filterVisible();
            return true;
        } catch (Exception e) {
            log.error(e.getLocalizedMessage(), e);
            return false;
        }
    }

    public static void filterVisible() {
        final String currentPropertyName_ = TEMPLATE_DATA.get(CURRENT_PROPERTY_TYPE).asText();
        final String viewAs_ = TEMPLATE_DATA.get(VIEW_AS).asText();
        if(TEMPLATE_DATA.get(SHOW_PORTRAIT).asBoolean() || TEMPLATE_DATA.get(SHOW_PORTRAIT).asText().equalsIgnoreCase("on")){
            if(!TEMPLATE_DATA.has("portrait")){
                for(String key: portraitInfo.keySet()){
                    TEMPLATE_DATA.set(key, new TextNode(portraitInfo.get(key)));
                }
            }
        } else {
            if(TEMPLATE_DATA.has("portrait")){
                for(String key: portraitInfo.keySet()){
                    portraitInfo.put(key, TEMPLATE_DATA.get(key).asText());
                    TEMPLATE_DATA.remove(key);
                }
            }
        }

        if (currentPropertyName.equalsIgnoreCase(currentPropertyName_) && viewAs.equalsIgnoreCase(viewAs_)) {
            return;
        }
        currentPropertyName = currentPropertyName_;
        viewAs = viewAs_;
        boolean isGm = viewAs.equalsIgnoreCase("gm");

        boolean isNpc = TEMPLATE_DATA.get("tokenType").asText().equalsIgnoreCase("npc");

        final ObjectNode states = Pref.getObjectNode(STATES);
        TEMPLATE_DATA.set("states", OBJECT_MAPPER.valueToTree(
                states.propertyStream().filter(nodeEntry -> {
                    if (nodeEntry.getValue() instanceof ObjectNode on) {
                        return (isGm && on.get("showGM").asBoolean())
                                || (on.get("showOthers").asBoolean() && viewAs.equalsIgnoreCase("player"))
                                || (on.get("showOwner").asBoolean() && viewAs.equalsIgnoreCase("owner"));
                    }
                    return false;
                }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))));

        final ObjectNode bars = Pref.getObjectNode(BARS);
        TEMPLATE_DATA.set("bars", OBJECT_MAPPER.valueToTree(
                bars.propertyStream().filter(nodeEntry -> {
                    if (nodeEntry.getValue() instanceof ObjectNode on) {
                        return (isGm && on.get("showGM").asBoolean())
                                || (on.get("showOthers").asBoolean() && viewAs.equalsIgnoreCase("player"))
                                || (on.get("showOwner").asBoolean() && viewAs.equalsIgnoreCase("owner"));
                    }
                    return false;
                }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))));

        final ArrayNode properties = OBJECT_MAPPER.valueToTree(DATA_SETS.get(currentPropertyName_).get("properties"));
        List<JsonNode> availableProperties = StreamSupport.stream(properties.spliterator(), false)
                .filter(prop ->
                        (viewAs_.equalsIgnoreCase("player") &&
                                !prop.get("gmOnly").asBoolean() && !prop.get("ownerOnly").asBoolean())
                                || (viewAs_.equalsIgnoreCase("owner") && !prop.get("gmOnly").asBoolean())
                                || isGm
                )
                .toList();

        final ArrayNode out = OBJECT_MAPPER.createArrayNode();
        availableProperties.forEach(out::add);
        TEMPLATE_DATA.set("properties", out);

        if (viewAs.equalsIgnoreCase("gm")) {
            TEMPLATE_DATA.put("gm", "gm");
            TEMPLATE_DATA.remove("player");
        } else if (viewAs.equalsIgnoreCase("player")) {
            TEMPLATE_DATA.put("player", "player");
            TEMPLATE_DATA.remove("gm");
        } else {
            TEMPLATE_DATA.remove("gm");
            TEMPLATE_DATA.remove("player");
        }
    }
}