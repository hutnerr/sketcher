import javax.swing.*;
import java.awt.FlowLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.awt.image.BufferedImage;

public class App {

    JFrame appFrame;
    JMenuBar menuBar;
    ImagePanel imagePanel;
    JButton startButton;

    String currentDirectory = null;
    Timer timer;
    JLabel timerLabel;
    int intervalSeconds = 5;
    int remainingSeconds = intervalSeconds;

    List<BufferedImage> seenImages = new ArrayList<>();
    int currentImageIndex = -1;

    /**
     * Initializes the application and makes it visible.
     */
    public void run() {
        initFrame();
        initMenuBar();
        initActionBar();
        initPicturePanel();
        appFrame.setVisible(true);
    }

    /**
     * Initializes the application frame.
     */
    private void initFrame() {
        appFrame = new JFrame("Sketcher");
        appFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        appFrame.setSize(400, 300);
    }

    /**
     * Initializes the application menu bar.
     */
    private void initMenuBar() {
        menuBar = new JMenuBar();
        menuBar.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
        appFrame.setJMenuBar(menuBar);

        // JMenu fileMenu = new JMenu("File");
        // menuBar.add(fileMenu);

        JMenuItem openItem = new JMenuItem("Open");
        openItem.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (currentDirectory != null) fileChooser.setCurrentDirectory(new File(currentDirectory));
            int returnValue = fileChooser.showOpenDialog(appFrame);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                currentDirectory = fileChooser.getSelectedFile().getPath();
                seenImages.clear();
                currentImageIndex = -1;
                imagePanel.setImage(getNextImage());
                resetTimer();
                timer.start();
            }
        });
        menuBar.add(openItem);

        JMenuItem helpMenu = new JMenuItem("Help");
        menuBar.add(helpMenu);
    }

    private void initActionBar() {
        timerLabel = new JLabel(remainingSeconds + "s");

        timer = new Timer(1000, e -> {
            remainingSeconds--;
            if (remainingSeconds <= 0) {
                imagePanel.setImage(getNextImage());
                resetTimer();
            } else timerLabel.setText(remainingSeconds + "s");
        });
        timer.setRepeats(true);

        JButton startButton = new JButton("Start/Stop");
        startButton.addActionListener(e -> {
            if (timer.isRunning()) timer.stop();
            else timer.start();
        });

        JButton prevButton = new JButton("Previous");
        prevButton.addActionListener(e -> {
            imagePanel.setImage(getPreviousImage());
            resetTimer();
        });

        JButton nextButton = new JButton("Next");
        nextButton.addActionListener(e -> {
            imagePanel.setImage(getNextImage());
            resetTimer();
        });

        // 10s, 30s, 1m, 2m, 5m, 10m
        Integer[] intervals = { 10, 30, 60, 120, 300, 600 };
        JComboBox<Integer> intervalDropdown = new JComboBox<>(intervals);
        intervalDropdown.setSelectedItem(intervalSeconds);
        intervalDropdown.addActionListener(e -> {
            intervalSeconds = (Integer) intervalDropdown.getSelectedItem();
            resetTimer();
        });

        JPanel actionPanel = new JPanel();
        actionPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 10));
        actionPanel.add(prevButton);
        actionPanel.add(startButton);
        actionPanel.add(nextButton);
        actionPanel.add(timerLabel);
        actionPanel.add(intervalDropdown);
        appFrame.add(actionPanel, java.awt.BorderLayout.SOUTH);
    }

    /**
     * Initializes the picture panel.
     */
    private void initPicturePanel() {
        imagePanel = new ImagePanel();
        appFrame.add(imagePanel, java.awt.BorderLayout.CENTER);
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
    public BufferedImage getNextImage() {
        if (currentDirectory == null) return null;

        // if we arent at the end of seen images, return next in sequence
        if (currentImageIndex < seenImages.size() - 1) {
            currentImageIndex++;
            return seenImages.get(currentImageIndex);
        }

        // if we are at the end of seen images, load a new image
        BufferedImage nextImage = ImageLoader.getNextImage(currentDirectory);
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
    public BufferedImage getPreviousImage() {
        if (seenImages.isEmpty()) return null;
        if (currentImageIndex > 0) currentImageIndex--;
        return seenImages.get(currentImageIndex);
    }
}
