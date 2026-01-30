package net.rptools.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.jayway.jsonpath.*;
import com.jayway.jsonpath.spi.cache.Cache;
import com.jayway.jsonpath.spi.cache.CacheProvider;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import jakarta.annotation.Nonnull;
import org.cache2k.config.Cache2kConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static net.rptools.data.Constants.OBJECT_MAPPER;

public class JPath {
    private static final Logger log = LoggerFactory.getLogger(JPath.class);
    private static final Configuration PATH_CONFIG;
    private static final Configuration NODE_CONFIG;
    private static final EvaluationListener EVALUATION_LISTENER = new EvaluationListener() {
        @Override
        public EvaluationContinuation resultFound(FoundResult found) {
            try {
                if(log.isDebugEnabled()) {
                    log.debug("Index: {}, Path: {}, Json: {}", found.index(), found.path(), OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(found.result()));
                }
            } catch (JsonProcessingException e) {
                log.error("{}\n{}\nLocation: {}\nProcessor: {}",
                        e.getOriginalMessage(),
                        e.getMessage(),
                        e.getLocation(),
                        e.getProcessor()
                );
            }
            return EvaluationContinuation.CONTINUE;
        }
    };
    private final AtomicBoolean updating = new AtomicBoolean();

    static {
        PATH_CONFIG = Configuration.builder()
                .jsonProvider(new JacksonJsonNodeJsonProvider(OBJECT_MAPPER))
                .mappingProvider(new JacksonMappingProvider(OBJECT_MAPPER))
                .options(Option.AS_PATH_LIST)
                .options(Option.SUPPRESS_EXCEPTIONS)
                .evaluationListener(EVALUATION_LISTENER)
                .build();
        NODE_CONFIG = Configuration.builder()
                .jsonProvider(new JacksonJsonNodeJsonProvider(OBJECT_MAPPER))
                .mappingProvider(new JacksonMappingProvider(OBJECT_MAPPER))
                .options(Option.SUPPRESS_EXCEPTIONS)
                .evaluationListener(EVALUATION_LISTENER)
                .build();
    }

    private final JsonNode jsonRoot;
    private DocumentContext nodeContext;
    private DocumentContext pathContext;
    private final RAMCache ramCache = new RAMCache();

    private JPath(JsonNode rootNode) {
        jsonRoot = rootNode;
        CacheProvider.setCache(ramCache);
        nodeContext = JsonPath.using(NODE_CONFIG).parse(jsonRoot);
        pathContext = JsonPath.using(PATH_CONFIG).parse(jsonRoot);
    }

    public static JPath of(JsonNode jsonNode) {
        return new JPath(jsonNode);
    }

    public JsonNode getRootNode() {
        return nodeContext.json();
    }

    public String nodePath(JsonNode node) {
        return ramCache.getNodePath(node);
    }

    public String resolveRelativePath(String startPath, String relativePath) {
        boolean rootReached = false;
        try {
            while (relativePath.startsWith("../")) {
                int idx = startPath.lastIndexOf('[');
                if (idx == -1 && rootReached) {
                    throw new IllegalArgumentException("Path not traversable.");
                }

                if (idx == -1) {
                    idx = 1;
                    rootReached = true;
                }
                startPath = startPath.substring(0, idx);
                relativePath = relativePath.substring(3);
            }
        } catch (IllegalArgumentException e) {
            System.out.println(e.getLocalizedMessage());
            return startPath;
        }
        if (!relativePath.isBlank()) {
            relativePath = String.format("['%s']", relativePath.replaceAll("/", "']['"));
        }
        return startPath + relativePath;
    }

    public JsonNode resolve(JsonNode jsonNode, String relativePath) {
        return getNode(
                resolveRelativePath(nodePath(jsonNode), relativePath)
        );
    }

    public boolean hasParent(JsonNode jsonNode){
        String path = ramCache.getNodePath(jsonNode);
        return path != null && path.length() > 1;
    }

    public JsonNode getParent(JsonNode jsonNode) {
        return resolve(jsonNode, "../");
    }

    public String getKey(JsonNode jsonNode) {
        String path = nodePath(jsonNode);
        if(path == null) {
            path = "";
        } else {
            path = path.substring(path.lastIndexOf('[') + 2, path.length() - 2);
        }
        return path;
    }

    public ValueNode getValue(String jsonPath) {
        return _getValue(jsonPath, ValueNode.class);
    }

    public ObjectNode getObject(String jsonPath) {
        return _getValue(jsonPath, ObjectNode.class);
    }

    public ArrayNode getArray(String jsonPath) {
        return _getValue(jsonPath, ArrayNode.class);
    }

    public JsonNode getNode(String jsonPath) {
        return _getValue(jsonPath, JsonNode.class);
    }

    public List<ValueNode> getValues(String jsonPath) {
        return _getValues(jsonPath, ValueNode.class);
    }

    public List<ObjectNode> getObjects(String jsonPath) {
        return _getValues(jsonPath, ObjectNode.class);
    }

    public List<ArrayNode> getArrays(String jsonPath) {
        return _getValues(jsonPath, ArrayNode.class);
    }

    public List<JsonNode> getNodes(String jsonPath) {
        return _getValues(jsonPath, JsonNode.class);
    }
    public List<String> getPaths(String jsonPath){
        return pathContext.read(jsonPath, new TypeRef<List<String>>() {});
    }
    public ValueNode getValue(String jsonPath, Object defaultValue) {
        return _getValue(jsonPath, ValueNode.class, defaultToNode(defaultValue));
    }

    public ObjectNode getObject(String jsonPath, Object defaultValue) {
        return _getValue(jsonPath, ObjectNode.class, defaultToNode(defaultValue));
    }

    public ArrayNode getArray(String jsonPath, Object defaultValue) {
        return _getValue(jsonPath, ArrayNode.class, defaultToNode(defaultValue));
    }

    public JsonNode getNode(String jsonPath, Object defaultValue) {
        return _getValue(jsonPath, JsonNode.class, defaultToNode(defaultValue));
    }

    private JsonNode defaultToNode(Object defaultValue) {
        return switch (defaultValue) {
            case ArrayNode arrayNode -> arrayNode;
            case ObjectNode objectNode -> objectNode;
            case Boolean val -> OBJECT_MAPPER.getNodeFactory().booleanNode(val);
            case byte[] val -> OBJECT_MAPPER.getNodeFactory().binaryNode(val);
            case Number num -> switch (num) {
                case Byte numVal -> OBJECT_MAPPER.getNodeFactory().numberNode(numVal);
                case Double numVal -> OBJECT_MAPPER.getNodeFactory().numberNode(numVal);
                case Float numVal -> OBJECT_MAPPER.getNodeFactory().numberNode(numVal);
                case Integer numVal -> OBJECT_MAPPER.getNodeFactory().numberNode(numVal);
                case Long numVal -> OBJECT_MAPPER.getNodeFactory().numberNode(numVal);
                case Short numVal -> OBJECT_MAPPER.getNodeFactory().numberNode(numVal);
                default -> OBJECT_MAPPER.getNodeFactory().numberNode(new BigDecimal(num.toString()));
            };
            case null -> OBJECT_MAPPER.getNodeFactory().nullNode();
            case String val -> OBJECT_MAPPER.getNodeFactory().textNode(val);
            default -> OBJECT_MAPPER.getNodeFactory().pojoNode(defaultValue);
        };
    }

    /**
     * Get a single typed value with fallback default.
     */
    protected <T> T _getValue(String jsonPath, Class<T> type) {
        try {
            return nodeContext.read(jsonPath, type);
        } catch (PathNotFoundException e) {
            return null;
        }
    }

    /**
     * Get a single typed value with fallback default.
     */
    protected <T> T _getValue(String jsonPath, Class<T> type, Object defaultVal) {
        if (defaultVal.getClass().isAssignableFrom(type)) {
            T dv = type.cast(defaultVal);
            T result = _getValue(jsonPath, type);
            return (result != null) ? result : dv;
        } else {
            throw new IllegalArgumentException("Default is different class");
        }
    }

    /**
     * Get a list of typed values by converting matching JsonNodes.
     */
    protected <T> List<T> _getValues(String jsonPath, Class<T> type) {
        List<JsonNode> nodes = _getNodes(jsonPath);
        return nodes.stream()
                .map(node -> OBJECT_MAPPER.convertValue(node, type))
                .toList();
    }

    /**
     * Get list of JsonNode values matching the JSONPath.
     */
    protected List<JsonNode> _getNodes(String jsonPath) {
        try {
            return nodeContext.read(jsonPath, new TypeRef<List<JsonNode>>() {});
        } catch (PathNotFoundException e) {
            return Collections.emptyList();
        }
    }

    public void renameKey(String path, String oldKeyName, String newKeyName) {
        nodeContext = nodeContext.renameKey(path, oldKeyName, newKeyName);
        pathContext = pathContext.renameKey(path, oldKeyName, newKeyName);

//        updateMap();
    }

    public void delete(String path) {
        nodeContext = nodeContext.delete(path);
        pathContext = pathContext.delete(path);
    }

    public void replace(JsonNode original, JsonNode replacement) {
        try {
            String nodePath = nodePath(original);
            set(nodePath, replacement);
        } catch (RuntimeException re) {
            log.info(re.getLocalizedMessage(), re);
        }
    }

    public void add(String path, Object value) {
        nodeContext = nodeContext.add(path, value);
        pathContext = pathContext.add(path, value);
    }
    public void set(String path, JsonNode value) {
        try {
            nodeContext = nodeContext.set(path, value);
            pathContext = pathContext.set(path, value);
        } catch (Exception e){
            log.error("On JSON path: {},\n{}", path, e.getLocalizedMessage(), e);
        }
    }

    public void set(String path, String key, JsonNode value) {
        try{
            path = String.format("%s[%d]", path, Integer.parseInt(key));
            nodeContext = nodeContext.set(path, value);
            pathContext = pathContext.set(path, value);
        } catch (NumberFormatException _) {
            path = String.format("%s['%s']", path, key);
            nodeContext = nodeContext.set(path, value);
            pathContext = pathContext.set(path, value);
        }
    }

    public List<String> readPaths(String jsonPath, Predicate<String> predicate) {
        return pathContext.read(jsonPath, new TypeRef<List<String>>() {}).stream().filter(predicate).toList();
//           return pathContext.read(jsonPath, String.class, predicate);
    }

    private class RAMCache implements Cache{
         private final org.cache2k.Cache<String, JsonPath> cache = new Cache2kConfig<String,JsonPath>().builder()
                .name("JPathCache")
                .boostConcurrency(true)
                .entryCapacity(4096)
                .build();
        private final org.cache2k.Cache<JsonNode, JsonPath> nodeCache = new Cache2kConfig<JsonNode,JsonPath>().builder()
                .name("JNodeCache")
                .boostConcurrency(true)
                .entryCapacity(4096)
                .build();

        public Map<String, JsonPath> getMap(){
            return cache.asMap();
        }
        public String getNodePath(JsonNode jsonNode){
            return Objects.requireNonNull(nodeCache.get(jsonNode)).getPath();
        }
        @Override
        public JsonPath get (String key){
            return cache.get(key);
        }

        @Override
        public void put (String key, JsonPath jsonPath){
            cache.put(key, jsonPath);
            nodeCache.put(nodeContext.read(jsonPath), jsonPath);
        }
    }
}
