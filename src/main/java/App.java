import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class App extends Application {

    Stage appFrame;
    ImageView imageView;
    Button startButton;

    String currentDirectory = null;
    Timeline timer;
    Label timerLabel;
    int intervalSeconds = 5;
    int remainingSeconds = intervalSeconds;

    List<Image> seenImages = new ArrayList<>();
    int currentImageIndex = -1;

    /**
     * Initializes the application and makes it visible.
     */
    @Override
    public void start(Stage stage) {
        appFrame = stage;
        appFrame.setTitle("Sketcher");

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");
        root.setTop(initToolbar());
        root.setCenter(initImageArea());
        root.setBottom(initControls());

        Scene scene = new Scene(root, 480, 360);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        appFrame.setScene(scene);
        appFrame.show();
    }

    /**
     * Initializes the toolbar.
     */
    private HBox initToolbar() {
        HBox toolbar = new HBox(8);
        toolbar.getStyleClass().add("toolbar");
        toolbar.setPadding(new Insets(8));

        Button openButton = new Button("Open");
        openButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            if (currentDirectory != null) chooser.setInitialDirectory(new File(currentDirectory));
            File selected = chooser.showDialog(appFrame);
            if (selected != null) {
                currentDirectory = selected.getPath();
                seenImages.clear();
                currentImageIndex = -1;
                imageView.setImage(getNextImage());
                resetTimer();
                timer.play();
            }
        });

        Button helpButton = new Button("Help");

        toolbar.getChildren().addAll(openButton, helpButton);
        return toolbar;
    }

    /**
     * Initializes the image display area.
     */
    private StackPane initImageArea() {
        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        StackPane imageArea = new StackPane(imageView);
        imageArea.getStyleClass().add("image-area");
        imageView.fitWidthProperty().bind(imageArea.widthProperty());
        imageView.fitHeightProperty().bind(imageArea.heightProperty());
        return imageArea;
    }

    /**
     * Initializes the bottom control bar.
     */
    private HBox initControls() {
        timerLabel = new Label(remainingSeconds + "s");

        timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            remainingSeconds--;
            if (remainingSeconds <= 0) {
                imageView.setImage(getNextImage());
                resetTimer();
            } else timerLabel.setText(remainingSeconds + "s");
        }));
        timer.setCycleCount(Timeline.INDEFINITE);

        startButton = new Button("Start/Stop");
        startButton.setOnAction(e -> {
            if (timer.getStatus() == Animation.Status.RUNNING) timer.pause();
            else timer.play();
        });

        Button prevButton = new Button("Previous");
        prevButton.setOnAction(e -> {
            imageView.setImage(getPreviousImage());
            resetTimer();
        });

        Button nextButton = new Button("Next");
        nextButton.setOnAction(e -> {
            imageView.setImage(getNextImage());
            resetTimer();
        });

        // 10s, 30s, 1m, 2m, 5m, 10m
        ComboBox<Integer> intervalDropdown = new ComboBox<>();
        intervalDropdown.getItems().addAll(10, 30, 60, 120, 300, 600);
        intervalDropdown.setValue(intervalSeconds);
        intervalDropdown.setOnAction(e -> {
            intervalSeconds = intervalDropdown.getValue();
            resetTimer();
        });

        HBox controls = new HBox(10, prevButton, startButton, nextButton, timerLabel, intervalDropdown);
        controls.getStyleClass().add("controls");
        controls.setPadding(new Insets(10));
        controls.setAlignment(javafx.geometry.Pos.CENTER);
        return controls;
    }

    /**
     * Resets the timer to the current interval.
     */
    private void resetTimer() {
        remainingSeconds = intervalSeconds;
        timerLabel.setText(remainingSeconds + "s");
    }

    /**
     * Gets the next image from the current directory.
     *
     * @return The next image, or null if there are no more images.
     */
    public Image getNextImage() {
        if (currentDirectory == null) return null;

        // if we arent at the end of seen images, return next in sequence
        if (currentImageIndex < seenImages.size() - 1) {
            currentImageIndex++;
            return seenImages.get(currentImageIndex);
        }

        // if we are at the end of seen images, load a new image
        Image nextImage = ImageLoader.getNextImage(currentDirectory);
        if (nextImage == null) return null;

        seenImages.add(nextImage);
        currentImageIndex = seenImages.size() - 1;
        return nextImage;
    }

    /**
     * Gets the previous image from the seen images.
     *
     * @return The previous image, or null if there are no previous images.
     */
    public Image getPreviousImage() {
        if (seenImages.isEmpty()) return null;
        if (currentImageIndex > 0) currentImageIndex--;
        return seenImages.get(currentImageIndex);
    }
}
