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
import static net.rptools.data.TemplateData.TEMPLATE_DATA;

public class TestingServer {
    private static final Logger log = LoggerFactory.getLogger(TestingServer.class);

    private final ObjectNode DATASETS;
    private static final String RESOURCE_FOLDER = "/testSpace";
    private static final String PATH_PREFIX = "testSpace";
    private static final URLTemplateLoader TEMPLATE_LOADER = new ClassPathTemplateLoader();
    private final File FOLDER;
    private final Server server;

    public TestingServer() {
        DATASETS = Pref.getObjectNode(Config.DATASETS);
        try {
            FOLDER = Path.of(Objects.requireNonNull(TestingServer.class.getResource(RESOURCE_FOLDER)).toURI()).toFile();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        try {
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

        } catch (Exception e) {
            log.error(e.getLocalizedMessage(),e);
            throw new RuntimeException(e);
        }
    }

    public boolean start() throws Exception {
        if (server != null) {
            server.start();
            server.join();
            return server.isRunning();
        }
        return false;
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

    }

    public static final Supplier<Runnable> testServerRunnable = () -> () -> {
        TestingServer testingServer = new TestingServer();
        try {
            if(!testingServer.start()){
                throw new RuntimeException("Not running");
            }
        } catch (Exception e) {
            testingServer.stop();
            throw new RuntimeException(e);
        }
    };

}