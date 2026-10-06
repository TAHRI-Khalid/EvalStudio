package tk.tahri.eval.desktop.ui.controllers;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tk.tahri.eval.desktop.ui.NotificationService;
import tk.tahri.eval.desktop.ui.util.DesktopFileViewer;

import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

@Component
public class PdfViewerController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(PdfViewerController.class);

    @FXML private Button btnBack;
    @FXML private Label lblTitle;
    @FXML private Label lblSubtitle;
    @FXML private Button btnExport;
    @FXML private Button btnPrint;
    @FXML private Button btnExternalViewer;

    @FXML private Button btnZoomOut;
    @FXML private Label lblZoom;
    @FXML private Button btnZoomIn;
    @FXML private Button btnActualSize;
    @FXML private Button btnFitWidth;
    @FXML private Button btnRotate;

    @FXML private ScrollPane pdfScrollPane;
    @FXML private StackPane imageContainer;
    @FXML private VBox pagesContainer;
    @FXML private ProgressIndicator loadingIndicator;

    private final NotificationService notificationService;
    private final List<ImageView> pageImageViews = new ArrayList<>();

    private File currentPdfFile;
    private PDDocument pdDocument;
    private PDFRenderer pdfRenderer;
    private int totalPages = 0;
    private double zoomFactor = 1.0;
    private int rotationDegrees = 0;
    private Runnable backAction;

    public PdfViewerController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Permet de zoomer avec Ctrl + Molette de la souris sur le ScrollPane
        if (pdfScrollPane != null) {
            pdfScrollPane.setOnScroll(event -> {
                if (event.isControlDown()) {
                    event.consume();
                    if (event.getDeltaY() > 0) {
                        onZoomIn();
                    } else if (event.getDeltaY() < 0) {
                        onZoomOut();
                    }
                }
            });
        }
    }

    public void loadFile(File pdfFile, String title, Runnable backAction) {
        this.currentPdfFile = pdfFile;
        this.backAction = backAction;

        closeCurrentDocument();
        zoomFactor = 1.0;
        rotationDegrees = 0;

        if (lblTitle != null) {
            lblTitle.setText(title != null ? title : "Visualiseur PDF");
        }
        if (lblSubtitle != null) {
            lblSubtitle.setText("Chargement du document...");
        }
        if (loadingIndicator != null) {
            loadingIndicator.setVisible(true);
        }

        if (pdfFile == null || !pdfFile.exists()) {
            if (lblSubtitle != null) {
                lblSubtitle.setText("Fichier introuvable.");
            }
            if (loadingIndicator != null) {
                loadingIndicator.setVisible(false);
            }
            return;
        }

        Thread.startVirtualThread(() -> {
            try {
                PDDocument doc = Loader.loadPDF(pdfFile);
                int pages = doc.getNumberOfPages();
                PDFRenderer renderer = new PDFRenderer(doc);

                Platform.runLater(() -> {
                    this.pdDocument = doc;
                    this.pdfRenderer = renderer;
                    this.totalPages = pages;

                    if (lblSubtitle != null) {
                        lblSubtitle.setText(pdfFile.getName() + " • " + pages + " page(s) • " + formatFileSize(pdfFile.length()));
                    }
                    updateZoomLabel();

                    if (pagesContainer != null) {
                        pagesContainer.getChildren().clear();
                    }
                    pageImageViews.clear();

                    // Initialiser les ImageView pour chaque page
                    for (int i = 0; i < pages; i++) {
                        ImageView iv = new ImageView();
                        iv.setPreserveRatio(true);
                        iv.setStyle("-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.18), 8, 0, 0, 2);");
                        pageImageViews.add(iv);
                        if (pagesContainer != null) {
                            pagesContainer.getChildren().add(iv);
                        }
                    }

                    // Rendu asynchrone de toutes les pages
                    renderAllPages(doc, renderer, pages);
                });
            } catch (Exception e) {
                log.error("Échec lors du chargement du fichier PDF {}", pdfFile.getAbsolutePath(), e);
                Platform.runLater(() -> {
                    if (loadingIndicator != null) loadingIndicator.setVisible(false);
                    if (lblSubtitle != null) lblSubtitle.setText("Erreur : Impossible de lire ce document PDF.");
                    notificationService.showError("Erreur d'ouverture", "Impossible de lire le fichier PDF : " + e.getMessage());
                });
            }
        });
    }

    private void renderAllPages(PDDocument doc, PDFRenderer renderer, int pages) {
        Thread.startVirtualThread(() -> {
            try {
                for (int pageIdx = 0; pageIdx < pages; pageIdx++) {
                    if (this.pdDocument != doc) {
                        // Le document a été changé ou fermé entre temps
                        return;
                    }

                    // Rendu à 130 DPI (netteté excellente tout en préservant la mémoire)
                    BufferedImage bim = renderer.renderImageWithDPI(pageIdx, 130, ImageType.RGB);
                    Image fxImage = SwingFXUtils.toFXImage(bim, null);

                    final int currentIdx = pageIdx;
                    Platform.runLater(() -> {
                        if (this.pdDocument == doc && currentIdx < pageImageViews.size()) {
                            ImageView iv = pageImageViews.get(currentIdx);
                            iv.setImage(fxImage);
                            updateSingleImageDimensions(iv);
                            iv.setRotate(rotationDegrees);
                        }
                        if (currentIdx == 0 && loadingIndicator != null) {
                            loadingIndicator.setVisible(false);
                        }
                    });
                }
            } catch (Exception e) {
                log.error("Erreur lors du rendu des pages du PDF", e);
            } finally {
                Platform.runLater(() -> {
                    if (loadingIndicator != null) {
                        loadingIndicator.setVisible(false);
                    }
                });
            }
        });
    }

    private void updateSingleImageDimensions(ImageView iv) {
        Image img = iv.getImage();
        if (img == null) return;

        double baseWidth = img.getWidth();
        double baseHeight = img.getHeight();

        iv.setFitWidth(baseWidth * zoomFactor);
        iv.setFitHeight(baseHeight * zoomFactor);
    }

    private void applyZoomAndRotation() {
        updateZoomLabel();
        for (ImageView iv : pageImageViews) {
            updateSingleImageDimensions(iv);
            iv.setRotate(rotationDegrees);
        }
    }

    private void updateZoomLabel() {
        if (lblZoom != null) {
            lblZoom.setText(Math.round(zoomFactor * 100) + "%");
        }
    }

    @FXML
    public void onZoomIn() {
        if (zoomFactor < 3.5) {
            zoomFactor = Math.min(zoomFactor * 1.25, 3.5);
            applyZoomAndRotation();
        }
    }

    @FXML
    public void onZoomOut() {
        if (zoomFactor > 0.3) {
            zoomFactor = Math.max(zoomFactor / 1.25, 0.3);
            applyZoomAndRotation();
        }
    }

    @FXML
    public void onActualSize() {
        zoomFactor = 1.0;
        applyZoomAndRotation();
    }

    @FXML
    public void onFitWidth() {
        if (pdfScrollPane != null && !pageImageViews.isEmpty()) {
            Image firstImg = pageImageViews.getFirst().getImage();
            if (firstImg != null && firstImg.getWidth() > 0) {
                double viewportWidth = pdfScrollPane.getViewportBounds().getWidth() - 48;
                if (viewportWidth > 150) {
                    zoomFactor = Math.max(0.3, Math.min(viewportWidth / firstImg.getWidth(), 3.5));
                    applyZoomAndRotation();
                }
            }
        }
    }

    @FXML
    public void onRotate() {
        rotationDegrees = (rotationDegrees + 90) % 360;
        for (ImageView iv : pageImageViews) {
            iv.setRotate(rotationDegrees);
        }
    }

    @FXML
    public void onExport() {
        if (currentPdfFile == null || !currentPdfFile.exists()) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Enregistrer le document PDF sous...");
        chooser.setInitialFileName(currentPdfFile.getName());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documents PDF (*.pdf)", "*.pdf"));

        File target = chooser.showSaveDialog(pagesContainer.getScene().getWindow());
        if (target != null) {
            try {
                Files.copy(currentPdfFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                notificationService.showInfo("Export réussi", "Le document a été enregistré sous :\n" + target.getAbsolutePath());
            } catch (IOException e) {
                log.error("Échec de l'exportation du PDF", e);
                notificationService.showError("Erreur d'exportation", e.getMessage());
            }
        }
    }

    @FXML
    public void onPrint() {
        if (currentPdfFile == null || !currentPdfFile.exists()) return;
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.PRINT)) {
                Desktop.getDesktop().print(currentPdfFile);
            } else {
                DesktopFileViewer.openFile(currentPdfFile);
            }
        } catch (Exception e) {
            log.warn("Impression directe indisponible, bascule sur l'ouverture externe", e);
            DesktopFileViewer.openFile(currentPdfFile);
        }
    }

    @FXML
    public void onExternalViewer() {
        if (currentPdfFile != null && currentPdfFile.exists()) {
            DesktopFileViewer.openFile(currentPdfFile);
        }
    }

    @FXML
    public void onBack() {
        closeCurrentDocument();
        if (backAction != null) {
            backAction.run();
        }
    }

    public void closeCurrentDocument() {
        if (pdDocument != null) {
            try {
                pdDocument.close();
            } catch (IOException ignored) {}
            pdDocument = null;
            pdfRenderer = null;
        }
        for (ImageView iv : pageImageViews) {
            iv.setImage(null);
        }
        pageImageViews.clear();
        if (pagesContainer != null) {
            pagesContainer.getChildren().clear();
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " o";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %so", bytes / Math.pow(1024, exp), unit);
    }
}
