package net.rptools.servers;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.jknack.handlebars.*;
import com.github.jknack.handlebars.context.MapValueResolver;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.FileTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.rptools.data.TemplateData;
import net.rptools.data.config.Config;
import net.rptools.data.config.Pref;
import net.rptools.util.Alerts;
import net.rptools.util.Utils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.resource.PathResourceFactory;
import org.eclipse.jetty.util.resource.Resource;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.lang.Thread.sleep;
import static net.rptools.data.Constants.FALLBACK_TEMPLATE;
import static net.rptools.data.Constants.OBJECT_MAPPER;
import static net.rptools.data.TemplateData.TEMPLATE_DATA;
import static net.rptools.data.config.Config.CURRENT_THEME;

public class OneServlet extends HttpServlet {

    static final ObjectReader TEMPLATE_UPDATER = OBJECT_MAPPER.readerForUpdating(TEMPLATE_DATA);
    private static final Logger log = LoggerFactory.getLogger(OneServlet.class);
    private static final Template PAGE_TEMPLATE;
    private static final String RESOURCE_PATH = "/testPage";
    private static final TemplateLoader CLASS_PATH_TEMPLATE_LOADER = new ClassPathTemplateLoader(RESOURCE_PATH);
    private static final TemplateLoader PATH_TEMPLATE_LOADER = new FileTemplateLoader(Pref.getString(Config.TEMPLATE_FOLDER));
    private static final Handlebars PAGE_BARS = Utils.createHandlebars(CLASS_PATH_TEMPLATE_LOADER);
    private static final Handlebars HANDLEBARS = Utils.createHandlebars(PATH_TEMPLATE_LOADER);

    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() {
    };
    private static Resource templateResource;
    private static final Resource CLASSPATH_RESOURCE;
    private static final Resource TOKEN_IMAGES_RESOURCE;

    private static final ObjectNode CSS_OBJECT = Pref.getObjectNode(Config.ALL_THEME_CSS);
    private static String theme = Pref.getString(CURRENT_THEME);
    private static String themeCSS = Pref.getObjectNode(Config.ALL_THEME_CSS).get(theme).asText();
    private static Server server;
    private static CompletableFuture<?> sseFuture = CompletableFuture.completedFuture(true);
    private static final String IMAGE_CYCLE_JAVASCRIPT_TEXT;
    private static final ArrayNode TOKEN_IMAGE_URIS = OBJECT_MAPPER.createArrayNode();
    private static final ArrayNode TOKEN_IMAGES = OBJECT_MAPPER.createArrayNode();
    protected static final AtomicBoolean TEMPLATE_DATA_CHANGED = new AtomicBoolean(false);
    private static final Map<String, Double> AR_MAP = new HashMap<>();
    private static int initialHeight;
    private static int initialWidth;

    static {
        try {
            PAGE_TEMPLATE = PAGE_BARS.compile(CLASS_PATH_TEMPLATE_LOADER.sourceAt("testPage"));
            CLASSPATH_RESOURCE = new PathResourceFactory().newClassLoaderResource(RESOURCE_PATH);
            templateResource = new PathResourceFactory().newResource(Pref.getString(Config.TEMPLATE_FOLDER));
            TOKEN_IMAGES_RESOURCE = "/testPage/tokenImages".equals(Pref.getString(Config.TOKEN_IMAGES_FOLDER)) ?
                    new PathResourceFactory().newClassLoaderResource(Pref.getString(Config.TOKEN_IMAGES_FOLDER)) :
                    new PathResourceFactory().newResource(Pref.getString(Config.TOKEN_IMAGES_FOLDER));

            TOKEN_IMAGES_RESOURCE.getAllResources().forEach(resource -> {
                if (!resource.isDirectory()) {
                    String path = CLASSPATH_RESOURCE.getURI().relativize(resource.getURI()).toASCIIString();
                    ObjectNode imageObject = OBJECT_MAPPER.createObjectNode();
                    imageObject.put("name", Path.of(path).getFileName().toString());
                    imageObject.put("uri", path);
                    try {
                        BufferedImage bi = ImageIO.read(resource.newInputStream());
                        AR_MAP.put(path, (double) (bi.getHeight() / bi.getWidth()));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    TOKEN_IMAGE_URIS.add(path);
                    TOKEN_IMAGES.add(imageObject);
                }
            });
            TEMPLATE_DATA.set("tokenImages", TOKEN_IMAGE_URIS);
            TEMPLATE_DATA.set("tokenImageObjects", TOKEN_IMAGES);

            Template imageCycle = PAGE_BARS.compile("imageCycle");
            Map<String, Object> map = OBJECT_MAPPER.readValue(TEMPLATE_DATA.toString(), MAP_TYPE_REFERENCE);
            Context context = Context
                    .newBuilder(map)
                    .push(MapValueResolver.INSTANCE)
                    .build();
            IMAGE_CYCLE_JAVASCRIPT_TEXT = imageCycle.apply(context);
            HANDLEBARS.setCharset(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final java.util.List<Resource> RESOURCE_LIST = List.of(templateResource, CLASSPATH_RESOURCE, TOKEN_IMAGES_RESOURCE);

    public OneServlet(Server _server) {
        server = _server;
        TOKEN_IMAGE_URIS.add(TEMPLATE_DATA.get("image"));
        initialHeight = TEMPLATE_DATA.get("portraitHeight").asInt();
        initialWidth = TEMPLATE_DATA.get("portraitWidth").asInt();
    }


    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) {
        log.debug("POST Request - > {}", request.getRequestURI());
        String formString = null;
        try (BufferedReader reader = request.getReader()) { // try-with-resources auto-closes the reader
            formString = reader.lines().collect(Collectors.joining());
        } catch (IOException e) {
            log.error(e.getLocalizedMessage(), e);
            throw new RuntimeException(e);
        }
        if (request.getRequestURI().startsWith("/api/image")) {
            try {
                cycleImage(OBJECT_MAPPER.readTree(formString).get("currentTokenImage").asText());
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        } else if (request.getRequestURI().startsWith("/api/folder")) {
            try {
                Desktop.getDesktop().browse(Pref.getPath(Config.TEMPLATE_FOLDER).toUri());
            } catch (IOException ex) {
                Alerts.whoops(ex);
            }
        } else if (request.getRequestURI().startsWith("/api")) {
            if (!formString.equals("{}")) {
                try {
                    OneServlet.TEMPLATE_UPDATER.readValue(formString);
                    TemplateData.filterVisible();
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        log.debug("OneServlet GET request -> {}", request.getRequestURI());
        Utils.commonResponseBits(response);
        JsonNode barsNode = TEMPLATE_DATA.get(Config.BARS);
        if (barsNode instanceof ObjectNode objectNode) {
            objectNode.properties().forEach(nodeEntry -> {
                if (nodeEntry instanceof ObjectNode barNode) {
                    barNode.put("value", Math.random());
                }
            });
        }
        String requestURI = request.getRequestURI().strip();
        if (requestURI.toLowerCase().endsWith(".hbs") || requestURI.equalsIgnoreCase("/sheet/default")) {
            getHandlebars(request, response);
        } else if (requestURI.toLowerCase().endsWith("mt-stat-sheet.css")) {
            getMtCss(response);
        } else
//            if (requestURI.toLowerCase().startsWith("/sse")) {
//            response.setContentType("text/event-stream"); //most important part
//            if (!sseFuture.state().equals(Future.State.RUNNING)) {
//                sseFuture = CompletableFuture.runAsync(() ->
//                {
//                    try {
//                        sseGet(request, response);
//                    } catch (IOException e) {
//                        throw new RuntimeException(e);
//                    }
//                });
//            }
//        }
//            else
        {
            getFile(request, response);
        }
    }

    protected void getHandlebars(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        log.debug("Handlebars GET request -> {}", request.getRequestURI());
        Writer writer = null;

        Map<String, Object> map = OBJECT_MAPPER.readValue(TEMPLATE_DATA.toString(), MAP_TYPE_REFERENCE);
        Context context = Context
                .newBuilder(map)
                .push(MapValueResolver.INSTANCE)
                .build();

        String output = "";
        boolean isSheet = false;
        try {
            if (request.getRequestURI().equalsIgnoreCase("/sheet/_default.hbs")) {
                output = FALLBACK_TEMPLATE.apply(context);
            } else if (request.getRequestURI().endsWith("Page.hbs")) {
                output = PAGE_TEMPLATE.apply(context);
            } else if (request.getRequestURI().endsWith(".hbs")) {
                try {
                    Template template = HANDLEBARS.compile(PATH_TEMPLATE_LOADER.sourceAt(requestURI(request).replace("/sheet", "").replace(".hbs", "")));
                    output = template.apply(context);
                    isSheet = true;
                } catch (HandlebarsException | IOException e) {
                    ObjectNode node = OBJECT_MAPPER.valueToTree(TEMPLATE_DATA);
                    node.put("message", e.getLocalizedMessage());
                    context = Context
                            .newBuilder(node)
                            .push(JsonNodeValueResolver.INSTANCE)
                            .build();
                    output = FALLBACK_TEMPLATE.apply(context);
                }
            }
            Document doc = Jsoup.parse(output);
            var cssNode = doc.createElement("link");
            cssNode.id("mtThemeCss");
            cssNode.attr("rel", "stylesheet");
            cssNode.attr("href", "./css/mt-stat-sheet.css");

            var head = doc.head();
            if (isSheet) {
                var baseNode = doc.createElement("base");
                baseNode.attr("href", "./sheet");
                head.insertChildren(0, baseNode);

                if (request.getQueryString() != null) {
                    var imageCycleNode = doc.createElement("script");
                    imageCycleNode.id("imageCycle");
                    imageCycleNode.html(IMAGE_CYCLE_JAVASCRIPT_TEXT);
                    doc.body().insertChildren(-1, imageCycleNode);
                }
            }
            for (Element element : head.children()) {
                if (element.tag().equals(Tag.valueOf("link"))) {
                    if (element.hasAttr("href")) {
                        if (element.attr("href").toLowerCase().startsWith("lib")) {
                            head.insertChildren(head.elementSiblingIndex(), cssNode);
                            element.remove();
                        }
                    }
                }
            }
            response.setStatus(HttpServletResponse.SC_OK);
            writer = response.getWriter();
            writer.write(doc.outerHtml());
        } catch (
                HandlebarsException ex) {
            Utils.handlebarsError(ex, response);
        } catch (JsonParseException ex) {
            Alerts.whoops(ex);
            log.error("Unexpected error", ex);
            Utils.jsonError(ex, request, response);
        } catch (FileNotFoundException ex) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        } catch (IOException | RuntimeException ex) {
            Alerts.whoops(ex);
            log.error("Unexpected error", ex);
            throw ex;
        } catch (Exception ex) {
            Alerts.whoops(ex);
            log.error("Unexpected error", ex);
            throw new ServletException(ex);
        } finally {
            IOUtils.closeQuietly(writer);
        }
    }

    protected void getMtCss(final HttpServletResponse response) throws HandlebarsException, IOException {
        String theme_ = TEMPLATE_DATA.get("theme").asText();
        if (!theme_.equalsIgnoreCase(theme)) {
            theme = theme_;
            themeCSS = CSS_OBJECT.get(theme_).asText();
        }
        response.setStatus(HttpServletResponse.SC_OK);
        Writer writer = response.getWriter();
        writer.write(themeCSS);
    }

//    protected void sseGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
//        log.debug("SSEServlet request -> {}", request.getRequestURI());
//
//        if(Pref.getBoolean(Config.WATCH_FOLDER) && !WatchFolder.getState().equals(Constants.State.STARTED)){
//            log.info("WatchFolder issue");
//        }
//        // Set content type for SSE
//        response.setCharacterEncoding("UTF-8");
//        // Disable caching
//        response.setHeader("X-Accel-Buffering", "no");
//        response.setHeader("Cache-Control", "no-cache");
//        response.setHeader("Connection", "keep-alive");
//        response.setStatus(200);
//        Writer writer = response.getWriter();
//        if (server != null && server.isRunning()){
//            if(SheetsObject.getWatchChange()){
//                CompletableFuture.runAsync(()-> templateResource = new PathResourceFactory().newResource(Pref.getString(Config.TEMPLATE_FOLDER)));
//                writer.write("data: {\"source-change\":true, \"template-change\":false, \"idle\": false}\n\n");
//                SheetsObject.setWatchChange(false);
//                log.info("SSEServlet -> Source change notification sent");
//            } else if(TEMPLATE_DATA_CHANGED.get()) {
//                log.info("SSEServlet -> Template change notification sent");
//                writer.write("data: {\"source-change\":false, \"template-change\":true, \"idle\": false}\n\n");
//                TEMPLATE_DATA_CHANGED.set(false);
//            } else {
//                writer.write("data: {\"source-change\":false, \"template-change\":false, \"idle\": false}\n\n");
//            }
//
//            writer.flush();
//        }
//        try {
//            sleep(240);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }
//        writer.close();
//    }

    /**
     * Remove context path from the request's URI.
     *
     * @param request The current request.
     * @return Same as {@link HttpServletRequest#getRequestURI()} without context
     * path.
     */
    private String requestURI(final HttpServletRequest request) {
        log.debug("getContextPath: {}", request.getContextPath());
        return request.getRequestURI().replace(request.getContextPath(), "");
    }

    protected void getFile(HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.debug("getFile: {}", request.getRequestURI());
        String subPath = request.getRequestURI().substring(1).replace("?cachelib=false", "");
        if (request.getRequestURI() == null || request.getRequestURI().isBlank()) {
            log.error("No resource specified: {}", request.getRequestURI());
            response.sendError(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        Resource resource = null;
        for (Resource resource_ : RESOURCE_LIST) {
            if (resource_.equals(TOKEN_IMAGES_RESOURCE) && subPath.contains("tokenImages")) {
                resource = TOKEN_IMAGES_RESOURCE.resolve(subPath.substring(subPath.indexOf("tokenImages") + 11));
            } else if (resource_.equals(templateResource) && subPath.startsWith("sheet/")) {
                resource = templateResource.resolve(subPath.substring(6));
            } else {
                resource = CLASSPATH_RESOURCE.resolve(subPath);
            }
            if (resource.exists()) {
                break;
            }
        }
        if (resource == null || !resource.exists()) {
            log.error("Resource not found: {}", request.getRequestURI());
            response.reset();
            return;
        }
        if (resource.isReadable() && !resource.isDirectory()) {
            String mimeType = getServletContext().getMimeType(resource.getPath().toString());
            response.setContentType(mimeType);

            try (InputStream is = resource.newInputStream()) {
                // it is the responsibility of the container to close output stream
                if (is != null) {
                    OutputStream os = response.getOutputStream();
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                    os.flush();
                    os.close();
                } else {
                    log.error("Resource not accessible: {}", request.getRequestURI());
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "File not readable.");
                }
            } catch (Exception e) {
                log.error(e.getLocalizedMessage(), e);
            }
        } else {
            log.error("Resource not readable: {}", request.getRequestURI());
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "File not readable.");
        }
    }

    private void cycleImage(String imageUri) {
        TEMPLATE_DATA.put("image", imageUri);
        TEMPLATE_DATA.put("portrait", imageUri);
        if(AR_MAP.containsKey(imageUri)) {
            double AR = AR_MAP.get(imageUri);
            AR = AR != 0 ? AR : 1;
            TEMPLATE_DATA.put("portraitWidth", initialHeight * AR);
        }
        TEMPLATE_DATA_CHANGED.set(true);
    }
}
