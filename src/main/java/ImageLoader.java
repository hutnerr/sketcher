import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;

public class ImageLoader {
    private static String loadedDirectory;
    private static List<File> files;
    private static int index;

    public static BufferedImage getNextImage(String directory) {
        if (!directory.equals(loadedDirectory)) {
            loadedDirectory = directory;
            files = new ArrayList<>(Arrays.asList(new File(directory).listFiles((dir, name) -> {
                String lower = name.toLowerCase();
                return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
            })));
            Collections.shuffle(files);
            index = 0;
        }

        if (index >= files.size()) return null;

        try { return ImageIO.read(files.get(index++)); }
        catch (IOException e) { return null; }
    }
}
