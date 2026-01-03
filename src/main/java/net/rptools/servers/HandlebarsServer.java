package net.rptools.servers;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.jknack.handlebars.*;
import com.github.jknack.handlebars.io.FileTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import net.rptools.data.Config;
import net.rptools.data.SheetsObject;
import net.rptools.util.Utils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Writer;
import java.util.function.Supplier;

import static net.rptools.data.Constants.TEMPLATE_DATA;
import static org.apache.commons.io.FilenameUtils.removeExtension;

public class HandlebarsServer {
    private static final Logger log = LoggerFactory.getLogger(HandlebarsServer.class);
    private static final String CONTEXT = "/";
    private final Handlebars handlebars;
    private final Server server;
    private static final ObjectNode CSS_OBJECT = Config.getObjectNode(Config.THEME_CSS);

    public HandlebarsServer() {
        server = new Server(Config.getInt(Config.HANDLEBARS_PORT));
        TemplateLoader loader = new FileTemplateLoader(Config.getPath(Config.TEMPLATE_FOLDER).toFile());
        handlebars = new Handlebars(loader);
        Utils.registerHandlebarsHelpers(handlebars);

        Servlet servlet = new HandlebarsServlet();
        ServletHolder servletHolder = new ServletHolder("HandlebarsServletHolder", servlet);

        WebAppContext root = new WebAppContext();
        root.setErrorHandler(Utils.errorHandlerSupplier.get());
        root.setContextPath(CONTEXT);
        root.setLogger(null);

        root.setResourceBase(Config.getPath(Config.TEMPLATE_FOLDER).toAbsolutePath().toString());
        root.addServlet(servletHolder, "*" + TemplateLoader.DEFAULT_SUFFIX);
        root.setParentLoaderPriority(true);

        // prevent jetty from loading the webapp web.xml
        root.setConfigurations(new Configuration[]{new WebXmlConfiguration() {
            @Override
            protected Resource findWebXml(final WebAppContext context) {
                return null;
            }
        }});

        HandlerList handlerList = new HandlerList(root, SessionHandling.fileSessionHandler());
        server.setHandler(handlerList);

//            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
        server.addLifeCycleListener(new AbstractLifeCycle.AbstractLifeCycleListener() {
            @Override
            public void lifeCycleStarted(final LifeCycle event) {
                log.info("Handlebars server started. http://localhost:{}{}/{}",
                        Config.getInt(Config.HANDLEBARS_PORT),
                        CONTEXT,
                        SheetsObject.getJson().get("sheet").asText());
            }
        });

//        } catch (Exception e) {
//        }

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

    public class HandlebarsServlet extends HttpServlet {
        private static final Logger log = LoggerFactory.getLogger(HandlebarsServlet.class);

        @Override
        protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
            log.info("Handlebars POST request -> {}", req.getRequestURI());
            doGet(req, resp);
        }

        @Override
        protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
                throws ServletException, IOException {
            log.info("Handlebars GET request -> {}", request.getRequestURI());
            Writer writer = null;
            Utils.commonResponseBits(response);
            try {
                Template template = handlebars.compile(removeExtension(requestURI(request)));

                String css = CSS_OBJECT.get(TEMPLATE_DATA.get("theme").asText()).asText();

                Context context = Context
                        .newBuilder(TEMPLATE_DATA)
                        .push(JsonNodeValueResolver.INSTANCE)
                        .build();

                Document doc = Jsoup.parse(template.apply(context));
                if(!css.isBlank()) {
                    var cssNode = doc.createElement("style");
                    cssNode.id("themeCss");
                    cssNode.appendText(Config.getObjectNode(Config.THEME_CSS).get(Config.getString(Config.THEME)).asText());

                    var head = doc.head();
                    head.insertChildren(0, cssNode);
                }
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


    public static final Supplier<Runnable> handlebarsRunnable = () -> () -> {
        HandlebarsServer handlebarsServer = new HandlebarsServer();
        try {
            handlebarsServer.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    };
}

