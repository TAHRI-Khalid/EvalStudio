package tk.tahri.eval.desktop.document;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.pdf417.PDF417Writer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import tk.tahri.eval.desktop.document.entities.Setting;
import tk.tahri.eval.desktop.exception.AddPageToDocumentException;
import tk.tahri.eval.desktop.exception.WriteBarcodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tk.tahri.eval.desktop.exception.WritePageNumberException;
import tk.tahri.eval.desktop.service.ImageStorageService;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public abstract class Document {
    private static final Logger log = LoggerFactory.getLogger(Document.class);
    protected final PDDocument document;
    protected final PDFont font;
    public final PDRectangle pdRectangle = PDRectangle.A4;
    private final Map<String, PDImageXObject> images;

    public Document(PDDocument document, PDFont font) {
        this.document = document;
        this.font = font;
        this.images = new HashMap<>();
    }

    public PDPageContentStream addPageToTheDocument(boolean displayMarks, boolean draft) {
        try {
            var page = new PDPage(pdRectangle);
            this.document.addPage(page);
            var canvas = new PDPageContentStream(this.document, page);
            if (displayMarks) addPageMarks(canvas, pdRectangle);

            if (draft) {
                canvas.setNonStrokingColor(new Color(0xE0, 0xE0, 0xE0));
                canvas.beginText();
                Matrix matrix = Matrix.getRotateInstance(Math.toRadians(50), 0, 0);
                matrix.translate(200, 0);
                canvas.setTextMatrix(matrix);
                canvas.setFont(font, 150);
                canvas.newLineAtOffset(0, 0);
                canvas.showText("Brouillon");
                canvas.endText();
                canvas.setNonStrokingColor(Color.BLACK);
            }
            return canvas;
        } catch (IOException e) {
            throw new AddPageToDocumentException();
        }
    }

    private void writeLine(PDPageContentStream canvas, String line, Place place, TextAlign textAlign, float fontSize) throws IOException {
        canvas.setFont(font, fontSize);
        canvas.beginText();
        var textWidth = font.getStringWidth(line) / 1000 * fontSize;
        var x = switch (textAlign) {
            case LEFT -> place.x();
            case CENTER -> place.x() + place.width() / 2 - textWidth / 2;
            case RIGHT -> place.x() - textWidth;
        };
        canvas.newLineAtOffset(x, place.y() - Setting.lineSpacing);
        canvas.showText(line);
        canvas.endText();
    }

    protected void addPageMarks(PDPageContentStream canvas, PDRectangle pdRectangle) throws IOException {
        float size = Setting.markSize;
        drawSquareMark(canvas, Setting.margin, Setting.margin, size);
        drawSquareMark(canvas, Setting.margin, pdRectangle.getHeight() - Setting.margin, size);
        drawSquareMark(canvas, pdRectangle.getWidth() - Setting.margin, Setting.margin, size);
        drawSquareMark(canvas, pdRectangle.getWidth() - Setting.margin, pdRectangle.getHeight() - Setting.margin, size);
    }

    protected void drawSquareMark(PDPageContentStream canvas, float cx, float cy, float size) throws IOException {
        canvas.addRect(cx - (size / 2f), cy - (size / 2f), size, size);
        canvas.fill();
    }

    protected void drawCircle(PDPageContentStream canvas, float cx, float cy, float r, float width) throws IOException {
        final float k = 0.552284749831f;
        canvas.setLineWidth(width);
        canvas.moveTo(cx - r, cy);
        canvas.curveTo(cx - r, cy + k * r, cx - k * r, cy + r, cx, cy + r);
        canvas.curveTo(cx + k * r, cy + r, cx + r, cy + k * r, cx + r, cy);
        canvas.curveTo(cx + r, cy - k * r, cx + k * r, cy - r, cx, cy - r);
        canvas.curveTo(cx - k * r, cy - r, cx - r, cy - k * r, cx - r, cy);
        canvas.stroke();
    }

    protected void drawCheckBox(PDPageContentStream canvas, float cx, float cy, float r) throws IOException {
        canvas.setLineWidth(0.8f);
        canvas.addRect(cx - r, cy - r, 2 * r, 2 * r);
        canvas.stroke();
    }

    protected void drawRectangle(PDPageContentStream canvas, float x, float y, float width, float height) throws IOException {
        canvas.addRect(x, y - height, width, height);
        canvas.stroke();
    }

    private static final PDF417Writer BARCODE_WRITER = new PDF417Writer();
    private static final Map<EncodeHintType, Object> BARCODE_HINTS = Map.of(
            EncodeHintType.MARGIN, 2,
            EncodeHintType.ERROR_CORRECTION, 3
    );

    public void addBarcodeImage(PDPageContentStream canvas, String barcodeText) {
        try {
            var bitMatrix = BARCODE_WRITER.encode(barcodeText, BarcodeFormat.PDF_417, 142, 37, BARCODE_HINTS);
            var bufferedImage = MatrixToImageWriter.toBufferedImage(bitMatrix);
            var barcodeImage = LosslessFactory.createFromImage(this.document, bufferedImage);
            float y = Setting.margin - (37f / 2f);
            canvas.drawImage(barcodeImage, Setting.margin * 2, y, 142, 37);
        } catch (Exception e) {
            throw new WriteBarcodeException(barcodeText);
        }
    }

    protected float drawParagraph(PDPageContentStream canvas, String text, Place place, TextAlign textAlign, float fontSize) throws IOException {
        return paragraph(canvas, text, place, textAlign, fontSize, true);
    }

    protected float getParagraphHeight(PDPageContentStream canvas, String text, Place place, TextAlign textAlign, float fontSize) throws IOException {
        return paragraph(canvas, text, place, textAlign, fontSize, false);
    }

    private float paragraph(PDPageContentStream canvas, String text, Place place, TextAlign textAlign, float fontSize, boolean sideEffect) throws IOException {
        int lineNumber = 0;
        var lines = this.getPrintableString(text, font).lines().toList();
        for (var line : lines) {
            var buffer = new StringBuilder();
            var words = line.split(" ");
            for (var word : words) {
                var lineWidth = font.getStringWidth(buffer + word) / 1000 * fontSize;
                if (lineWidth <= place.width()) {
                    buffer.append(word).append(" ");
                } else {
                    if (sideEffect) {
                        var newLinePlace = new Place(place.x(), place.y() - (lineNumber * Setting.lineSpacing), place.width());
                        writeLine(canvas, buffer.toString(), newLinePlace, textAlign, fontSize);
                    }
                    buffer.setLength(0);
                    buffer.append(word).append(" ");
                    lineNumber++;
                }
            }
            if (!buffer.isEmpty()) {
                if (sideEffect) {
                    var newLinePlace = new Place(place.x(), place.y() - (lineNumber * Setting.lineSpacing), place.width());
                    writeLine(canvas, buffer.toString(), newLinePlace, textAlign, fontSize);
                }
                lineNumber++;
            }
        }
        return lineNumber * Setting.lineSpacing;
    }

    private String getPrintableString(String input, PDFont font) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            try {
                font.encode(String.valueOf(c));
                sb.append(c);
            } catch (IllegalArgumentException | IOException e) {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    protected float drawImage(PDPageContentStream canvas, PDImageXObject image, Place place, float maxHeight) throws IOException {
        return image(canvas, image, place, maxHeight, true);
    }

    protected float getImageHeight(PDPageContentStream canvas, PDImageXObject image, Place place, float maxHeight) throws IOException {
        return image(canvas, image, place, maxHeight, false);
    }

    private float image(PDPageContentStream canvas, PDImageXObject image, Place place, float maxHeight, boolean sideEffect) throws IOException {
        if (image == null) {
            return 0f;
        }
        final float maxWidth = place.width();
        var width = (float) image.getWidth();
        var height = (float) image.getHeight();

        if (image.getHeight() > maxHeight) {
            height = maxHeight;
            width = image.getWidth() * maxHeight / image.getHeight();
            if (width > maxWidth) {
                width = maxWidth;
                height = image.getHeight() * maxWidth / image.getWidth();
            }
        }

        if (image.getWidth() > maxWidth) {
            width = maxWidth;
            height = image.getHeight() * maxWidth / image.getWidth();
            if (height > maxHeight) {
                height = maxHeight;
                width = image.getWidth() * maxHeight / image.getHeight();
            }
        }

        if (sideEffect) {
            canvas.drawImage(image, place.x() + (place.width() / 2f) - (width / 2f), place.y() - height, width, height);
        }
        return height;
    }

    public void addPageNumbersAndBarcode(int pageNumber, String blueprintId) {
        for (int i = 0; i < pageNumber; i++) {
            var barcode = blueprintId + "-" + (i + 1);
            try {
                var page = document.getPage(document.getNumberOfPages() - pageNumber + i);
                try (var pageCanvas = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true)) {
                    pageCanvas.setFont(font, Setting.fontSize);
                    var pageNumberPlace = new Place(pdRectangle.getWidth() - (2 * Setting.margin), Setting.margin + Setting.lineSpacing * 2 / 3, 100);
                    drawParagraph(pageCanvas, "Page " + (i + 1) + "/" + pageNumber, pageNumberPlace, TextAlign.RIGHT, Setting.fontSize);
                    addBarcodeImage(pageCanvas, barcode);
                }
            } catch (IOException | WriteBarcodeException e) {
                throw new WritePageNumberException(barcode);
            }
        }
    }

    protected String durationToString(int durationMinutes) {
        return String.format("%02d h %02d min", durationMinutes / 60, durationMinutes % 60);
    }

    protected PDImageXObject loadImage(String folder, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        try {
            if (!images.containsKey(fileName)) {
                File targetFile = null;
                // 1. Tente dans le dossier spécifié
                if (folder != null && !folder.isBlank()) {
                    File f = Path.of(folder).resolve(fileName).toFile();
                    if (f.exists() && f.canRead()) {
                        targetFile = f;
                    }
                }
                // 2. Recherche alternative dans les dossiers de logos et questions
                if (targetFile == null) {
                    Path[] candidates = new Path[]{
                            ImageStorageService.ImageFolder.GROUP_LOGOS.dirSm().resolve(fileName),
                            ImageStorageService.ImageFolder.GROUP_LOGOS.dirXm().resolve(fileName),
                            ImageStorageService.ImageFolder.COURSE_LOGOS.dirSm().resolve(fileName),
                            ImageStorageService.ImageFolder.COURSE_LOGOS.dirXm().resolve(fileName),
                            ImageStorageService.ImageFolder.QUESTION_IMAGES.dirSm().resolve(fileName)
                    };
                    for (Path c : candidates) {
                        File candidateFile = c.toFile();
                        if (candidateFile.exists() && candidateFile.canRead()) {
                            targetFile = candidateFile;
                            break;
                        }
                    }
                }

                if (targetFile != null) {
                    images.put(fileName, PDImageXObject.createFromFileByExtension(targetFile, document));
                } else {
                    log.warn("Fichier image introuvable ou illisible : {} (dossier demandé : {})", fileName, folder);
                    return null;
                }
            }
            return images.get(fileName);
        } catch (Exception e) {
            log.warn("Impossible de charger l'image {} depuis {} : {}", fileName, folder, e.getMessage());
            return null;
        }
    }

    protected String logoFolder() {
        return ImageStorageService.ImageFolder.GROUP_LOGOS.dirSm().toString();
    }

    protected String questionImagesFolder() {
        return ImageStorageService.ImageFolder.QUESTION_IMAGES.dirSm().toString();
    }
}
