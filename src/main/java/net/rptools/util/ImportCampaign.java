package net.rptools.util;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.rptools.data.config.Config;
import net.rptools.data.config.Pref;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Iterator;
import java.util.List;
import java.util.zip.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.nio.file.Path;

import static net.rptools.data.Constants.*;

@SuppressWarnings("SpellCheckingInspection")
public class ImportCampaign {
private static final Logger log = LoggerFactory.getLogger(ImportCampaign.class);
    private static final JFileChooser fc = new JFileChooser(Pref.getPath(Config.TEMPLATE_FOLDER).toFile());

    private static final String FILE_NAME = "content.xml";
    private static Component parent = null;

    static {
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setMultiSelectionEnabled(false);
        fc.setDialogTitle("Select Campaign to Import");
        fc.addChoosableFileFilter(new FileNameExtensionFilter("MapTool Files", "mtprops", "cmpgn"));
        fc.addChoosableFileFilter(new FileNameExtensionFilter("Json Files", "json"));
        fc.setAcceptAllFileFilterUsed(false);
    }

    public static Path chooseCampaignFile() {
        if (fc.showDialog(parent, "Open") == JFileChooser.APPROVE_OPTION) {
            return fc.getSelectedFile().getAbsoluteFile().toPath();
        }
        return null;
    }

    public static void importProps(Component parent) {
        ImportCampaign.parent = parent;
        Path path = chooseCampaignFile();
        ObjectNode out = null;
        if (path != null) {
            if (path.endsWith("json")) {
                try {
                    out = (ObjectNode) OBJECT_MAPPER.readTree(path.toFile());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                out = unzip(path);
            }
        }

        if (out != null && !out.isEmpty()) {
            ObjectNode defaultObject = Pref.getObjectNode(Config.DATASETS + "/Default");
            final ObjectNode output = out;
            out.fieldNames().forEachRemaining(name ->
                    Pref.getObjectNode(Config.DATASETS)
                            .set(name, defaultObject.deepCopy().set("properties", output.get(name))));
        }
        log.info("Import successful");
    }

    // Method to unzip files
    public static ObjectNode unzip(Path path) {
        log.info(path.toString());
        ObjectNode on = OBJECT_MAPPER.createObjectNode();
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            ZipEntry entry;
            Iterator<? extends ZipEntry> iterator = zipFile.entries().asIterator();
            while ((entry = iterator.next()) != null) {
                if (entry.getName().equalsIgnoreCase(FILE_NAME)) {
                    break;
                }
            }
            if (entry != null) {
                try (InputStream inputStream = zipFile.getInputStream(entry)) {
                    Document document = Jsoup.parse(inputStream, "UTF-8", "", Parser.xmlParser());
                    document.getElementsByTag("tokenTypeMap");
                    String name = document.getElementsByTag("tokenTypeMap").getFirst().getElementsByTag("entry").asList().getFirst().getElementsByTag("string").getFirst().nodeValue();
                    ArrayNode an = OBJECT_MAPPER.createArrayNode();
                    List<Element> props = document.getElementsByTag("tokenTypeMap").getFirst().getElementsByTag("entry").asList().getFirst().getElementsByTag("list").getFirst().children().asList();
                    for(Element prop: props){
                        an.add(processPropertyNode(prop));
                    }
                    on.set(name, an);
                } catch (Exception e) {
                    log.error(e.getLocalizedMessage(), e);
                    throw new RuntimeException(e);
                }
            }
        } catch (Exception e) {
            log.error(e.getLocalizedMessage(), e);
            throw new RuntimeException(e);
        }
        return on;
    }

    private static final List<String> PROPERTY_NAMES = List.of("name", "shortName", "displayName", "value", "gmOnly", "ownerOnly");

    private static ObjectNode processPropertyNode(Element element) {
        ObjectNode on = OBJECT_MAPPER.createObjectNode();
        PROPERTY_NAMES.forEach(propName -> {
            Elements els = element.getElementsByTag(propName);
            if (els.size() == 1) {
                String value = els.getFirst().nodeValue();
                if (propName.equalsIgnoreCase("gmOnly") || propName.equalsIgnoreCase("ownerOnly")) {
                    on.set(propName, OBJECT_MAPPER.getNodeFactory().booleanNode(Boolean.parseBoolean(value)));
                } else {
                    on.set(propName, OBJECT_MAPPER.getNodeFactory().textNode(value));
                }
            } else {
                if (propName.equalsIgnoreCase("gmOnly") || propName.equalsIgnoreCase("ownerOnly")) {
                    on.set(propName, OBJECT_MAPPER.getNodeFactory().booleanNode(false));
                } else {
                    on.set(propName, OBJECT_MAPPER.getNodeFactory().nullNode());
                }
            }
        });
        return on;
    }
}