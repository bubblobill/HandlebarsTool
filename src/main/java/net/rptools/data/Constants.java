package net.rptools.data;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Constants {
    public static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .enable(JsonParser.Feature.ALLOW_COMMENTS)
            .enable(StreamWriteFeature.STRICT_DUPLICATE_DETECTION)
            .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .enable(JsonReadFeature.ALLOW_MISSING_VALUES)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    public static final ObjectNode TEMPLATE_DATA = OBJECT_MAPPER.createObjectNode();

    public enum NoteType {
        HTML("text/html","HTML"),
        MARKDOWN("text/markdown", "Markdown"),
        TEXT("text/plain", "Text");
        final String type;
        final String displayName;
        NoteType(String type, String displayName){
            this.type = type;
            this.displayName = displayName;
        }
        public static NoteType fromString(String s){
            for(NoteType n: values()){
                if(n.type.equalsIgnoreCase(s) || n.displayName.equalsIgnoreCase(s)){
                    return n;
                }
            }
            return null;
        }
        public String getType(){ return type; }
        @Override
        public String toString() {
            return displayName;
        }
    }

    public enum State {
        STARTING,
        READY,
        STARTED,
        STOPPING,
        FINISHED,
        FAILED,
    }

    /**
     * The location of a stat sheet on a map view.
     */
    public enum StatSheetLocation {
        TOP_LEFT("Top-Left", "statSheet-topLeft"),
        TOP("Top", "statSheet-topRight"),
        TOP_RIGHT("Top-Right", "statSheet-top"),
        LEFT("Left", "statSheet-left"),
        RIGHT("Right", "statSheet-right"),
        BOTTOM_LEFT("Bottom-Left", "statSheet-bottomLeft"),
        BOTTOM("Bottom", "statSheet-bottom"),
        BOTTOM_RIGHT("Left", "statSheet-bottomRight");
        final String displayName;
        final String className;

        StatSheetLocation(String displayName, String className) {
            this.displayName = displayName;
            this.className = className;
        }
        public String className(){
            return className;
        }
        @Override
        public String toString() {
            return displayName;
        }
    }
}
