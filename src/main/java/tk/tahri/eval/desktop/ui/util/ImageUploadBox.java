package tk.tahri.eval.desktop.ui.util;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.nio.file.Path;

/**
 * Composant d'interface unifié pour l'import, l'aperçu et la suppression d'images.
 * Supporte le sélecteur de fichiers, le glisser-déposer (Drag & Drop) et l'adaptation réactive.
 */
public class ImageUploadBox extends HBox {

    public enum DisplayMode {
        SQUARE,      // Format carré pour logos (ex: 100x100)
        LANDSCAPE    // Format paysage pour illustrations de questions (ex: 140x90)
    }

    private final DisplayMode displayMode;
    private final String defaultTitle;
    private final String defaultHint;

    private final ObjectProperty<File> chosenFile = new SimpleObjectProperty<>(null);
    private final BooleanProperty imageRemoved = new SimpleBooleanProperty(false);
    private Image currentLoadedImage = null;
    private String currentDisplayName = null;

    private final StackPane previewBox = new StackPane();
    private final Label lblTitle = new Label();
    private final Label lblDetails = new Label();
    private final Button btnChoose = new Button();
    private final Button btnRemove = new Button();

    private Runnable onChangeCallback;

    public ImageUploadBox(String defaultTitle, String defaultHint, DisplayMode displayMode) {
        this.defaultTitle = defaultTitle != null ? defaultTitle : "Image";
        this.defaultHint = defaultHint != null ? defaultHint : "Format recommandé : PNG, JPG, WebP";
        this.displayMode = displayMode != null ? displayMode : DisplayMode.SQUARE;

        initUI();
        initDragAndDrop();
        refreshDisplay();
    }

    private void initUI() {
        this.setAlignment(Pos.CENTER_LEFT);
        this.setSpacing(14);
        this.getStyleClass().add("image-upload-box");

        // Panneau d'aperçu
        previewBox.getStyleClass().add("image-upload-preview");
        previewBox.setFocusTraversable(false);

        // Textes et informations
        lblTitle.getStyleClass().add("image-upload-title");
        lblDetails.getStyleClass().add("image-upload-details");

        // Bouton Choisir / Changer
        btnChoose.getStyleClass().addAll("button", "image-upload-btn-choose");
        btnChoose.setGraphic(new FontIcon(Feather.UPLOAD));
        btnChoose.setOnAction(e -> openFileChooser());

        // Bouton Retirer
        btnRemove.getStyleClass().addAll("button", "btn-danger-outline", "image-upload-btn-remove");
        FontIcon trashIcon = new FontIcon(Feather.TRASH_2);
        trashIcon.setIconSize(13);
        btnRemove.setGraphic(trashIcon);
        btnRemove.setText("Retirer");
        btnRemove.setOnAction(e -> removeImage());

        HBox buttonBar = new HBox(8, btnChoose, btnRemove);
        buttonBar.setAlignment(Pos.CENTER_LEFT);

        VBox infoBox = new VBox(5, lblTitle, lblDetails, buttonBar);
        infoBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        this.getChildren().addAll(previewBox, infoBox);
    }

    private void initDragAndDrop() {
        this.setOnDragOver(event -> {
            if (event.getGestureSource() != this && event.getDragboard().hasFiles()) {
                boolean hasImage = event.getDragboard().getFiles().stream().anyMatch(ImageUtils::isImageFile);
                if (hasImage) {
                    event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                }
            }
            event.consume();
        });

        this.setOnDragEntered(event -> {
            if (event.getGestureSource() != this && event.getDragboard().hasFiles()) {
                boolean hasImage = event.getDragboard().getFiles().stream().anyMatch(ImageUtils::isImageFile);
                if (hasImage) {
                    if (!this.getStyleClass().contains("drag-active")) {
                        this.getStyleClass().add("drag-active");
                    }
                    if (!previewBox.getStyleClass().contains("drag-active")) {
                        previewBox.getStyleClass().add("drag-active");
                    }
                }
            }
            event.consume();
        });

        this.setOnDragExited(event -> {
            this.getStyleClass().remove("drag-active");
            previewBox.getStyleClass().remove("drag-active");
            event.consume();
        });

        this.setOnDragDropped(event -> {
            this.getStyleClass().remove("drag-active");
            previewBox.getStyleClass().remove("drag-active");
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                for (File file : db.getFiles()) {
                    if (ImageUtils.isImageFile(file)) {
                        selectFile(file);
                        success = true;
                        break;
                    }
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void openFileChooser() {
        Window window = getScene() != null ? getScene().getWindow() : null;
        FileChooser chooser = ImageUtils.createImageFileChooser("Sélectionner " + defaultTitle.toLowerCase());
        File file = chooser.showOpenDialog(window);
        if (file != null) {
            selectFile(file);
        }
    }

    public void selectFile(File file) {
        if (file == null || !file.exists()) return;
        Image img = ImageUtils.loadImage(file);
        if (img != null && !img.isError() && img.getWidth() > 0) {
            this.chosenFile.set(file);
            this.imageRemoved.set(false);
            this.currentLoadedImage = img;
            this.currentDisplayName = file.getName();
            refreshDisplay();
            fireChange();
        }
    }

    public void setInitialImage(File file, String displayName) {
        if (file == null || !file.exists()) return;
        Image img = ImageUtils.loadImage(file);
        if (img != null && !img.isError() && img.getWidth() > 0) {
            this.chosenFile.set(null);
            this.imageRemoved.set(false);
            this.currentLoadedImage = img;
            this.currentDisplayName = (displayName != null && !displayName.isBlank()) ? displayName : file.getName();
            refreshDisplay();
        }
    }

    public void setInitialImage(Path path, String displayName) {
        if (path == null) return;
        setInitialImage(path.toFile(), displayName);
    }

    public void removeImage() {
        this.chosenFile.set(null);
        this.imageRemoved.set(true);
        this.currentLoadedImage = null;
        this.currentDisplayName = null;
        refreshDisplay();
        fireChange();
    }

    private void refreshDisplay() {
        previewBox.getChildren().clear();

        if (currentLoadedImage != null && !imageRemoved.get()) {
            previewBox.getStyleClass().remove("image-upload-preview-empty");
            if (!previewBox.getStyleClass().contains("image-upload-preview-active")) {
                previewBox.getStyleClass().add("image-upload-preview-active");
            }

            double maxW = (displayMode == DisplayMode.SQUARE) ? 100 : 130;
            double maxH = (displayMode == DisplayMode.SQUARE) ? 100 : 85;

            previewBox.setMinSize(maxW + 10, maxH + 10);
            previewBox.setPrefSize(maxW + 10, maxH + 10);
            previewBox.setMaxSize(maxW + 10, maxH + 10);

            double origW = currentLoadedImage.getWidth();
            double origH = currentLoadedImage.getHeight();
            double fitW = maxW;
            double fitH = maxH;
            if (origW > 0 && origH > 0) {
                double scale = Math.min(maxW / origW, maxH / origH);
                fitW = Math.max(1, Math.round(origW * scale));
                fitH = Math.max(1, Math.round(origH * scale));
            }

            ImageView iv = new ImageView(currentLoadedImage);
            iv.setFitWidth(fitW);
            iv.setFitHeight(fitH);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);

            Rectangle clip = new Rectangle(fitW, fitH);
            clip.setArcWidth(12);
            clip.setArcHeight(12);
            iv.setClip(clip);

            previewBox.getChildren().add(iv);

            lblTitle.setText(currentDisplayName != null ? currentDisplayName : defaultTitle);

            File f = chosenFile.get();
            if (f != null && f.exists()) {
                lblDetails.setText(String.format("%d × %d px • %s", (int) origW, (int) origH, ImageUtils.formatFileSize(f.length())));
            } else {
                lblDetails.setText(String.format("%d × %d px • Image enregistrée", (int) origW, (int) origH));
            }

            btnChoose.setText("Changer l'image...");
            btnRemove.setDisable(false);
        } else {
            previewBox.getStyleClass().remove("image-upload-preview-active");
            if (!previewBox.getStyleClass().contains("image-upload-preview-empty")) {
                previewBox.getStyleClass().add("image-upload-preview-empty");
            }

            double emptyW = (displayMode == DisplayMode.SQUARE) ? 68 : 90;
            double emptyH = (displayMode == DisplayMode.SQUARE) ? 68 : 68;

            previewBox.setMinSize(emptyW, emptyH);
            previewBox.setPrefSize(emptyW, emptyH);
            previewBox.setMaxSize(emptyW, emptyH);

            FontIcon ph = new FontIcon(Feather.IMAGE);
            ph.setIconSize(24);
            ph.getStyleClass().add("image-upload-placeholder-icon");
            previewBox.getChildren().add(ph);

            lblTitle.setText(defaultTitle);
            lblDetails.setText(defaultHint + " • Glissez-déposez ici");
            btnChoose.setText("Choisir un fichier...");
            btnRemove.setDisable(true);
        }
    }

    public void setOnChange(Runnable callback) {
        this.onChangeCallback = callback;
    }

    private void fireChange() {
        if (onChangeCallback != null) {
            onChangeCallback.run();
        }
    }

    public File getFile() {
        return chosenFile.get();
    }

    public boolean isRemoved() {
        return imageRemoved.get();
    }

    public boolean hasImage() {
        return currentLoadedImage != null && !imageRemoved.get();
    }

    public Image getLoadedImage() {
        return currentLoadedImage;
    }
}
