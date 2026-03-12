package net.rptools.util;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;

import static net.rptools.data.Constants.OBJECT_MAPPER;

public class Flatterer {
    private static final Logger log = LoggerFactory.getLogger(Flatterer.class);
    private final JPath jPath;
    private final List<String> collectedAssets = new ArrayList<>();

    public Flatterer(JsonNode jsonNode) {
        jPath = JPath.of(jsonNode);
        squash();
    }

    public JPath getjPath() {
        return jPath;
    }

    public List<String> getCollectedAssets() {
        return collectedAssets;
    }

    protected static JsonNode fixStrokeNode(JsonNode jsonNode) {
        if (jsonNode instanceof ObjectNode objectNode) {
            objectNode.set("join", OBJECT_MAPPER.getNodeFactory().textNode("miter"));
            objectNode.set("cap", OBJECT_MAPPER.getNodeFactory().textNode("butt"));
            objectNode.set("dash-phase", OBJECT_MAPPER.getNodeFactory().numberNode(0));
            objectNode.remove("dash__phase");
            return objectNode;
        }
        return jsonNode;
    }

    protected static JsonNode cleanAssetIdNode(JsonNode jsonNode) {
        if (jsonNode instanceof ArrayNode arrayNode) {
            ArrayNode replacement = OBJECT_MAPPER.createArrayNode();
            for (int i = 0; i < arrayNode.size(); i++) {
                replacement.add(cleanAssetIdNode(arrayNode.get(i)));
            }
            return replacement;
        } else if (jsonNode instanceof ObjectNode objectNode
                && objectNode.has("net.rptools.lib.MD5Key")) {
            return cleanAssetIdNode(objectNode.get("net.rptools.lib.MD5Key"));
        } else if (jsonNode instanceof ObjectNode objectNode && objectNode.has("id")) {
            return OBJECT_MAPPER.getNodeFactory().textNode(objectNode.get("id").asText());
        } else if (jsonNode instanceof ValueNode valueNode && valueNode.isTextual()) {
            return valueNode;
        } else {
            return jsonNode;
        }
    }

    protected static JsonNode nodeToColourString(JsonNode jsonNode) {
        int r = 0, g = 0, b = 0;
        double a = 1;
        if (jsonNode instanceof ValueNode valueNode && valueNode.isNumber()) {
            float[] components = new float[4];
            new Color(valueNode.asInt()).getColorComponents(components);
            r = Math.round(components[0] * 255);
            g = Math.round(components[1] * 255);
            b = Math.round(components[2] * 255);
            a = components[3];
        } else if (jsonNode instanceof ObjectNode objectNode && objectNode.has("red")) {
            r = !objectNode.has("red") ? 0 : objectNode.get("red").asInt();
            g = !objectNode.has("green") ? 0 : objectNode.get("green").asInt();
            b = !objectNode.has("blue") ? 0 : objectNode.get("blue").asInt();
            a = !objectNode.has("alpha") ? 1 : objectNode.get("alpha").asDouble();
        }
        return OBJECT_MAPPER.getNodeFactory().textNode(String.format("rgba(%d,%d,%d,%3f)", r, g, b, a));
    }

    private void test() {
        JsonNode node = jPath.getNode("$['campaign']['campaignProperties']['tokenStates']['entry'][6]['net.rptools.maptool.client.ui.token.OTokenOverlay']['name']");
        System.out.println(jPath.resolve(node, "../../../[4]"));
    }

    protected synchronized void resolveReferences(List<String> pathsToCheck) {
        List<String> referencePaths = pathsToCheck.stream()
                .filter(s -> s.contains("reference")).toList();
        List<JsonNode> references = referencePaths.stream().map(jPath::getNode).distinct().toList();

        while (!references.isEmpty()) {
            JsonNode refNode = references.removeFirst();
            String relativePath = refNode.asText();
            JsonNode parent = jPath.getParent(refNode);
            JsonNode actualNode = jPath.resolve(refNode, "../" + relativePath);
            jPath.replace(parent, actualNode);
        }
    }

    protected void collectAssets(String jsonPath) {
        JsonNode assetNode = jPath.getNode(jsonPath);
        if (assetNode instanceof ValueNode valueNode) {
            collectedAssets.add(valueNode.asText());
        } else if (assetNode instanceof ArrayNode arrayNode) {
            arrayNode.forEach(node -> collectedAssets.add(node.asText()));
        }
    }

    protected void squash() {
        Predicate<String> predicate = string -> string.endsWith("TokenOverlay']");
        List<String> typeNodesPaths = jPath.readPaths("$..*", predicate);

        List<String> allAssetNodesPaths = new ArrayList<>();
        allAssetNodesPaths.addAll(jPath.getPaths("$..['assetId']"));
        allAssetNodesPaths.addAll(jPath.getPaths("$..['assetIds']"));
        allAssetNodesPaths.addAll(jPath.getPaths("$..['bottomAssetId']"));
        allAssetNodesPaths.addAll(jPath.getPaths("$..['topAssetId']"));

        List<String> allColourPaths = new ArrayList<>();
        allColourPaths.addAll(jPath.getPaths("$..['color']"));
        allColourPaths.addAll(jPath.getPaths("$..['barColor']"));
        allColourPaths.addAll(jPath.getPaths("$..['bgColor']"));

        List<String> allStrokeNodesPaths = new ArrayList<>(jPath.getPaths("$..[?(@.keys() anyof ['stroke'])]['stroke']"));

        List<String> assetNodesPaths = new ArrayList<>(
                allAssetNodesPaths.stream().filter(string ->
                        !typeNodesPaths.stream().filter(string::startsWith).toList().isEmpty()
                ).toList()
        );

        List<String> colourNodesPaths = new ArrayList<>(
                allColourPaths.stream().filter(string ->
                        !typeNodesPaths.stream().filter(string::startsWith).toList().isEmpty()
                ).toList()
        );
        List<String> strokeNodesPaths = new ArrayList<>(
                allStrokeNodesPaths.stream().filter(string ->
                        !typeNodesPaths.stream().filter(string::startsWith).toList().isEmpty()
                ).toList()
        );

        resolveReferences(assetNodesPaths);
        for (String path : assetNodesPaths) {
            JsonNode assetNode = cleanAssetIdNode(jPath.getNode(path));
            jPath.set(path, assetNode);
            collectAssets(path);
        }
        resolveReferences(colourNodesPaths);
        for (String path : colourNodesPaths) {
            JsonNode colourTextNode = nodeToColourString(jPath.getNode(path));
            jPath.set(path, colourTextNode);
        }
        for (String path : strokeNodesPaths) {
            JsonNode strokeNode = fixStrokeNode(jPath.getNode(path));
            jPath.set(path, strokeNode);
        }
        resolveReferences(typeNodesPaths);
        for (String path : typeNodesPaths) {
            String newPath = path.replace("net.rptools.maptool.client.ui.token.", "").replace("TokenOverlay", "");
            int idx = path.lastIndexOf('[');
            jPath.renameKey(path.substring(0, idx), newPath.substring(idx), path.substring(idx));
        }
    }
}
