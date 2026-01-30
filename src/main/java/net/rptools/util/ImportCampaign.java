package net.rptools.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import net.rptools.data.config.Config;
import net.rptools.data.config.ConfigStore;
import net.rptools.data.config.Pref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URISyntaxException;
import java.util.*;
import java.util.List;
import java.util.zip.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.nio.file.Path;

import static net.rptools.data.Constants.*;

public class ImportCampaign {
    private static final Logger log = LoggerFactory.getLogger(ImportCampaign.class);
    private static final JFileChooser fc = new JFileChooser();
    private static final String FILE_NAME = "content.xml";
    private static Component parent = null;
    private static List<String> assetList = new ArrayList<>();
    private static Flatterer flatterer;
    private static JPath jPath;
    private static Map<String, AssetCreation.ImageDetails> assetToImageMap = new HashMap<>();
    private static final Path DEFAULT_IMAGE_PATH;

    static {
        try {
            DEFAULT_IMAGE_PATH = Path.of(Objects.requireNonNull(
                    ImportCampaign.class.getClassLoader().getResource("testPage/states/bang_yellow.png")).toURI()
                    );
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setMultiSelectionEnabled(false);
        fc.setDialogTitle("Select Campaign to Import");
        fc.addChoosableFileFilter(new FileNameExtensionFilter("MapTool Files", "mtprops", "cmpgn"));
        fc.addChoosableFileFilter(new FileNameExtensionFilter("Json Files", "json"));
        fc.setAcceptAllFileFilterUsed(false);
        fc.setSelectedFile(Pref.getPath(Config.LAST_IMPORT_PATH).toFile());
    }

    public static Path chooseCampaignFile() {
        if (fc.showDialog(parent, "Open") == JFileChooser.APPROVE_OPTION) {
            Path selected = fc.getSelectedFile().getAbsoluteFile().toPath();
            Pref.set(Config.LAST_IMPORT_PATH, selected);
            return selected;
        }
        return null;
    }

    public static void importProps(Component parent) {
        ImportCampaign.parent = parent;
        Path path = chooseCampaignFile();
        ObjectNode out = null;
        if (path != null) {
            if (path.endsWith("json")) {
                try {
                    out = (ObjectNode) OBJECT_MAPPER.readTree(path.toFile());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                ObjectNode sourceNode = unzipContentXml(path);
                flatterer = new Flatterer(sourceNode);
                jPath = flatterer.getjPath();
                List<String> assetIds = flatterer.getCollectedAssets();
                if (!assetIds.isEmpty()) {
                    assetToImageMap = new AssetCreation(path, DEFAULT_IMAGE_PATH)
                            .loadResources(assetIds)
                            .getPathMap();
                }
                out = processSourceNode();
                sourceNode.removeAll();
            }
        }

        if (out != null && !out.isEmpty()) {
            JsonNode storedBars = Pref.get(Config.BARS);
            if (storedBars.isNull()) {
                storedBars = OBJECT_MAPPER.createObjectNode();
            }
            ((ObjectNode) storedBars).setAll((ObjectNode) out.get("bars"));

            JsonNode storedStates = Pref.get(Config.STATES);
            if (storedStates.isNull()) {
                storedStates = OBJECT_MAPPER.createObjectNode();
            }
            ((ObjectNode) storedStates).setAll((ObjectNode) out.get("states"));

            ObjectNode defaultDataSet = (ObjectNode) ConfigStore.getDefaults().get(Config.PROPERTY_TYPES).get("Basic");
            ObjectNode tokenTypes = (ObjectNode) out.get("propertyTypes");
            List<String> propertyTypeNames = new ArrayList<>();
            Pref.getArrayNode(Config.PROPERTY_TYPE_NAMES).forEach(nm -> propertyTypeNames.add(nm.asText()));

            tokenTypes.forEachEntry((name, array) -> {
                ObjectNode dataset = OBJECT_MAPPER.valueToTree(defaultDataSet);
                dataset.set("properties", array);
                Pref.set(Config.PROPERTY_TYPES + "/" + name, dataset);
                propertyTypeNames.add(name);
            });
            ArrayNode propTypeNames = OBJECT_MAPPER.createArrayNode();
            propertyTypeNames.stream().distinct().forEach(propTypeNames::add);
            Pref.set(Config.PROPERTY_TYPE_NAMES, propTypeNames);
        }
        log.info("Import successful");
    }

    private static ObjectNode processSourceNode() {
        ObjectNode on = OBJECT_MAPPER.createObjectNode();
        Pref.set(Config.DEFAULT_PROPERTY_TYPE, jPath.getValue("$.campaign.campaignProperties.defaultTokenPropertyType").asText());
        ObjectNode propertyTypes = processPropertyTypesNode(jPath.getObject("$.campaign.campaignProperties.tokenTypeMap")); // sourceNode.at("/campaign/campaignProperties/tokenTypeMap"));
        ObjectNode bars = processOverlaysNode(jPath.getArray("$.campaign.campaignProperties.tokenBars.entry"));// sourceNode.at("/campaign/campaignProperties/tokenBars/entry").deepCopy();
        ObjectNode states = processOverlaysNode(jPath.getArray("$.campaign.campaignProperties.tokenStates.entry")); // sourceNode.at("/campaign/campaignProperties/tokenStates/entry").deepCopy();
        on.set("propertyTypes", OBJECT_MAPPER.valueToTree(propertyTypes));
        on.set("bars", OBJECT_MAPPER.valueToTree(bars));
        on.set("states", OBJECT_MAPPER.valueToTree(states));

        return on;
    }

    // Method to unzip ContentXml files
    private static ObjectNode unzipContentXml(Path path) {
        assetList.clear();
        ObjectNode sourceNode = OBJECT_MAPPER.createObjectNode();
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            Iterator<? extends ZipEntry> iterator = zipFile.entries().asIterator();
            while (iterator.hasNext()) {
                ZipEntry entry = iterator.next();
                if (entry.getName().equalsIgnoreCase(FILE_NAME)) {
                    try (InputStream inputStream = zipFile.getInputStream(entry)) {
                        sourceNode = (ObjectNode) XML_MAPPER.readTree(inputStream);
                    } catch (IOException ex) {
                        log.error(ex.getLocalizedMessage(), ex);
                    }
                }
            }
        } catch (Exception e) {
            log.error(e.getLocalizedMessage(), e);
        }
        return sourceNode;
    }

//                    if (!assetList.isEmpty()) {
//                        Map<String, AssetCreation.ImageDetails> pathMap = new AssetCreation(path).loadResources(assetList).getPathMap();
//                        for (Map.Entry<String, AssetCreation.ImageDetails> imageDetailsEntry : pathMap.entrySet()) {
//                            for (Iterator<String> it = barsObject.fieldNames(); it.hasNext(); ) {
//                                String fieldName = it.next();
//                                ObjectNode barEntry = (ObjectNode) barsObject.get(fieldName);
//                                for (Iterator<String> iterator_ = barEntry.fieldNames(); iterator_.hasNext(); ) {
//                                    String field = iterator_.next();
//                                    if (field.equalsIgnoreCase("assetIds")
//                                            && barEntry.get(field) instanceof ArrayNode assetIds) {
//                                        ArrayNode arAn = (ArrayNode) barEntry.get(field + "AspectRatio");
//                                        for (int i = 0; i < assetIds.size(); i++) {
//                                            if (assetIds.get(i).asText().equalsIgnoreCase(imageDetailsEntry.getValue().key())) {
//                                                assetIds.set(i, imageDetailsEntry.getValue().location());
//                                                arAn.set(i, imageDetailsEntry.getValue().aspectRatio());
//                                            }
//                                        }
//                                    } else if (field.toLowerCase().endsWith("assetid")) {
//                                        barEntry.put(field + "AspectRatio", imageDetailsEntry.getValue().aspectRatio());
//                                        barEntry.put(field, imageDetailsEntry.getValue().location());
//                                    }
//                                }
//                            }
//

    /// /                            barsObject = (ObjectNode) findReplaceTextNode(stringPath.getKey(), stringPath.getValue(), barsObject);
    /// /                            statesObject = (ObjectNode) findReplaceTextNode(stringPath.getKey(), stringPath.getValue(), statesObject);
//                        }
//
//                    }
    private static JsonNode findReplaceTextNode(String find, String replace, JsonNode node) {
        if (node.isTextual() && node.asText().contains(find)) {
            return OBJECT_MAPPER.getNodeFactory().textNode(node.asText().replaceAll(find, replace));
        } else if (node instanceof ArrayNode arrayNode) {
            for (int i = 0; i < arrayNode.size(); i++) {
                arrayNode.set(i, findReplaceTextNode(find, replace, arrayNode.get(i)));
            }
            return arrayNode;
        } else if (node instanceof ObjectNode objectNode) {
            objectNode.fieldNames().forEachRemaining(s ->
                    objectNode.set(s, findReplaceTextNode(find, replace, objectNode.get(s))));
            return objectNode;
        }
        return node;
    }


    private static ObjectNode processOverlaysNode(ArrayNode arrayNode) {
        ObjectNode out = OBJECT_MAPPER.createObjectNode();
        arrayNode.forEach(jsonNode -> {
            if (jsonNode instanceof ObjectNode on) {
                Map.Entry<?, ?>[] entries = on.properties().toArray(Map.Entry<?, ?>[]::new);
                String name = "", type = "";
                if (entries[0].getValue() instanceof ValueNode value) {
                    name = value.asText();
                }
                if (entries[1].getKey() instanceof String key) {
                    type = key
                            .replace("net.rptools.maptool.client.ui.token.", "")
                            .replace("TokenOverlay", "");
                }
                if (entries[1].getValue() instanceof ObjectNode objectNode) {
                    objectNode.put("type", type);
                    if (objectNode.has("assetIds")) {
                        updateAssetDetails(objectNode, "assetIds");
                    } else if (objectNode.has("assetId")) {
                        updateAssetDetails(objectNode, "assetId");
                    } else if (objectNode.has("bottomAssetId")) {
                        updateAssetDetails(objectNode, "bottomAssetId");
                    } else if (objectNode.has("topAssetId")) {
                        updateAssetDetails(objectNode, "topAssetId");
                    }
                    out.set(name, objectNode);
                }
            }
        });
        return out;
    }

    private static void updateAssetDetails(ObjectNode on, String key) {
        if (key.equalsIgnoreCase("assetIds")) {
            ArrayNode arAn = OBJECT_MAPPER.createArrayNode();
            ArrayNode idAn = (ArrayNode) on.get(key);
            for (int i = 0; i < idAn.size(); i++) {
                ValueNode vn = (ValueNode) idAn.get(i);
                if (assetToImageMap.containsKey(vn.asText())) {
                    idAn.set(i, assetToImageMap.get(vn.asText()).location());
                    arAn.add(assetToImageMap.get(vn.asText()).aspectRatio());
                } else {
                    idAn.set(i, assetToImageMap.get(DEFAULT_IMAGE_NAME).location());
                    arAn.add(1);
                }
            }
            on.set(key + "AspectRatios", arAn);
        } else {
            ValueNode vn = (ValueNode) on.get(key);
            if (assetToImageMap.containsKey(vn.asText())) {
                on.put(key, assetToImageMap.get(vn.asText()).location());
                on.put(key + "AspectRatio", assetToImageMap.get(vn.asText()).aspectRatio());
            } else {
                on.put(key, assetToImageMap.get(DEFAULT_IMAGE_NAME).location());
                on.put(key + "AspectRatio", 1);
            }
        }
    }
private static final List<String> propFeatures = List.of("gmOnly","ownerOnly","name","displayName","shortName","value");
    private static ObjectNode processPropertyTypesNode(ObjectNode objectNode) {
        ObjectNode out = OBJECT_MAPPER.createObjectNode();
        for (Map.Entry<String, JsonNode> entry : objectNode.properties()) {
            if (entry.getValue() instanceof ObjectNode on && on.get("list") instanceof ObjectNode list) {
                if(list.has("net.rptools.maptool.model.TokenProperty")
                        && list.get("net.rptools.maptool.model.TokenProperty") instanceof ArrayNode properties){
                    for (int i = 0; i < properties.size(); i++) {
                        ObjectNode prop = (ObjectNode) properties.get(i);
                        for(String propName: propFeatures){
                            if(!prop.has(propName)){
                                if(propName.equalsIgnoreCase("gmOnly") ||
                                propName.equalsIgnoreCase("ownerOnly")){
                                    prop.set(propName, OBJECT_MAPPER.getNodeFactory().booleanNode(false));
                                } else if(propName.equalsIgnoreCase("value")){
                                    prop.put(propName, String.valueOf(Math.round(Math.random() * 100)));
                                } else {
                                    prop.put(propName, "");
                                }
                            } else {
                                if(propName.equalsIgnoreCase("gmOnly") ||
                                        propName.equalsIgnoreCase("ownerOnly")){
                                    prop.set(propName, OBJECT_MAPPER.getNodeFactory().booleanNode(Boolean.parseBoolean(prop.get(propName).asText())));
                                }
                            }
                        }
                        prop.remove("highPriority");
                    }
                }
                out.set(on.get("string").asText(), list.get("net.rptools.maptool.model.TokenProperty"));
            }
        }
        return out;
    }

    private static ObjectNode processPropertyNode(ObjectNode jsonNode) {
        ObjectNode on = OBJECT_MAPPER.createObjectNode();
//            PROPERTY_NAMES.forEach(propName -> {
//                Elements els = jsonNode.getElementsByTag(propName);
//                if (els.size() == 1) {
//                    String value = els.getFirst().nodeValue();
//                    if (propName.equalsIgnoreCase("gmOnly") || propName.equalsIgnoreCase("ownerOnly")) {
//                        on.set(propName, OBJECT_MAPPER.getNodeFactory().booleanNode(Boolean.parseBoolean(value)));
//                    } else {
//                        on.set(propName, OBJECT_MAPPER.getNodeFactory().textNode(value));
//                    }
//                } else {
//                    if (propName.equalsIgnoreCase("gmOnly") || propName.equalsIgnoreCase("ownerOnly")) {
//                        on.set(propName, OBJECT_MAPPER.getNodeFactory().booleanNode(false));
//                    } else {
//                        on.set(propName, OBJECT_MAPPER.getNodeFactory().textNode(""));
//                    }
//                }
//            });
        return on;
    }
}