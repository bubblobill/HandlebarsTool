package net.rptools.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

public class Displacement {
    public static void main(String[] args){
        double increment = Math.PI / 512;
        List<Float> values = new ArrayList<>();

        for (int i = 0; i < 256; i++) {
            values.add((float) Math.sin(increment * i));
        }

        BufferedImage bi = new BufferedImage(256, 256, BufferedImage.TYPE_3BYTE_BGR);
        for (int r = 0; r < 256; r++) {
            float red = 1 - values.get(r);
            for (int g = 0; g < 256; g++) {
                bi.setRGB(r, g, new Color(red, 1 - values.get(g), 0f).getRGB());
            }
        }

        File outputfile = Path.of("C:/Users/Strat/Desktop/Sheets/image/displacement.gif").toAbsolutePath().toFile();

        try {
            ImageIO.write(bi, "gif", outputfile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
