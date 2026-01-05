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
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private static final Executor DELAYED_EXECUTOR = CompletableFuture.delayedExecutor(80, TimeUnit.MILLISECONDS);
    public static final PropertyChangeListener propertyChangeListener = _ -> DELAYED_EXECUTOR.execute(() -> {
        while (!WatchFolder.getQueue().isEmpty()) {
            Path p = WatchFolder.getQueue().poll();
            if (p.toFile().exists()) {
                if (p.toFile().isDirectory()) {
                    PATH_LIST.addAll(new Phantom(p).getPaths());
                } else if (p.getFileName().endsWith(".hbs") && !PATH_LIST.contains(p)) {
                    PATH_LIST.add(p);
                }
            } else {
                PATH_LIST.removeIf(path -> path.startsWith(p));
                PATH_LIST.remove(p);
            }
        }
        PATH_LIST.sort(Comparator.naturalOrder());
        if(PATH_LIST.isEmpty()) {
            buildJson(PATH_LIST.getFirst());
        }
        setWatchChange(true);
        log.info("SheetsObject rebuilt");
    });

    public static boolean setFolder(Path folder_) {
        File f = folder_.toFile();
        loaded = false;
        if (Files.exists(folder_) && Files.isDirectory(folder_)) {
            PATH_LIST.clear();
            folder = folder_;
            if (Pref.getBoolean(Config.LIB_FILE)){
                LibraryJSON.setJsonFilePath(folder_);
            }
            Pref.set(Config.TEMPLATE_FOLDER, folder_.toString());
            json.removeAll();
            json.put("source", folder_.toString());
            PATH_LIST.addAll(new Phantom(folder).getPaths());
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
