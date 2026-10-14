package tk.tahri.eval.desktop.ui.util;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Utilitaire unifié pour le chargement, la validation et la manipulation des images
 * à travers toute l'application.
 */
public final class ImageUtils {

    private static final Logger log = LoggerFactory.getLogger(ImageUtils.class);

    public static final List<String> SUPPORTED_EXTENSIONS_GLOB = Arrays.asList(
            "*.png", "*.jpg", "*.jpeg", "*.webp", "*.bmp", "*.gif"
    );

    public static final List<String> SUPPORTED_EXTENSIONS_RAW = Arrays.asList(
            "png", "jpg", "jpeg", "webp", "bmp", "gif"
    );

    private ImageUtils() {}

    /**
     * Crée un FileChooser standardisé pour la sélection d'images.
     */
    public static FileChooser createImageFileChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title != null ? title : "Sélectionner une image");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images (*.png, *.jpg, *.jpeg, *.webp, *.bmp)", SUPPORTED_EXTENSIONS_GLOB),
                new FileChooser.ExtensionFilter("Tous les fichiers (*.*)", "*.*")
        );
        return chooser;
    }

    /**
     * Vérifie si le fichier donné est un fichier image supporté d'après son extension.
     */
    public static boolean isImageFile(File file) {
        if (file == null || !file.exists() || file.isDirectory()) {
            return false;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS_RAW.stream().anyMatch(ext -> name.endsWith("." + ext));
    }

    /**
     * Charge une image de façon robuste avec mécanisme de secours :
     * 1) JavaFX InputStream direct
     * 2) ImageIO
     * 3) OpenCV (WebP / formats spécifiques)
     */
    public static Image loadImage(File file) {
        if (file == null || !file.exists()) {
            return null;
        }

        // 1. Essai direct via InputStream (rapide pour PNG, JPG, GIF standard)
        try (InputStream is = Files.newInputStream(file.toPath())) {
            Image img = new Image(is);
            if (!img.isError() && img.getWidth() > 0) {
                return img;
            }
        } catch (Exception ex) {
            log.debug("Lecture directe JavaFX échouée pour {} : {}", file.getName(), ex.getMessage());
        }

        // 2. Bascule sur ImageIO et OpenCV
        BufferedImage bi = readBufferedImage(file);
        if (bi != null) {
            try {
                return SwingFXUtils.toFXImage(bi, null);
            } catch (Exception ex) {
                log.debug("Conversion SwingFXUtils échouée pour {} : {}", file.getName(), ex.getMessage());
            }
        }

        return null;
    }

    /**
     * Charge une image depuis un chemin Path.
     */
    public static Image loadImage(Path path) {
        if (path == null) return null;
        return loadImage(path.toFile());
    }

    /**
     * Décode un fichier image en BufferedImage avec secours OpenCV pour les formats étendus (WebP...).
     */
    public static BufferedImage readBufferedImage(File file) {
        if (file == null || !file.exists()) {
            return null;
        }

        // Essai ImageIO standard
        try {
            BufferedImage image = ImageIO.read(file);
            if (image != null) {
                return image;
            }
        } catch (Exception ex) {
            log.debug("ImageIO.read échoué pour {} : {}", file.getName(), ex.getMessage());
        }

        // Essai via OpenCV (WebP, etc.)
        try {
            org.opencv.core.Mat mat = org.opencv.imgcodecs.Imgcodecs.imread(file.getAbsolutePath());
            if (mat != null && !mat.empty()) {
                org.opencv.core.MatOfByte mob = new org.opencv.core.MatOfByte();
                org.opencv.imgcodecs.Imgcodecs.imencode(".png", mat, mob);
                BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(mob.toArray()));
                mat.release();
                if (image != null) {
                    return image;
                }
            }
        } catch (Throwable t) {
            log.debug("Décodage OpenCV échoué pour {} : {}", file.getName(), t.getMessage());
        }

        return null;
    }

    /**
     * Formate lisiblement la taille d'un fichier en octets (ex: 245 Ko, 1.2 Mo).
     */
    public static String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 o";
        if (bytes < 1024) return bytes + " o";
        if (bytes < 1024 * 1024) return String.format(Locale.FRENCH, "%.0f Ko", bytes / 1024.0);
        return String.format(Locale.FRENCH, "%.1f Mo", bytes / (1024.0 * 1024.0));
    }
}
