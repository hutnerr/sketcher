import javafx.scene.image.Image;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ImageLoader {
    private static String loadedDirectory;
    private static List<File> files;
    private static int index;

    public static Image getNextImage(String directory) {
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

        Image image = new Image(files.get(index++).toURI().toString());
        return image.isError() ? null : image;
    }
}
