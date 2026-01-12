package net.rptools.servers;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectReader;
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
import net.rptools.data.SheetsObject;
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

import java.awt.*;
import java.io.*;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static java.lang.Thread.sleep;
import static net.rptools.data.Constants.FALLBACK_TEMPLATE;
import static net.rptools.data.Constants.OBJECT_MAPPER;
import static net.rptools.data.TemplateData.TEMPLATE_DATA;
import static net.rptools.data.config.Config.THEME;

public class OneServlet extends HttpServlet {
    static final ObjectReader TEMPLATE_UPDATER = OBJECT_MAPPER.readerForUpdating(TEMPLATE_DATA);
    private static final Logger log = LoggerFactory.getLogger(OneServlet.class);
    private static final Template PAGE_TEMPLATE;
    private static final String RESOURCE_PATH = "/testPage";
    private static final TemplateLoader CLASS_PATH_TEMPLATE_LOADER = new ClassPathTemplateLoader(RESOURCE_PATH);
    private static final TemplateLoader PATH_TEMPLATE_LOADER = new FileTemplateLoader(Pref.getString(Config.TEMPLATE_FOLDER));
    private static final Handlebars PAGE_BARS = new Handlebars(CLASS_PATH_TEMPLATE_LOADER);
    private static final Handlebars HANDLEBARS = new Handlebars(PATH_TEMPLATE_LOADER);

    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() {
};
    private static final Resource TEMPLATE_RESOURCE;
    private static final Resource CLASSPATH_RESOURCE;
    private static final ObjectNode CSS_OBJECT = Pref.getObjectNode(Config.THEME_CSS);
    private static String theme = Pref.getString(THEME);
    private static String themeCSS = Pref.getObjectNode(Config.THEME_CSS).get(theme).asText();
    private static Server server;
    private static CompletableFuture<?> sseFuture = CompletableFuture.completedFuture(true);
    static {
        try {
            Utils.registerHandlebarsHelpers(HANDLEBARS);
            Utils.registerHandlebarsHelpers(PAGE_BARS);
            PAGE_TEMPLATE = PAGE_BARS.compile(CLASS_PATH_TEMPLATE_LOADER.sourceAt("testPage"));
            CLASSPATH_RESOURCE = new PathResourceFactory().newClassLoaderResource(RESOURCE_PATH);
            TEMPLATE_RESOURCE = new PathResourceFactory().newResource(Pref.getString(Config.TEMPLATE_FOLDER));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public OneServlet(Server _server) {
        server = _server;
    }


    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) {
        log.info("POST Request - > {}", request.getRequestURI());
        if (request.getRequestURI().startsWith("/api")) {
            try (BufferedReader reader = request.getReader()) { // try-with-resources auto-closes the reader
                String string = reader.lines().collect(Collectors.joining());
                if (string.contains("openFolder")) {
                    try {
                        Desktop.getDesktop().browse(Pref.getPath(Config.TEMPLATE_FOLDER).toUri());
                    } catch (IOException ex) {
                        Alerts.whoops(ex);
                    }
                } else if (!string.equals("{}")) {
                    OneServlet.TEMPLATE_UPDATER.readValue(string);
                    TemplateData.filterProperties();
                }
            } catch (IOException e) {
                log.error(e.getLocalizedMessage(), e);
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        log.info("OneServlet GET request -> {}", request.getRequestURI());
        String requestURI = request.getRequestURI().strip();
        if (requestURI.toLowerCase().endsWith(".hbs") || requestURI.equalsIgnoreCase("/sheet/default")) {
            handlebarsGet(request, response);
        } else if (requestURI.toLowerCase().endsWith("mt-stat-sheet.css")) {
            getMtCss(response);
        } else if (requestURI.toLowerCase().startsWith("/sse")) {
            if(!sseFuture.state().equals(Future.State.RUNNING)){
                // Set content type for SSE
                response.setContentType("text/event-stream"); //most important part
                response.setCharacterEncoding("UTF-8");
                // Disable caching
                response.setHeader("X-Accel-Buffering", "no");
                response.setHeader("Cache-Control", "no-cache");
                response.setHeader("Connection", "keep-alive");
                response.setStatus(200);
                sseFuture = CompletableFuture.runAsync(() ->
                {
                    try {
                        sseGet(request, response);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        } else {
            getFile(request, response);
        }
    }

    protected void handlebarsGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        log.info("Handlebars GET request -> {}", request.getRequestURI());
        Writer writer = null;
        Utils.commonResponseBits(response);

        Map<String, Object> map = OBJECT_MAPPER.readValue(TEMPLATE_DATA.toString(), MAP_TYPE_REFERENCE);
        Context context = Context
                .newBuilder(map)
                .push(MapValueResolver.INSTANCE)
                .build();

            String output = "";
            boolean setBase = true;
            try{
            if(request.getRequestURI().equalsIgnoreCase("/sheet/_default.hbs")){
                output = FALLBACK_TEMPLATE.apply(context);
            } else if (request.getRequestURI().endsWith("Page.hbs")) {
                output = PAGE_TEMPLATE.apply(context);
                setBase = false;
            } else if (request.getRequestURI().endsWith(".hbs")) {
                try {
                    Template template = HANDLEBARS.compile(PATH_TEMPLATE_LOADER.sourceAt(requestURI(request).replace("/sheet", "").replace(".hbs", "")));
                    output = template.apply(context);
                } catch (HandlebarsException|IOException e){
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
            if (setBase) {
                var base = doc.createElement("base");
                base.attr("href", "http://localhost:" + Pref.getInt(Config.SERVER_PORT) + "/sheet");
                head.insertChildren(0, base);

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

    protected void sseGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.debug("SSEServlet request -> {}", request.getRequestURI());
        Writer writer = response.getWriter();
        if (server != null && server.isRunning() && SheetsObject.getWatchChange()) {
                writer.write("data: true\n\n");
                writer.flush();
                log.info("SSEServlet -> Update notification sent");
                SheetsObject.setWatchChange(false);
            }
            try {
                sleep(400);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        writer.close();
    }

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
        String subPath = request.getRequestURI().substring(1);

        if (request.getRequestURI() == null || request.getRequestURI().isBlank()) {
            response.sendError(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        Resource resource = CLASSPATH_RESOURCE.resolve(subPath);
        if (!resource.exists()) {
            resource = TEMPLATE_RESOURCE.resolve(subPath);
            if (!resource.exists()) {
                response.reset();
                return;
            }
        }
        if (resource.isReadable() && !resource.isDirectory()) {
            String mimeType = getServletContext().getMimeType(resource.getPath().toString());
            log.info("MimeType: {}", mimeType);
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
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "File not readable.");
                }
            }
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "File not readable.");
        }
    }
}
