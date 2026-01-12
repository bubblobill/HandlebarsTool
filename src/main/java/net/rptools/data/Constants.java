package net.rptools.data;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import org.eclipse.jetty.http.MimeTypes;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("SpellCheckingInspection")
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
    public static final Path USER_DIR = Path.of(System.getProperty("user.dir")).toAbsolutePath();
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
        NOT_STARTED,
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


    public static final Map<String,String> MIME_MAP = new HashMap<>(){{
        put("apng", "image/apng");
        put("asc", "text/plain");
        put("bmp", "image/bmp");
        put("css", "text/css");
        put("dtd", "application/xml-dtd");
        put("gif", "image/gif");
        put("hbs", "text/html");
        put("htm", "text/html");
        put("html", "text/html");
        put("jp2", "image/jpeg2000");
        put("jpe", "image/jpeg");
        put("jpeg", "image/jpeg");
        put("jpg", "image/jpeg");
        put("js", "text/javascript");
        put("json", "application/json");
        put("jsp", "text/html");
        put("mjs", "text/javascript");
        put("png", "image/png");
        put("rtf", "application/rtf");
        put("rtx", "text/richtext");
        put("sgm", "text/sgml");
        put("sgml", "text/sgml");
        put("svg", "image/svg+xml");
        put("svgz", "image/svg+xml");
        put("tif", "image/tiff");
        put("tiff", "image/tiff");
        put("txt", "text/plain");
        put("wbmp", "image/vnd.wap.wbmp");
        put("webp", "image/webp");
        put("xbm", "image/x-xbitmap");
        put("xcf", "image/xcf");
        put("xml", "application/xml");
        put("xsd", "application/xml");
        put("xsl", "application/xml");
    }};
    public static final MimeTypes.Mutable MIME_TYPES = new MimeTypes.Mutable();
    static {
        for(Map.Entry<String, String> entry: MIME_MAP.entrySet()){
            MIME_TYPES.addMimeMapping(entry.getKey(), entry.getValue());
        }
    }
    public static final Template FALLBACK_TEMPLATE;
    private static final TemplateLoader TEMPLATE_LOADER = new ClassPathTemplateLoader("/testPage");
    private static final Handlebars HANDLEBARS = new Handlebars(TEMPLATE_LOADER);

    static {
        Template template;
        try {
            template = HANDLEBARS.compile(TEMPLATE_LOADER.sourceAt("fallBack"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        FALLBACK_TEMPLATE = template;
    }
}
