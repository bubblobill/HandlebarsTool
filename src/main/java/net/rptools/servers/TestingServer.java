package net.rptools.servers;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.jknack.handlebars.*;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import com.github.jknack.handlebars.io.URLTemplateLoader;
import net.rptools.data.config.Config;
import net.rptools.data.config.Pref;
import net.rptools.data.SheetsObject;
import net.rptools.util.Utils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebXmlConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static net.rptools.data.Constants.OBJECT_MAPPER;
import static net.rptools.data.Constants.TEMPLATE_DATA;

public class TestingServer {
    private static final Logger log = LoggerFactory.getLogger(TestingServer.class);

    private static final ObjectNode DATASETS = Pref.getObjectNode(Config.DATASETS);
    private static final String RESOURCE_FOLDER = "/testSpace";
    private static final String PATH_PREFIX = "testSpace";
    private static final URLTemplateLoader TEMPLATE_LOADER = new ClassPathTemplateLoader();
    private static final File FOLDER;
    private final Server server;

    static {
        try {
            FOLDER = Path.of(Objects.requireNonNull(TestingServer.class.getResource(RESOURCE_FOLDER)).toURI()).toFile();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public TestingServer() {
        try {
            TEMPLATE_DATA.removeAll();
            TEMPLATE_DATA.setAll(SheetsObject.getJson());
            TEMPLATE_DATA.set("datasetNames", Pref.getArrayNode(Config.DATASET_NAMES));
            TEMPLATE_DATA.put("datasetName", Pref.getString(Config.DATASET_NAME));
            TEMPLATE_DATA.put("statSheetLocation", Pref.getString(Config.LOCATION));
            TEMPLATE_DATA.set("themes", OBJECT_MAPPER.readTree(OBJECT_MAPPER.writerFor(List.class).writeValueAsString(Pref.getList(Config.THEME_CSS))));
            TEMPLATE_DATA.put("theme", Pref.getString(Config.THEME));
            TEMPLATE_DATA.put("background", Pref.getString(Config.BACKGROUND));
            TEMPLATE_DATA.put("viewAs", Pref.getString(Config.VIEW_AS));
            addDataSet();

            TEMPLATE_LOADER.setPrefix(RESOURCE_FOLDER);

            Handlebars handlebars = new Handlebars(TEMPLATE_LOADER);
            Utils.registerHandlebarsHelpers(handlebars);
            Template template = handlebars.compile(PATH_PREFIX);

            Servlet pageServlet = new TestServlet(template);
            ServletHolder pageServletHolder = new ServletHolder("TestServletHolder", pageServlet);


            WebAppContext root = new WebAppContext();
            root.setErrorHandler(Utils.errorHandlerSupplier.get());
            root.setPersistTempDirectory(false);
            root.setLogger(null);
            root.setParentLoaderPriority(true);
            root.setContextPath(TemplateLoader.DEFAULT_PREFIX);
            root.setResourceBase(FOLDER.getAbsolutePath());
            root.addServlet(pageServletHolder, "*" + TemplateLoader.DEFAULT_SUFFIX);

            // prevent jetty from loading the webapp web.xml
            root.setConfigurations(new Configuration[]{new WebXmlConfiguration() {
                @Override
                protected Resource findWebXml(final WebAppContext context) {
                    return null;
                }
            }});

            server = new Server(Pref.getInt(Config.SERVER_PORT));
            server.addLifeCycleListener(new AbstractLifeCycle.AbstractLifeCycleListener() {
                @Override
                public void lifeCycleStarted(final LifeCycle event) {
                    log.info("Testing server started on port {}. http://localhost:{}/testSpace{}", Pref.getInt(Config.SERVER_PORT), Pref.getInt(Config.SERVER_PORT), TemplateLoader.DEFAULT_SUFFIX);
                }
            });
            server.setHandler(root);
            if(Pref.getBoolean(Config.WATCH_FOLDER)) {
                SSEServlet sseServlet = new SSEServlet();
                ServletHolder sseServletHolder = new ServletHolder("SSEServletHolder", sseServlet);
                root.addServlet(sseServletHolder, "/sse");
                sseServlet.setServer(server);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void start() throws Exception {
        if (server != null) {
            server.start();
            server.join();
        }
    }

    public void stop() {
        try {
            server.stop();
            log.info("Testing server stopped");
        } catch (Exception ex) {
            log.warn("Can't stop the Testing server", ex);
        }
    }
    public void addDataSet() {
        ObjectNode node = (ObjectNode) DATASETS.get(Pref.getString(Config.DATASET_NAME));
        node.fieldNames().forEachRemaining(fieldName -> TEMPLATE_DATA.set(fieldName, node.get(fieldName)));
    }

    public static final Supplier<Runnable> testServerRunnable = () -> () -> {
        TestingServer testingServer = new TestingServer();
        try {
            testingServer.start();
        } catch (Exception e) {
            testingServer.stop();
            throw new RuntimeException(e);
        }
    };

}