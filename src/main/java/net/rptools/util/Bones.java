package net.rptools.util;

import net.rptools.data.Config;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.*;

public class Bones {
    private static final Logger log = LoggerFactory.getLogger(Bones.class);
    private static File tempFile = null;
    private static Path sheetsFolder;
    private static final JFileChooser FC = new JFileChooser(Config.getString(Config.TEMPLATE_FOLDER));
    private static final List<String> FILE_LIST = Arrays.stream(new String[]{
            "/data/library/public/sheets/attributions.txt",
            "/data/library/public/sheets/copyright.txt",
            "/data/library/public/sheets/sampleImportable.json",
            "/data/library/public/sheets/Simple.hbs",
            "/data/library/public/sheets/Sheet.hbs",
            "/data/library/public/sheets/js/base.js",
            "/data/library/public/sheets/css/mt-stat-sheet.css",
            "/data/library/public/sheets/css/sheet.css",
            "/data/library/public/sheets/css/generic.css",
            "/data/library/public/sheets/image/handout.gif",
            "/data/library/public/sheets/image/image.gif",
            "/data/library/public/sheets/image/portrait.gif",
            "/data/library/public/sheets/image/sparkle-glitter-small.gif"
    }).toList();

    private static final String structure = """
            <html>
            <h2>This will create a new folder for<br/>
             holding Handlebars templates with<br/>the following structure.</h2>
            <div style="border:1px solid red; padding:8px; font-size:14pt;">
            <pre>
            root
             └─ library
                └─ public
                   └─ sheets
                      ├─ image
                      │  ├─ handout.png
                      │  ├─ image.png
                      │  ├─ portrait.png
                      │  └─ sparkle-glitter-small.gif
                      ├─ js
                      │  └─ base.js
                      ├─ css
                      │  ├─ mt-stat-sheet.css
                      │  ├─ generic.css
                      │  └─ sheet.css
                      ├─ attributions.txt
                      ├─ copyright.txt
                      ├─ sampleImportable.json
                      ├─ Simple.hbs
                      └─ Sheet.hbs
            </pre></div>
            <h2 color="blue" style="width:100%; text-align: center;">Select Root Folder?</h2>
            </html>
            """;

    static {
        FC.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        FC.setMultiSelectionEnabled(false);
        FC.setDialogTitle("Choose the Root Directory");

        try {
            String content = IOUtils.resourceToString("/data/library/public/sheets/copyright.txt", StandardCharsets.UTF_8);
            content = MessageFormat.format(content, System.getProperty("user.name"), LocalDateTime.now().getYear());
            tempFile = Files.createTempFile("copyright", ".txt").toAbsolutePath().toFile();
            IOUtils.write(content, new FileWriter(tempFile));
        } catch (IOException e) {
            Utils.whoops(e);
            log.info(e.getLocalizedMessage(), e);
        }
    }

    public static boolean createBones() {
        if (Utils.prompt(null, structure)) {
            if(FC.showDialog(null, "Select Folder") == JFileChooser.APPROVE_OPTION) {
                Path rootFolder = FC.getSelectedFile().toPath();
                if (Utils.prompt(null, String.format("Create folder structure in:\n %s", rootFolder.toAbsolutePath()))) {
                    for (String filePath : FILE_LIST) {
                        try {
                            Path file = Paths.get(rootFolder.toAbsolutePath().toString(), filePath.substring(5));
                            Path folder = file.getParent();
                            Files.createDirectories(folder);
                            Files.deleteIfExists(file);
                            URI uri;
                            if (filePath.equalsIgnoreCase("/data/library/public/sheets/copyright.txt")) {
                                uri = tempFile.toURI();
                            } else {
                                URL url = Bones.class.getResource(filePath);
                                if (url == null) {
                                    continue;
                                }
                                uri = url.toURI();
                            }
                            byte[] fileData = Files.readAllBytes(Path.of(uri));
                            Files.write(file, fileData);
                        } catch (URISyntaxException | SecurityException | IOException e) {
                            Utils.whoops(e);
                            log.info(e.getLocalizedMessage(), e);
                            return false;
                        }
                    }
                    sheetsFolder = Paths.get(rootFolder.toAbsolutePath().toString(), "library", "public", "sheets");
                    return true;
                }
            }
        }
        return false;
    }

    public static Path getSheetsFolder() {
        return sheetsFolder;
    }

}
