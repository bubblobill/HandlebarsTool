package net.rptools.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.rptools.data.config.Config;
import net.rptools.data.config.Pref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.StreamSupport;

import static net.rptools.data.Constants.OBJECT_MAPPER;
import static net.rptools.data.config.Config.*;

public class TemplateData {
    private static final Logger log = LoggerFactory.getLogger(TemplateData.class);
    public static final ObjectNode TEMPLATE_DATA = Constants.OBJECT_MAPPER.createObjectNode();
    private static final ObjectNode DATA_SETS = OBJECT_MAPPER.createObjectNode();
    private static String datasetName = "";
    private static String viewAs = "";
    private static final List<String> IGNORE = List.of(
            ADD_ON_FOLDER,
            DATASETS,
            HANDLEBARS_PORT,
            LIB_FILE,
            RESET,
            SERVER_PORT,
            THEME_CSS,
            WATCH_FOLDER
    );
    private static final List<String> KEEP = FIELD_NAMES.stream().filter(s -> !IGNORE.contains(s)).toList();

    public static boolean initialiseTemplateData() {
        DATA_SETS.removeAll();
        DATA_SETS.setAll((ObjectNode) OBJECT_MAPPER.valueToTree(Pref.getObjectNode(Config.DATASETS)));

        try {
            TEMPLATE_DATA.removeAll();
            TEMPLATE_DATA.put(THEME, "Aah");
            TEMPLATE_DATA.setAll(SheetsObject.getJson());
            for (String key : KEEP) {
                TEMPLATE_DATA.set(key, Pref.get(key));
            }
            TEMPLATE_DATA.setAll((ObjectNode) OBJECT_MAPPER.valueToTree(DATA_SETS.get(Pref.getString(DATASET_NAME))));
            return true;
        } catch (Exception e) {
            log.error(e.getLocalizedMessage(), e);
            return false;
        }
    }

    public static void filterProperties() {
        final boolean isNpc = TEMPLATE_DATA.get("tokenType").asText().equalsIgnoreCase("npc");
        final String datasetName_ = TEMPLATE_DATA.get(DATASET_NAME).asText();
        final String viewAs_ = TEMPLATE_DATA.get(VIEW_AS).asText();
        if (datasetName.equalsIgnoreCase(datasetName_) && viewAs.equalsIgnoreCase(viewAs_)) {
            return;
        }
        datasetName = datasetName_;
        viewAs = viewAs_;
        final ArrayNode properties = OBJECT_MAPPER.valueToTree(DATA_SETS.get(datasetName_).get("properties"));
        List<JsonNode> availableProperties = StreamSupport.stream(properties.spliterator(), false)
                .filter(prop ->
                        (viewAs_.equalsIgnoreCase("player") &&
                                !prop.get("gmOnly").asBoolean() && !prop.get("ownerOnly").asBoolean())
                                || (viewAs_.equalsIgnoreCase("owner") && !prop.get("gmOnly").asBoolean())
                                || viewAs_.equalsIgnoreCase("gm")
                )
                .toList();

        final ArrayNode out = OBJECT_MAPPER.createArrayNode();
        availableProperties.forEach(out::add);
        TEMPLATE_DATA.set("properties", out);
    }
}