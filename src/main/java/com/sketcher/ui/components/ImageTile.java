package com.sketcher.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

/**
 * One square thumbnail in an image grid (Gallery, Playlists): the whole
 * picture fitted (never cropped — composition matters for reference images)
 * with its own corners rounded, centered in a card-like tile with a
 * selection ring, an optional favorite badge, and an optional corner overlay.
 */
public class ImageTile extends StackPane {
    private static final double INSET = 10;
    private static final double RADIUS = 8;

    public ImageTile(Image thumbnail, double size) {
        getStyleClass().add("image-tile");
        setPrefSize(size, size);
        setMinSize(size, size);
        setMaxSize(size, size);

        double inner = size - INSET * 2;
        ImageView imageView = new ImageView(thumbnail);
        imageView.setFitWidth(inner);
        imageView.setFitHeight(inner);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        // Clip to the image's actual fitted size (not the square), so a
        // landscape or portrait picture gets rounded corners of its own.
        // Thumbnails decode in the background, so the size is only known once
        // the image reports its dimensions — until then, clip to the square.
        Rectangle clip = new Rectangle(inner, inner);
        clip.setArcWidth(RADIUS * 2);
        clip.setArcHeight(RADIUS * 2);
        imageView.setClip(clip);
        if (thumbnail != null) {
            Runnable fit = () -> fitClip(clip, thumbnail, inner);
            thumbnail.widthProperty().addListener((o, a, b) -> fit.run());
            thumbnail.heightProperty().addListener((o, a, b) -> fit.run());
            fit.run();
        }

        getChildren().add(imageView);
    }

    private static void fitClip(Rectangle clip, Image image, double box) {
        if (image.getWidth() <= 0 || image.getHeight() <= 0) return;
        double scale = Math.min(box / image.getWidth(), box / image.getHeight());
        clip.setWidth(image.getWidth() * scale);
        clip.setHeight(image.getHeight() * scale);
    }

    public ImageTile favorite(boolean favorite) {
        if (!favorite) return this;
        Label badge = new Label("★");
        badge.getStyleClass().add("image-tile-badge");
        overlay(badge, Pos.TOP_RIGHT);
        return this;
    }

    /** Pins a node (a remove button, a badge…) to a corner, inside the tile's padding. */
    public ImageTile overlay(Node node, Pos position) {
        StackPane.setAlignment(node, position);
        StackPane.setMargin(node, new Insets(12));
        getChildren().add(node);
        return this;
    }

    public void setSelected(boolean selected) {
        if (selected) {
            if (!getStyleClass().contains("selected")) getStyleClass().add("selected");
        } else {
            getStyleClass().remove("selected");
        }
    }
}
