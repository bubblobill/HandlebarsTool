package net.rptools.data;

import com.fasterxml.jackson.databind.node.ObjectNode;

public record TokenProperty(boolean gmOnly, boolean ownerOnly, String name, String displayName, String shortName,
                            String value) {
//    public boolean isEmpty() {
//        return (name == null || name.isBlank()) && (displayName == null || displayName.isBlank()) && (shortName == null || shortName.isBlank()) && (value == null || value.isBlank());
//    }
//
//    public TokenProperty(ObjectNode objectNode) {
//        this(objectNode.get("gmOnly").asBoolean(),
//                objectNode.get("ownerOnly").asBoolean(),
//                objectNode.get("name").asText(),
//                objectNode.get("displayName").asText(),
//                objectNode.get("shortName").asText(),
//                objectNode.get("value").asText()
//        );
//    }

//    public ObjectNode asObject() {
//        ObjectNode o = Constants.OBJECT_MAPPER.createObjectNode();
//        o.put("gmOnly", gmOnly());
//        o.put("ownerOnly", ownerOnly());
//        o.put("name", name());
//        o.put("displayName", displayName());
//        o.put("shortName", shortName());
//        o.put("value", value());
//        return o;
//    }
}
