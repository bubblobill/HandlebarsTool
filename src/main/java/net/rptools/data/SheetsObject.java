package net.rptools.data;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.rptools.data.config.Config;
import net.rptools.data.config.Pref;
import net.rptools.util.Phantom;
import net.rptools.util.WatchFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeListener;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.*;
import static net.rptools.data.Constants.OBJECT_MAPPER;

public class SheetsObject {
    private static final Logger log = LoggerFactory.getLogger(SheetsObject.class);
    private static final ObjectNode json = OBJECT_MAPPER.createObjectNode();
    private static String sheet = Pref.getString(Config.SHEET);
    private static Path folder = Pref.getPath(Config.TEMPLATE_FOLDER);
    private static boolean loaded = false;
    private static final List<Path> PATH_LIST = Collections.synchronizedList(new ArrayList<>());

    private static final AtomicBoolean WATCH_CHANGE = new AtomicBoolean(false);

    static {
        PATH_LIST.add(folder);
    }

    public static void setWatchChange(boolean value) {
        WATCH_CHANGE.set(value);
    }

    public static boolean getWatchChange() {
        return WATCH_CHANGE.get();
    }

    private static final Executor DELAYED_EXECUTOR = CompletableFuture.delayedExecutor(110, TimeUnit.MILLISECONDS);
    public static final PropertyChangeListener propertyChangeListener = e -> DELAYED_EXECUTOR.execute(() -> {
        log.info("Watch change PCL start");
        boolean rebuild = false;
        boolean notify = false;
        Queue<Path> queue = WatchFolder.getQueue(e.getPropertyName());
        if (queue == null) {
            return;
        }
        while (!queue.isEmpty()) {
            Path qPath = queue.poll();
            if (qPath == null) {
                continue;
            }
            notify = true;
            if (e.getPropertyName().equalsIgnoreCase(ENTRY_DELETE.name())) {
                PATH_LIST.remove(qPath);
                rebuild = true;
            } else if (e.getPropertyName().equalsIgnoreCase(ENTRY_CREATE.name())) {
                if (qPath.getFileName().endsWith(".hbs") && !PATH_LIST.contains(qPath)) {
                    PATH_LIST.add(qPath);
                    rebuild = true;
                }
            } // else ENTRY_MODIFY - unused
        }
        if (rebuild) {
            PATH_LIST.remove(folder);
            PATH_LIST.sort(Comparator.naturalOrder());
            PATH_LIST.addFirst(folder);
            buildJson(PATH_LIST.getFirst());
            log.info("SheetsObject rebuilt");
        }

        setWatchChange(notify || getWatchChange());
        log.info("Watch change PCL start");
    });

    public static boolean setFolder(Path folder_) {
        loaded = false;
        if (Files.exists(folder_) && Files.isDirectory(folder_)) {
            PATH_LIST.clear();
            folder = folder_;
            if (Pref.getBoolean(Config.LIB_FILE)) {
                LibraryJSON.setJsonFilePath(folder_);
            }
            Pref.set(Config.TEMPLATE_FOLDER, folder_.toString());
            json.removeAll();
            json.put("source", folder_.toString());
            PATH_LIST.addAll(new Phantom(folder).getHandlebarsPaths());
            buildJson(folder_);
        }
        return loaded;
    }

    private static void buildJson(Path folder_) {
        if (!PATH_LIST.isEmpty()) {
            ArrayNode arrayNode = OBJECT_MAPPER.createArrayNode();
            json.set("sheets", arrayNode);
            for (Path path : PATH_LIST) {
                ObjectNode sheet = OBJECT_MAPPER.createObjectNode();
                if (SheetsObject.sheet == null) {
                    SheetsObject.sheet = path.getFileName().toString();
                    Pref.set(Config.SHEET, SheetsObject.sheet);
                }
                sheet.put("name", path.getFileName().toString().replaceAll(".hbs", ""));
                sheet.put("entry", folder_.relativize(path).toString());
                sheet.put("description", "");
                sheet.set("propertyTypes", OBJECT_MAPPER.createArrayNode());
                arrayNode.add(sheet);
            }
            if (Pref.getBoolean(Config.LIB_FILE)) {
                LibraryJSON.addSheets(arrayNode);
            }
        }
        json.put(Config.SHEET, sheet);
        loaded = true;
    }

    public static ObjectNode getJson() {
        if (!loaded) {
            if (!setFolder(folder)) {
                return OBJECT_MAPPER.createObjectNode();
            }
        }
        return json;
    }

}
