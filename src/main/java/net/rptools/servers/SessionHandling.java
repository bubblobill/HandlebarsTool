package net.rptools.servers;

import org.eclipse.jetty.server.session.*;

import javax.servlet.SessionTrackingMode;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.UUID;

public class SessionHandling {
    private static final File tempFolder;
    static {
        File file;
        try {
            file = Files.createTempDirectory("Sheets-" + UUID.randomUUID()).toAbsolutePath().toFile();
            file.deleteOnExit();
        } catch (IOException _) {
            file = null;
        }
        tempFolder = file;

    }
    public static SessionHandler fileSessionHandler() {
        SessionHandler sessionHandler = new SessionHandler();
        SessionCache sessionCache = new DefaultSessionCache(sessionHandler);
        sessionCache.setSessionDataStore(fileSessionDataStore());
        sessionCache.setSaveOnCreate(true);
        sessionCache.setFlushOnResponseCommit(true);

        sessionHandler.setSessionCache(sessionCache);
        sessionHandler.setHttpOnly(true);
        sessionHandler.setSessionTrackingModes(Set.of(SessionTrackingMode.URL, SessionTrackingMode.COOKIE));

        // make additional changes to your SessionHandler here
        return sessionHandler;
    }

    public static FileSessionDataStore fileSessionDataStore() {
        FileSessionDataStore fileSessionDataStore = new FileSessionDataStore();
        fileSessionDataStore.setStoreDir(tempFolder);
        return fileSessionDataStore;
    }
    public static File getTempFolder(){
        return tempFolder;
    }
}
