package net.rptools.servers;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.jknack.handlebars.*;
import com.github.jknack.handlebars.io.TemplateLoader;
import net.rptools.data.config.Config;
import net.rptools.data.config.Pref;
import net.rptools.data.SheetsObject;
import net.rptools.util.Utils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebXmlConfiguration;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static net.rptools.data.TemplateData.TEMPLATE_DATA;
import static net.rptools.data.config.Config.THEME;

public class HandlebarsServer {
    private static final Logger log = LoggerFactory.getLogger(HandlebarsServer.class);
    private static final String FALLBACK_TEMPLATE;

    static {
        String template = "";
        try (InputStream in = HandlebarsServer.class.getResourceAsStream("/testSpace/jedi.svg")) {
            if (in != null) {
                template += "<html lang=\"en-AU\"><body><div id=\"statSheet\" class=\"statSheet-bottomLeft\">";
                template += new String(in.readAllBytes(), StandardCharsets.UTF_8);
                template += "</div></body></html>";
            }
        } catch (IOException e) {
            log.error(e.getLocalizedMessage(), e);
        }
        FALLBACK_TEMPLATE = template;
    }

    private static final String CONTEXT = "/";
    private Handlebars handlebars;
    private Server server;
    private static final ObjectNode CSS_OBJECT = Pref.getObjectNode(Config.THEME_CSS);
    private Path basePath;

    public HandlebarsServer() {
        basePath = Pref.getPath(Config.TEMPLATE_FOLDER);
        try {
            server = new Server(Pref.getInt(Config.HANDLEBARS_PORT));
            handlebars = new Handlebars();
            Utils.registerHandlebarsHelpers(handlebars);

            Servlet servlet = new HandlebarsServlet();
            ServletHolder servletHolder = new ServletHolder("HandlebarsServletHolder", servlet);

            WebAppContext root = new WebAppContext();
            root.setErrorHandler(Utils.errorHandlerSupplier.get());
            root.setContextPath(CONTEXT);
            root.setLogger(null);

            root.setResourceBase(basePath.toString());
            root.addServlet(servletHolder, "/");
            root.addServlet(servletHolder, "*.css");
            root.addServlet(servletHolder, "*" + TemplateLoader.DEFAULT_SUFFIX);
            root.setParentLoaderPriority(true);

            // prevent jetty from loading the webapp web.xml
            root.setConfigurations(new Configuration[]{new WebXmlConfiguration() {
                @Override
                protected Resource findWebXml(final WebAppContext context) {
                    return null;
                }
            }});


//            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
            server.addLifeCycleListener(new AbstractLifeCycle.AbstractLifeCycleListener() {
                @Override
                public void lifeCycleStarted(final LifeCycle event) {
                    log.info("Handlebars server started. http://localhost:{}{}/{}",
                            Pref.getInt(Config.HANDLEBARS_PORT),
                            CONTEXT,
                            SheetsObject.getJson().get("sheet").asText());
                }
            });
            server.setHandler(root);

        } catch (Exception e) {
            log.info(e.getLocalizedMessage(), e);
        }

    }

    public void start() throws Exception {
        if (server != null) {
            server.start();
            server.join();
        }
    }

//    public void stop() {
//        try {
//            server.stop();
//            log.info("Handlebars server stopped");
//        } catch (Exception ex) {
//            log.warn("Can't stop the server", ex);
//        }
//
//    }
    private static String theme = Pref.getString(THEME);
    private static String themeCSS = Pref.getObjectNode(Config.THEME_CSS).get(theme).asText();

    public class HandlebarsServlet extends HttpServlet {
        private static final Logger log = LoggerFactory.getLogger(HandlebarsServlet.class);

        @Override
        protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
            log.debug("Handlebars POST request -> {}", req.getRequestURI());
            doGet(req, resp);
        }

        @Override
        protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
                throws ServletException, IOException {
            log.debug("Handlebars GET request -> {}", request.getRequestURI());

            Writer writer = null;
            Utils.commonResponseBits(response);
            if(request.getRequestURI().toLowerCase().endsWith("mt-stat-sheet.css")){
                String theme_ = TEMPLATE_DATA.get("theme").asText();
                if (!theme_.equalsIgnoreCase(theme)) {
                    theme = theme_;
                    themeCSS = CSS_OBJECT.get(theme_).asText();
                }
                writer = response.getWriter();
                writer.write(themeCSS);
            } else if(request.getRequestURI().toLowerCase().endsWith(".hbs")) {
                try {
                    Template template = handlebars.compileInline(getTemplateText.apply(requestURI(request)));

                    Context context = Context
                            .newBuilder(TEMPLATE_DATA)
                            .push(JsonNodeValueResolver.INSTANCE)
                            .build();

                    Document doc = Jsoup.parse(template.apply(context));
                    var cssNode = doc.createElement("link");
                    cssNode.id("mtThemeCss");
                    cssNode.attr("rel","stylesheet");
                    cssNode.attr("href", "./css/mt-stat-sheet.css");
                    var head = doc.head();
                    head.insertChildren(0, cssNode);

                    writer = response.getWriter();
                    writer.write(doc.outerHtml());
                } catch (HandlebarsException ex) {
                    Utils.handlebarsError(ex, response);
                } catch (JsonParseException ex) {
                    Utils.whoops(ex);
                    log.error("Unexpected error", ex);
                    Utils.jsonError(ex, request, response);
                } catch (FileNotFoundException ex) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                } catch (IOException | RuntimeException ex) {
                    Utils.whoops(ex);
                    log.error("Unexpected error", ex);
                    throw ex;
                } catch (Exception ex) {
                    Utils.whoops(ex);
                    log.error("Unexpected error", ex);
                    throw new ServletException(ex);
                } finally {
                    IOUtils.closeQuietly(writer);
                }
            } else {
                Path sourcePath = basePath.resolve(request.getRequestURI().substring(1));
                if (Files.exists(sourcePath) && Files.isRegularFile(sourcePath)) {
                    if (Files.isReadable(sourcePath) || sourcePath.toFile().setReadable(true)) {
                        try {
                            writer = response.getWriter();
                            writer.write(Files.readString(sourcePath, StandardCharsets.ISO_8859_1));
                        } catch (IOException e) {
                            log.error(e.getLocalizedMessage(), e);
                        }
                    }
                }
            }
        }

        /**
         * Remove context path from the request's URI.
         *
         * @param request The current request.
         * @return Same as {@link HttpServletRequest#getRequestURI()} without context
         * path.
         */
        private String requestURI(final HttpServletRequest request) {
            return request.getRequestURI().replace(request.getContextPath(), "");
        }
    }

    public Function<String, String> getTemplateText = requestURI -> {
        if (requestURI == null || requestURI.isBlank() || requestURI.equalsIgnoreCase("null")) {
            return FALLBACK_TEMPLATE;
        }
        Path sourcePath = basePath.resolve(requestURI.substring(1));

        if (Files.exists(sourcePath) && Files.isRegularFile(sourcePath)) {
            if (Files.isReadable(sourcePath) || sourcePath.toFile().setReadable(true)) {
                try {
                    return Files.readString(sourcePath, StandardCharsets.ISO_8859_1);
                } catch (IOException e) {
                    log.error(e.getLocalizedMessage(), e);
                }
            }
        }
        return FALLBACK_TEMPLATE;
    };
    public static final Supplier<Runnable> handlebarsRunnable = () -> () -> {
        HandlebarsServer handlebarsServer = new HandlebarsServer();
        try {
            handlebarsServer.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    };
}

