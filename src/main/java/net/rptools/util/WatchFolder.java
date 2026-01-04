package net.rptools.util;

import net.rptools.data.config.Config;
import net.rptools.data.config.Pref;
import net.rptools.data.Constants;
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
import java.util.function.BiFunction;
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
    private static CompletableFuture<Constants.State> watchFuture;
    private static final AtomicReference<Constants.State> STATE = new AtomicReference<>();
    private static Path WATCH_FOLDER;

    public static final ConcurrentLinkedQueue<Path> queue = new ConcurrentLinkedQueue<>();

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(queue);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.removePropertyChangeListener(listener);
    }

    public static ConcurrentLinkedQueue<Path> getQueue() {
        return queue;
    }

    public void addPathToQueue(Path changedPath) {
        if (!queue.contains(changedPath)) {
            try {
                queue.add(changedPath);
                this.pcs.firePropertyChange("value", queue.size(), changedPath);
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
            stop();
            start();
        }
    }

    private static final Supplier<Constants.State> VALIDATE_FOLDER = () -> {
        if (WATCH_FOLDER == null) {
            throw new RuntimeException("Error: Invalid folder(folder is null)");
        } else if (!WATCH_FOLDER.toFile().exists()) {
            throw new RuntimeException("Error: Invalid folder(folder doesn't exist)");
        } else if (!WATCH_FOLDER.toFile().isDirectory()) {
            throw new RuntimeException("Error: Invalid folder(not a folder)");
        }
        log.info("Watch folder is valid");
        return Constants.State.STARTING;
    };
    private static final Function<Constants.State, Constants.State> CREATE_SERVICE = state -> {
        if (state.equals(Constants.State.FAILED)) {
            return state;
        }
        try {
            watcher = FileSystems.getDefault().newWatchService();
            log.info("WatchService created");
            return state;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    };
    private static final Function<Constants.State, Constants.State> SCAN_FOLDERS = state -> {
        if (state.equals(Constants.State.FAILED)) {
            return state;
        }
        if (PATHS.isEmpty()) {
            log.info("Walking file tree");
            try {
                Phantom phantom = new Phantom(WATCH_FOLDER);
                Throwable throwable = phantom.getThrowable();
                if (throwable == null) {
                    PATHS.addAll(phantom.getPaths());
                } else {
                    throw new RuntimeException(throwable);
                }
                return state;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            return Constants.State.STARTING;
        }
    };
    private static final Function<Constants.State, Constants.State> REGISTER_FOLDERS = state -> {
        if (state.equals(Constants.State.FAILED)) {
            return state;
        }
        log.info("Register folder and sub-directories: {} ...", WATCH_FOLDER);
        try {
            registerFolderRecursive(WATCH_FOLDER);
            log.info("Registrations done.");
            return Constants.State.READY;
        } catch (Exception e) {
            log.error(e.getLocalizedMessage(), e);
            throw new RuntimeException(e);
        }
    };

    private void initialise() {
        if(Pref.getBoolean(Config.WATCH_FOLDER)) {
            STATE.set(Constants.State.STARTING);
            watchFuture = CompletableFuture.supplyAsync(VALIDATE_FOLDER)
                    .handleAsync(handle)
                    .thenApplyAsync(CREATE_SERVICE)
                    .handleAsync(handle)
                    .thenApplyAsync(SCAN_FOLDERS)
                    .handleAsync(handle)
                    .thenApplyAsync(REGISTER_FOLDERS)
                    .handleAsync(handle);
            try {
                STATE.set(watchFuture.get());
            } catch (InterruptedException | ExecutionException e) {
                Utils.whoops(e);
                STATE.set(Constants.State.FAILED);
            }
        } else {
            STATE.set(Constants.State.FAILED);
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
                log.info("register: {}", dir);
            } else {
                if (!dir.equals(prev)) {
                    log.info("update: {} -> {}", prev, dir);
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
                PATHS.addAll(phantom.getPaths());
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
    private final Supplier<Constants.State> PROCESS_EVENTS = () -> {
        while (STATE.get().equals(Constants.State.STARTED) && !KEYS.isEmpty()) {
            // wait for key to be signalled
            WatchKey key;
            try {
                key = watcher.take();
            } catch (ClosedWatchServiceException e) {
                return Constants.State.FINISHED;
            } catch (InterruptedException e) {
                return Constants.State.FAILED;
            }

            Path dir = KEYS.get(key);
            if (dir == null) {
                Utils.whoops(new IllegalArgumentException("Watch Key not recognized."));
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
                } catch (Exception _) {
                }
                addPathToQueue(child);
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
        return Constants.State.FINISHED;
    };

    private static final BiFunction<Constants.State, Throwable, Constants.State> handle = (s, t) -> {
        if (t != null) {
            log.error("Watcher process failed: {}", t.getLocalizedMessage());
            return Constants.State.FAILED;
        } else {
            return s;
        }
    };

    public boolean start() {
        final Constants.State state = STATE.get();
        boolean val;
        switch (state) {
            case STARTED -> val = true;
            case STARTING -> {
                int sensible = 8;
                while (STATE.get().equals(Constants.State.STARTING) && sensible > 0) {
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
                STATE.set(Constants.State.STARTED);
                watchFuture = CompletableFuture.supplyAsync(PROCESS_EVENTS);
                val = start();
            }
            case FINISHED -> {
                // need to recreate the service and register everything again
                watchFuture = CompletableFuture.supplyAsync(() -> state)
                        .thenApplyAsync(CREATE_SERVICE)
                        .handleAsync(handle)
                        .thenApplyAsync(REGISTER_FOLDERS)
                        .handleAsync(handle);
                val = start();
            }
            case null, default -> val = false;
        }
        return val;
    }

    public static void stop() {
        Constants.State state = STATE.get();
        if (state.equals(Constants.State.FAILED) || state.equals(Constants.State.FINISHED) || state.equals(Constants.State.STOPPING)) {
            return;
        }
        log.info("stopping watcher");

        watchFuture.thenApplyAsync(_ -> {
                    try {
                        STATE.set(Constants.State.STOPPING);
                        watcher.close();
                        KEYS.clear();
                        return Constants.State.FINISHED;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .handle(handle)
                .cancel(true);
    }
}






