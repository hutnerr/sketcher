import javax.swing.*;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

public class ImagePanel extends JPanel {
    private BufferedImage image;

    public void setImage(BufferedImage image) {
        this.image = image;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image == null) return;

        double scale = Math.min((double) getWidth() / image.getWidth(), (double) getHeight() / image.getHeight());
        int width = (int) (image.getWidth() * scale);
        int height = (int) (image.getHeight() * scale);
        int x = (getWidth() - width) / 2;
        int y = (getHeight() - height) / 2;
        g.drawImage(image, x, y, width, height, this);
    }
}

