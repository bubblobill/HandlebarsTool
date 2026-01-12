package net.rptools.servers;

import jakarta.servlet.Servlet;
import net.rptools.data.Constants;
import net.rptools.data.config.Config;
import net.rptools.data.config.Pref;
import net.rptools.util.Utils;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.*;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.resource.PathResourceFactory;
import org.eclipse.jetty.util.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public class Servitude {
    private static final Logger log = LoggerFactory.getLogger(Servitude.class);
    private static final String RESOURCE_FOLDER = "/testPage";
    private static final Path REQUEST_LOG_FILE = Constants.USER_DIR.toAbsolutePath().resolve("_Log").resolve("HandlebarsTool.log");
    private static final AtomicReference<Constants.State> state = new AtomicReference<>(Constants.State.NOT_STARTED);
    private final Resource CLASS_PATH_RESOURCE;
    protected final Server server;

    public Servitude() {
        try {
            CLASS_PATH_RESOURCE = new PathResourceFactory().newClassLoaderResource(RESOURCE_FOLDER);
        } catch (IllegalArgumentException e) {
            state.set(Constants.State.FAILED);
            log.error(e.getLocalizedMessage(), e);
            throw new RuntimeException(e);
        }
        if (state.get().equals(Constants.State.FAILED)) {
            throw new RuntimeException("Prior failure.");
        }
        state.set(Constants.State.STARTING);

        server = new Server(Pref.getInt(Config.SERVER_PORT));
        server.setErrorHandler(Utils.errorHandlerSupplier.get());

        try {
            server.setErrorHandler(Utils.errorHandlerSupplier.get());
            server.setStopTimeout(100);
            server.setStopAtShutdown(true);

            server.setRequestLog(new CustomRequestLog(REQUEST_LOG_FILE.toString())); // Sets the RequestLog to log to an SLF4J logger named "org.eclipse.jetty.server.RequestLog" at INFO level.

            // Create a ServerConnector to accept connections from clients.
            Connector connector = new ServerConnector(server);
            server.addConnector(connector); // Add the Connector to the Server

            server.addEventListener(new LifeCycle.Listener() {
                @Override
                public void lifeCycleStarted(final LifeCycle event) {
                    state.set(Constants.State.STARTED);
                    log.info("Server started. http://localhost:{}/index.html\t\t{}index.html",
                            server.getURI().getPort(), server.getURI());
                }

                @Override
                public void lifeCycleStopped(LifeCycle event) {
                    state.set(Constants.State.FINISHED);
                    log.info("Server stopped.");
                }
            });
            ContextHandler contextHandler = createContext(new OneServlet(server));
            server.setHandler(contextHandler);

        } catch (Exception e) {
            state.set(Constants.State.FAILED);
            log.error(e.getLocalizedMessage(), e);
            throw new RuntimeException(e);
        }
        state.set(Constants.State.READY);
    }

    protected ContextHandler createContext(Servlet servlet) {
        ServletHolder servletHolder = new ServletHolder(servlet);
        ServletHandler servletHandler = new ServletHandler();
        servletHandler.addServletWithMapping(servletHolder, "/");
        ContextHandler contextHandler = new ServletContextHandler();
        contextHandler.setHandler(servletHandler);
        contextHandler.setBaseResource(CLASS_PATH_RESOURCE);
        contextHandler.setServer(server);
        return contextHandler;
    }

    @SuppressWarnings("BusyWait")
    public Server getServer() {
        if (state.get().equals(Constants.State.STARTING)) {
            int count = 30;
            while (state.get().equals(Constants.State.STARTING) && count != 0) {
                count--;
                try {
                    log.debug(state.get().name());
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    log.error(e.getLocalizedMessage(), e);
                    state.set(Constants.State.FAILED);
                    return null;
                }
            }
        }
        return server;
    }

    public boolean start() {
        if (state.get().equals(Constants.State.FAILED)) {
            return false;
        }
        if (Constants.State.READY.compareTo(state.get()) < 0) {
            return true;
        }

        try {
            getServer().start();
            getServer().join();
        } catch (Exception e) {
            log.error(e.getLocalizedMessage(), e);
        }
        return server.isRunning();
    }

    public void stop() {
        try {
            server.stop();
        } catch (Exception ex) {
            log.warn("Can't stop the Testing server", ex);
        }
    }


    public static final Supplier<Runnable> serveRunnable = () -> () -> {
        Servitude servitude = new Servitude();
        try {
            if (!servitude.start()) {
                throw new RuntimeException("Not running");
            }
        } catch (Exception e) {
            servitude.stop();
            throw new RuntimeException(e);
        }
    };
}
