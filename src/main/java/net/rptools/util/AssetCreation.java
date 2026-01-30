package net.rptools.util;

import net.rptools.data.Constants;
import net.rptools.data.config.Config;
import net.rptools.data.config.Pref;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static net.rptools.data.Constants.DEFAULT_IMAGE_NAME;

public class AssetCreation {
    private static final Logger log = LoggerFactory.getLogger(AssetCreation.class);
    private final Path pathFrom;
    private Path pathTo;
    private final String relativePrefix;

    public record ImageDetails(String location, double aspectRatio) { }

    private final Map<String, ImageDetails> pathMap = new HashMap<>();

    public AssetCreation(Path pathFrom, Path defaultImagePath) {
        this.pathFrom = pathFrom;
        pathTo = Pref.getPath(Config.ASSETS_FOLDER);
        Path templatePath = Pref.getPath(Config.TEMPLATE_FOLDER);
        if (!Files.exists(pathTo)) {
            try (Stream<Path> destinations = Files.list(templatePath)) {
                List<Path> alternatives = destinations.filter(path -> {
                    String fileName = path.getFileName().toString();
                    for (String folderName : new String[]{"Image", "Img", "Pic"}) {
                        if (fileName.contains(folderName) ||
                                fileName.contains(folderName.toLowerCase()) ||
                                fileName.contains(folderName.toUpperCase())) {
                            return true;
                        }
                    }
                    return false;
                }).toList();
                if (!alternatives.isEmpty()) {
                    pathTo = alternatives.getFirst();
                } else {
                    pathTo = Files.createDirectory(templatePath.resolve("image"));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }
        relativePrefix = "./" + pathTo.getFileName().toString() + "/";
        if (!Files.exists(pathTo)) {
            try {
                Files.createDirectories(pathTo);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        String suffix = defaultImagePath.getFileName().toString();
        suffix = suffix.substring(suffix.indexOf('.'));
        try {
            Files.copy(defaultImagePath, pathTo.resolve(DEFAULT_IMAGE_NAME + suffix), StandardCopyOption.REPLACE_EXISTING);
            pathMap.put(DEFAULT_IMAGE_NAME, new ImageDetails(relativePrefix + "defaultImage" + suffix, 1));
        } catch (IOException e) {
            log.error(e.getLocalizedMessage(), e);
        }
    }

    public AssetCreation loadResources(List<String> assetList) {
        try (ZipFile zipFile = new ZipFile(pathFrom.toFile())) {

            Map<String, String> fileNameLookup = new HashMap<>();

            Iterator<? extends ZipEntry> iterator = zipFile.entries().asIterator();

            while (iterator.hasNext()) {
                ZipEntry entry = iterator.next();
                String entryName = entry.getName();
                if (entryName.startsWith("assets/")) {
                    String lookupValue = entry.getName().substring(7);
                    if (assetList.contains(lookupValue)) {
                        try (InputStream inputStream = zipFile.getInputStream(entry)) {
                            Document document = Jsoup.parse(inputStream, "UTF-8", "", Parser.xmlParser());
                            Element assetDetails = document.getElementsByTag("net.rptools.maptool.model.Asset").asList().getFirst();

                            String name = assetDetails.getElementsByTag("name").asList().getFirst().text();
                            String suffix = assetDetails.getElementsByTag("extension").asList().getFirst().text();
                            String fileNameOut = String.format("%s.%s", name, suffix);
                            fileNameLookup.put(String.format("%s.%s", entryName, suffix), fileNameOut);
                        }
                    }
                }
            }

            Set<String> lookingFor = fileNameLookup.keySet();

            final Iterator<? extends ZipEntry> it = zipFile.entries().asIterator();

            while (it.hasNext()) {
                ZipEntry entry = it.next();
                if (lookingFor.contains(entry.getName())) {
                    int height, width;
                    double aspectRatio;
                    String id = entry.getName().replace("assets/", "").substring(0, 32);
                    Path pathOut = pathTo.resolve(fileNameLookup.get(entry.getName()));

                    try (InputStream zis = zipFile.getInputStream(entry)) {
                        Files.copy(zis, pathOut.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
                        BufferedImage bi = ImageIO.read(pathOut.toAbsolutePath().toFile());
                        height = bi.getHeight();
                        width = bi.getWidth();
                        aspectRatio = (float) width / height;
                        pathMap.put(id, new ImageDetails(relativePrefix + pathOut.getFileName().toString(), aspectRatio));
                    } catch (IOException e) {
                        log.error(e.getLocalizedMessage(), e);
                    }
                }
            }
        } catch (IOException e) {
            log.error(e.getLocalizedMessage(), e);
        }

        return this;
    }

    public Map<String, ImageDetails> getPathMap() {
        return pathMap;
    }
}
