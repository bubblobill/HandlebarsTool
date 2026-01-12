package net.rptools.util;

import net.rptools.data.config.Config;
import net.rptools.data.config.Pref;

import static net.rptools.data.Constants.*;
import static net.rptools.data.Constants.State.*;

import org.apache.commons.lang3.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * <a href="https://dev.java/learn/java-io/file-system/watching-dir-changes/">From</a>
 */

public class WatchFolder {
    private static final Logger log = LoggerFactory.getLogger(WatchFolder.class);
    private static WatchService watcher = null;
    private static final Map<WatchKey, Path> KEYS = new HashMap<>();
    private static final List<Path> PATHS = new ArrayList<>();
    private static boolean trace;
    private static CompletableFuture<State> watchFuture;
    private static final AtomicReference<State> STATE = new AtomicReference<>();
    private static Path WATCH_FOLDER;

    public static final ConcurrentLinkedQueue<Path> addQueue = new ConcurrentLinkedQueue<>();
    public static final ConcurrentLinkedQueue<Path> modifyQueue = new ConcurrentLinkedQueue<>();
    public static final ConcurrentLinkedQueue<Path> removeQueue = new ConcurrentLinkedQueue<>();

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(KEYS);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.removePropertyChangeListener(listener);
    }

    public static ConcurrentLinkedQueue<Path> getQueue(String kind) {
        if(kind.equalsIgnoreCase(ENTRY_CREATE.name())) {
            return addQueue;
        } else if(kind.equalsIgnoreCase(ENTRY_MODIFY.name())) {
            return modifyQueue;
        } if(kind.equalsIgnoreCase(ENTRY_DELETE.name())) {
            return removeQueue;
        }
        return null;
    }


    public void addPathToQueue(final WatchEvent.Kind<?> kind, Path changedPath) {
        Queue<Path> queue = null;
        if(kind == ENTRY_CREATE){
            queue = addQueue;
        } else if(kind == ENTRY_DELETE){
            queue = removeQueue;
        } else if(kind == ENTRY_MODIFY){
            queue = modifyQueue;
        }
        if(queue != null && !queue.contains(changedPath)) {
            try {
                queue.add(changedPath);
                log.debug("Watcher PCS: fire event");
                this.pcs.firePropertyChange(kind.name(), queue.size(), changedPath);
            } catch (Exception e) {
                log.error(e.getLocalizedMessage(), e);
            }
        }
    }


    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>) event;
    }


    /**
     * Creates a WatchService and registers the given directory
     */
    public WatchFolder(Path folder) {
        if (WATCH_FOLDER == null || !WATCH_FOLDER.equals(folder)) {
            WATCH_FOLDER = folder;
            initialise();
        } else {
//            stop();
            start();
        }
    }

    private static final Supplier<State> VALIDATE_FOLDER = () -> {
        if (WATCH_FOLDER == null) {
            log.error("Error: Invalid folder(folder is null)");
            return FAILED;
        } else if (!Files.exists(WATCH_FOLDER)) {
            log.error("Error: Invalid folder(folder doesn't exist)");
            return FAILED;
        } else if (!Files.isDirectory(WATCH_FOLDER)) {
            log.error("Error: Invalid folder(not a folder)");
            return FAILED;
        } else if (!Files.isReadable(WATCH_FOLDER)) {
            log.error("Error: Invalid folder(no read access)");
            return FAILED;
        }
        log.debug("Watch folder is valid");
        return STARTING;
    };
    private static final Function<State, State> CREATE_SERVICE = state -> {
        if (state.equals(FAILED)) {
            return state;
        }
        try {
            watcher = FileSystems.getDefault().newWatchService();
            log.info("WatchService created");
            return state;
        } catch (IOException e) {
            log.error("Error creating WatchService: {}", e.getLocalizedMessage(), e);
            return FAILED;
        }
    };
    private static final Function<State, State> SCAN_FOLDERS = state -> {
        if (state.equals(FAILED)) {
            return state;
        }
        if (PATHS.isEmpty()) {
            log.info("Walking file tree");
            try {
                Phantom phantom = new Phantom(WATCH_FOLDER);
                Throwable throwable = phantom.getThrowable();
                if (throwable == null) {
                    PATHS.addAll(phantom.getFolderPaths());
                } else {
                    throw new RuntimeException(throwable);
                }
                return state;
            } catch (Exception e) {
                log.error("Error scanning folders: {}", e.getLocalizedMessage(), e);
                return FAILED;
            }
        } else {
            return State.STARTING;
        }
    };
    private static final Function<State, State> REGISTER_FOLDERS = state -> {
        if (state.equals(FAILED)) {
            return state;
        }
        log.info("Registering folder and sub-directories: {} ...", WATCH_FOLDER);
        try {
            registerFolderRecursive(WATCH_FOLDER);
            log.info("Registrations done.");
            return State.READY;
        } catch (Exception e) {
            log.error("Error registering folder: {}", e.getLocalizedMessage(), e);
            return FAILED;
        }
    };

    private void initialise() {
        if (Pref.getBoolean(Config.WATCH_FOLDER)) {
            STATE.set(State.STARTING);
            watchFuture = CompletableFuture.supplyAsync(VALIDATE_FOLDER)
                    .thenApplyAsync(CREATE_SERVICE)
                    .thenApplyAsync(SCAN_FOLDERS)
                    .thenApplyAsync(REGISTER_FOLDERS);
            try {
                STATE.set(watchFuture.get());
            } catch (InterruptedException | ExecutionException e) {
                Alerts.whoops(e);
                STATE.set(FAILED);
            }
        } else {
            STATE.set(FAILED);
        }
    }


    /**
     * Register the given directory with the WatchService
     */
    private static void register(Path dir) throws Exception {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        if (trace) {
            Path prev = KEYS.get(key);
            if (prev == null) {
                log.debug("register: {}", dir);
            } else {
                if (!dir.equals(prev)) {
                    log.debug("update: {} -> {}", prev, dir);
                }
            }
        }
        KEYS.put(key, dir);
    }

    /**
     * Register the given directory, and all its sub-directories, with the
     * WatchService.
     */
    private static void registerFolderRecursive(final Path start) throws Exception {
        try {
            trace = false;
            register(start);
            trace = true; // enable trace after initial registration
            // sub-directories
            Phantom phantom = new Phantom(start);
            Throwable throwable = phantom.getThrowable();
            if (throwable == null) {
                PATHS.addAll(phantom.getFolderPaths());
                PATHS.removeIf(p -> PATHS.stream().filter(p::equals).count() > 1);
                PATHS.sort(Comparator.naturalOrder());
            }
            for (Path path : PATHS) {
                if (path.toFile().isDirectory()) {
                    register(path);
                }
            }
        } catch (Exception e) {
            log.error(e.getLocalizedMessage(), e);
            throw e;
        }
    }


    /**
     * Process all events for keys queued to the watcher
     */
    private final Supplier<State> PROCESS_EVENTS = () -> {
        while (STATE.get().equals(STARTED) && !KEYS.isEmpty()) {
            // wait for key to be signalled
            WatchKey key;
            try {
                key = watcher.take();
            } catch (ClosedWatchServiceException e) {
                return FINISHED;
            } catch (InterruptedException e) {
                return FAILED;
            }

            Path dir = KEYS.get(key);
            if (dir == null) {
                Alerts.whoops(new IllegalArgumentException("Watch Key not recognized."));
                log.error("Watch Key not recognized.");
                KEYS.remove(key);
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == OVERFLOW) {
                    continue;
                }

                // Context for directory entry event is the file name of entry
                WatchEvent<Path> ev = cast(event);
                Path name = ev.context();
                Path child = dir.resolve(name);

                // print out event
                log.debug("WatchEvent - {}: {}", event.kind().name(), child);

                // if directory is created, and watching recursively, then
                // register it and its sub-directories
                try {
                    if (kind == ENTRY_CREATE) {
                        if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                            CompletableFuture.supplyAsync(() -> {
                                        try {
                                            registerFolderRecursive(child);
                                            return true;
                                        } catch (Exception e) {
                                            throw new RuntimeException(e);
                                        }
                                    })
                                    .handle((_, t) -> t == null);
                        }
                    }
                    addPathToQueue(kind, child);

                } catch (Exception _) {
                }

                // reset key and remove from set if directory no longer accessible
                boolean valid = key.reset();
                if (!valid) {
                    KEYS.remove(key);
                    // all directories are inaccessible
                    if (KEYS.isEmpty()) {
                        break;
                    }
                }
            }
        }
        return FINISHED;
    };

    public boolean start() {
        final State state = STATE.get();
        boolean val;
        switch (state) {
            case STARTED -> val = true;
            case STARTING -> {
                int sensible = 8;
                while (STATE.get().equals(State.STARTING) && sensible > 0) {
                    sensible--;
                    try {
                        ThreadUtils.sleep(Duration.ofMillis(300));
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                val = start();
            }
            case READY -> {
                STATE.set(State.STARTED);
                watchFuture = CompletableFuture.supplyAsync(PROCESS_EVENTS);
                val = start();
            }
            case FINISHED -> {
                // need to recreate the service and register everything again
                watchFuture = CompletableFuture.supplyAsync(() -> state)
                        .thenApplyAsync(CREATE_SERVICE)
                        .thenApplyAsync(REGISTER_FOLDERS);
                val = start();
            }
            case null, default -> val = false;
        }
        return val;
    }

    public static void stop() {
        State state = STATE.get();
        if (state.equals(FAILED) || state.equals(State.FINISHED) || state.equals(STOPPING)) {
            return;
        }
        log.info("stopping watcher");

        watchFuture.thenApplyAsync(_ -> {
                    try {
                        STATE.set(STOPPING);
                        watcher.close();
                        KEYS.clear();
                        return State.FINISHED;
                    } catch (Exception e) {
                        return STOPPING;
                    }
                })
                .cancel(true);
    }
}






