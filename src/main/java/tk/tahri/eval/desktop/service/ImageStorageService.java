package tk.tahri.eval.desktop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);

    public enum ImageFolder {
        GROUP_LOGOS("group_logos_sm", "group_logos_xm"),
        COURSE_LOGOS("course_logos_sm", "course_logos_xm"),
        QUESTION_IMAGES("question_images", "question_images");

        private final String subSm;
        private final String subXm;

        ImageFolder(String subSm, String subXm) {
            this.subSm = subSm;
            this.subXm = subXm;
        }

        public Path dirSm() { return tk.tahri.eval.desktop.config.AppPaths.getUploadDir().resolve(subSm); }
        public Path dirXm() { return tk.tahri.eval.desktop.config.AppPaths.getUploadDir().resolve(subXm); }
    }

    public ImageStorageService() {
        for (ImageFolder folder : ImageFolder.values()) {
            try {
                Files.createDirectories(folder.dirSm());
                Files.createDirectories(folder.dirXm());
            } catch (IOException e) {
                log.error("Failed to create upload directories for {}", folder, e);
            }
        }
    }

    public String storeImage(File file, ImageFolder folder) {
        if (file == null || !file.exists()) {
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                // Décodage alternatif via OpenCV pour les formats comme WebP non supportés par défaut par ImageIO
                try {
                    org.opencv.core.Mat mat = org.opencv.imgcodecs.Imgcodecs.imread(file.getAbsolutePath());
                    if (mat != null && !mat.empty()) {
                        org.opencv.core.MatOfByte mob = new org.opencv.core.MatOfByte();
                        org.opencv.imgcodecs.Imgcodecs.imencode(".png", mat, mob);
                        image = ImageIO.read(new java.io.ByteArrayInputStream(mob.toArray()));
                        mat.release();
                    }
                } catch (Throwable t) {
                    log.warn("Échec du décodage alternatif OpenCV pour {}: {}", file.getName(), t.getMessage());
                }
            }
            if (image == null) return null;
            return saveProcessedImage(image, folder);
        } catch (IOException e) {
            log.error("Failed to store image from file: {}", file.getAbsolutePath(), e);
            return null;
        }
    }

    public String storeImage(InputStream in, ImageFolder folder) {
        if (in == null) return null;
        try {
            BufferedImage image = ImageIO.read(in);
            if (image == null) return null;
            return saveProcessedImage(image, folder);
        } catch (IOException e) {
            log.error("Failed to store image from stream", e);
            return null;
        }
    }

    private String saveProcessedImage(BufferedImage original, ImageFolder folder) throws IOException {
        String filename = UUID.randomUUID() + ".png";
        Path destSm = folder.dirSm().resolve(filename);
        Path destXm = folder.dirXm().resolve(filename);

        if (folder == ImageFolder.QUESTION_IMAGES) {
            ImageIO.write(original, "PNG", destSm.toFile());
        } else {
            BufferedImage sm = resizeAndCropSquare(original, 400);
            BufferedImage xm = resizeAndCropSquare(original, 100);
            ImageIO.write(sm, "PNG", destSm.toFile());
            ImageIO.write(xm, "PNG", destXm.toFile());
        }
        return filename;
    }

    private BufferedImage resizeAndCropSquare(BufferedImage src, int targetSize) {
        int width = src.getWidth();
        int height = src.getHeight();
        int min = Math.min(width, height);
        int x = (width - min) / 2;
        int y = (height - min) / 2;

        BufferedImage cropped = src.getSubimage(x, y, min, min);
        BufferedImage resized = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = resized.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(cropped, 0, 0, targetSize, targetSize, null);
        g2.dispose();
        return resized;
    }

    public Path getImagePath(String filename, ImageFolder folder, boolean small) {
        if (filename == null) return null;
        Path primary = (small ? folder.dirXm() : folder.dirSm()).resolve(filename);
        if (Files.exists(primary)) {
            return primary;
        }
        // Fallback between GROUP_LOGOS and COURSE_LOGOS
        if (folder == ImageFolder.GROUP_LOGOS) {
            Path fallback = (small ? ImageFolder.COURSE_LOGOS.dirXm() : ImageFolder.COURSE_LOGOS.dirSm()).resolve(filename);
            if (Files.exists(fallback)) return fallback;
        } else if (folder == ImageFolder.COURSE_LOGOS) {
            Path fallback = (small ? ImageFolder.GROUP_LOGOS.dirXm() : ImageFolder.GROUP_LOGOS.dirSm()).resolve(filename);
            if (Files.exists(fallback)) return fallback;
        }
        return primary;
    }
}
