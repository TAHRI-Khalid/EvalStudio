package tk.tahri.eval.desktop.document.services;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.pdf417.PDF417Reader;
import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tk.tahri.eval.desktop.document.dto.Barcode;
import tk.tahri.eval.desktop.document.dto.MarkLocation;
import tk.tahri.eval.desktop.document.dto.TaskProgress;
import tk.tahri.eval.desktop.document.dto.TaskProgressTracker;
import tk.tahri.eval.desktop.document.entities.Setting;
import tk.tahri.eval.desktop.document.entities.UncorrectedPage;
import tk.tahri.eval.desktop.document.entities.UncorrectedPageSource;
import tk.tahri.eval.desktop.document.entities.blueprint.BlueprintAnswer;
import tk.tahri.eval.desktop.document.entities.blueprint.BlueprintPage;
import tk.tahri.eval.desktop.document.entities.blueprint.BlueprintPaperAssessment;
import tk.tahri.eval.desktop.document.repositories.BlueprintPaperAssessmentRepository;
import tk.tahri.eval.desktop.exception.InvalidEvaluationStateException;
import tk.tahri.eval.desktop.exception.NotFoundException;
import tk.tahri.eval.desktop.model.Evaluation;
import tk.tahri.eval.desktop.model.EvaluationState;
import tk.tahri.eval.desktop.repository.EvaluationRepository;

import javax.imageio.ImageIO;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

record CornerData(Point point, Rect rect, List<MarkLocation> markLocations) {
}

@Service
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);

    private final Map<DecodeHintType, Object> hints = Map.of(
            DecodeHintType.TRY_HARDER, Boolean.TRUE
    );

    private final int padding = (int) Setting.padding;
    private final EvaluationRepository evaluationRepository;
    private final BlueprintPaperAssessmentRepository blueprintPaperAssessmentRepository;
    private final UncorrectedPageService uncorrectedPageService;
    private final CheckboxClassifier checkboxClassifier;

    @Value("${eval.document.upload-dir:upload}")
    private String uploadDirPath;

    public record ProcessingResult(int totalPages, int correctedPages, int uncorrectedPages) {
    }

    @Autowired
    public DocumentProcessingService(EvaluationRepository evaluationRepository,
                                     BlueprintPaperAssessmentRepository blueprintPaperAssessmentRepository,
                                     UncorrectedPageService uncorrectedPageService,
                                     CheckboxClassifier checkboxClassifier) {
        this.evaluationRepository = evaluationRepository;
        this.blueprintPaperAssessmentRepository = blueprintPaperAssessmentRepository;
        this.uncorrectedPageService = uncorrectedPageService;
        this.checkboxClassifier = checkboxClassifier;
    }

    public ProcessingResult processScanFile(String groupId, String evaluationId, File file,
                                            Consumer<String> statusConsumer,
                                            Consumer<Double> progressConsumer) throws IOException {
        TaskProgressTracker tracker = new TaskProgressTracker(tp -> {
            if (statusConsumer != null) {
                statusConsumer.accept(tp.stageTitle() + " : " + tp.currentDetail());
            }
            if (progressConsumer != null) {
                progressConsumer.accept(tp.progressRatio());
            }
        });
        return processScanFile(groupId, evaluationId, file, tracker);
    }

    public ProcessingResult processScanFile(String groupId, String evaluationId, File file,
                                            Consumer<TaskProgress> progressConsumer) throws IOException {
        TaskProgressTracker tracker = new TaskProgressTracker(progressConsumer);
        return processScanFile(groupId, evaluationId, file, tracker);
    }

    public ProcessingResult processScanFile(String groupId, String evaluationId, File file,
                                            TaskProgressTracker tracker) throws IOException {
        var evaluation = evaluationRepository.findById(evaluationId)
                .filter(e -> groupId.equals(e.groupId()))
                .orElseThrow(() -> new NotFoundException("Évaluation introuvable : " + evaluationId));

        var evalDir = Path.of(uploadDirPath).resolve("evaluations").resolve(evaluationId);
        Files.createDirectories(evalDir.resolve("files"));
        Files.createDirectories(evalDir.resolve("pages"));
        Files.createDirectories(evalDir.resolve("corrected"));

        Path target = evalDir.resolve("files").resolve("scan_" + UUID.randomUUID() + "_" + file.getName());
        Files.copy(file.toPath(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        int totalPages = saveEachDocumentPageAsImage(target, evalDir, evaluationId, tracker);

        return loadAndProcessPages(groupId, evalDir, evaluationId, totalPages, tracker);
    }

    private int saveEachDocumentPageAsImage(Path file, Path root, String evaluationId,
                                            TaskProgressTracker tracker) throws IOException {
        var pages = root.resolve("pages");
        Files.createDirectories(pages);

        try (var walk = Files.walk(pages)) {
            walk.filter(Files::isRegularFile).forEach(path -> FileUtils.deleteQuietly(path.toFile()));
        }
        this.uncorrectedPageService.deleteByEvaluationId(evaluationId);

        String lower = file.toString().toLowerCase();
        if (lower.endsWith(".pdf")) {
            try (var document = Loader.loadPDF(file.toFile())) {
                var pdfRenderer = new PDFRenderer(document);
                int numberOfPages = document.getNumberOfPages();
                if (tracker != null) {
                    tracker.setStage("Phase 1/2 : Extraction haute résolution des pages", numberOfPages, 0.0, 0.30);
                }
                for (int i = 0; i < numberOfPages; ++i) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new IOException("Traitement interrompu par l'utilisateur.");
                    }
                    var image = pdfRenderer.renderImageWithDPI(i, 300, ImageType.GRAY);
                    var path = pages.resolve("page_" + String.format("%04d", i + 1) + "_" + UUID.randomUUID() + ".png");
                    ImageIO.write(image, "png", path.toFile());
                    if (tracker != null) {
                        tracker.updateStep(i + 1, "Page " + (i + 1) + " / " + numberOfPages + " extraite (300 DPI)");
                    }
                }
                return numberOfPages;
            }
        } else {
            if (tracker != null) {
                tracker.setStage("Phase 1/2 : Préparation de l'image", 1, 0.0, 0.30);
            }
            var path = pages.resolve("page_0001_" + UUID.randomUUID() + ".png");
            Files.copy(file, path);
            if (tracker != null) {
                tracker.updateStep(1, "Image prête pour analyse");
            }
            return 1;
        }
    }

    private ProcessingResult loadAndProcessPages(String groupId, Path evalDir, String evaluationId,
                                                int totalExpected,
                                                TaskProgressTracker tracker) throws IOException {
        var path = evalDir.resolve("pages");

        try (var filesStream = Files.list(path)) {
            var files = filesStream.sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
            int total = files.size();
            int current = 0;
            int correctedCount = 0;
            int uncorrectedCount = 0;

            if (tracker != null) {
                tracker.setStage("Phase 2/2 : Reconnaissance optique et détection OMR", total, 0.30, 0.70);
            }

            for (var file : files) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Traitement interrompu par l'utilisateur.");
                }
                current++;
                if (tracker != null) {
                    tracker.updateStep(current, "Analyse optique de la page " + current + " / " + total + "...");
                }

                try {
                    var image = Imgcodecs.imread(file.toString(), Imgcodecs.IMREAD_GRAYSCALE);
                    if (image.empty()) {
                        log.warn("Impossible de lire le fichier image : {}", file);
                        uncorrectedCount++;
                        if (tracker != null) {
                            tracker.recordWarning();
                        }
                        continue;
                    }

                    if (image.cols() > image.rows()) {
                        var rotated = new Mat();
                        Core.rotate(image, rotated, Core.ROTATE_90_CLOCKWISE);
                        image.release();
                        image = rotated;
                    }

                    var scaledImage = new Mat();
                    Imgproc.resize(image, scaledImage, new Size(PDRectangle.A4.getWidth() * 2, PDRectangle.A4.getHeight() * 2));
                    image.release();

                    var opPage = convertImageToBirdEyeView(scaledImage);

                    if (opPage.isEmpty()) {
                        if (isBlankPage(scaledImage)) {
                            log.info("Page {} détectée comme vierge, ignorée.", file.getFileName());
                            FileUtils.deleteQuietly(file.toFile());
                            scaledImage.release();
                            if (tracker != null) {
                                tracker.recordSuccess();
                            }
                            continue;
                        }
                        scaledImage.release();
                        this.saveUncorrectedPage(groupId, evaluationId, file.getFileName().toString(), UncorrectedPageSource.MARKS_ERROR);
                        uncorrectedCount++;
                        if (tracker != null) {
                            tracker.recordWarning();
                        }
                        continue;
                    }
                    scaledImage.release();

                    var page = opPage.get();
                    try {
                        var data = detectTheBarCode(page);
                        if (data.isPresent()) {
                            var isCorrected = correctTheQuestions(groupId, evaluationId, file.getFileName().toString(), evalDir, page, data.get());
                            if (isCorrected) {
                                FileUtils.deleteQuietly(file.toFile());
                                correctedCount++;
                                if (tracker != null) {
                                    tracker.recordSuccess();
                                }
                            } else {
                                uncorrectedCount++;
                                if (tracker != null) {
                                    tracker.recordWarning();
                                }
                            }
                        } else {
                            var rotatedImage = new Mat();
                            try {
                                Core.rotate(page, rotatedImage, Core.ROTATE_180);
                                data = detectTheBarCode(rotatedImage);
                                if (data.isPresent()) {
                                    var isCorrected = correctTheQuestions(groupId, evaluationId, file.getFileName().toString(), evalDir, rotatedImage, data.get());
                                    if (isCorrected) {
                                        FileUtils.deleteQuietly(file.toFile());
                                        correctedCount++;
                                        if (tracker != null) {
                                            tracker.recordSuccess();
                                        }
                                    } else {
                                        uncorrectedCount++;
                                        if (tracker != null) {
                                            tracker.recordWarning();
                                        }
                                    }
                                } else {
                                    this.saveUncorrectedPage(groupId, evaluationId, file.getFileName().toString(), UncorrectedPageSource.BARCODE_ERROR);
                                    uncorrectedCount++;
                                    if (tracker != null) {
                                        tracker.recordWarning();
                                    }
                                }
                            } finally {
                                rotatedImage.release();
                            }
                        }
                    } finally {
                        page.release();
                    }
                } catch (Exception e) {
                    log.error("Échec du traitement de la page : {}", file.getFileName(), e);
                    this.saveUncorrectedPage(groupId, evaluationId, file.getFileName().toString(), UncorrectedPageSource.BARCODE_ERROR);
                    uncorrectedCount++;
                    if (tracker != null) {
                        tracker.recordWarning();
                    }
                }
            }

            if (correctedCount > 0) {
                evaluationRepository.findById(evaluationId).ifPresent(eval -> {
                    if (eval.getState() != EvaluationState.ARCHIVED) {
                        eval.setState(EvaluationState.ARCHIVED);
                        evaluationRepository.save(eval);
                    }
                });
            }

            if (tracker != null) {
                tracker.setComplete("Correction terminée : " + correctedCount + " page(s) corrigée(s), " + uncorrectedCount + " rejetée(s).");
            }
            return new ProcessingResult(total, correctedCount, uncorrectedCount);
        }
    }

    public Optional<Mat> convertImageToBirdEyeView(Mat scaledImage) {
        Mat imageToProcess = scaledImage;
        boolean rotatedLocal = false;
        MatOfPoint2f src = null;
        MatOfPoint2f dst = null;
        Mat warpMat = null;

        try {
            if (scaledImage.cols() > scaledImage.rows()) {
                imageToProcess = new Mat();
                Core.rotate(scaledImage, imageToProcess, Core.ROTATE_90_CLOCKWISE);
                rotatedLocal = true;
            }

            final var roiSize = 200;

            final var cornerData = List.of(
                    new CornerData(new Point(0, 0), new Rect(0, 0, roiSize, roiSize), new ArrayList<>()),
                    new CornerData(new Point(imageToProcess.width(), 0), new Rect(imageToProcess.width() - roiSize, 0, roiSize, roiSize), new ArrayList<>()),
                    new CornerData(new Point(0, imageToProcess.height()), new Rect(0, imageToProcess.height() - roiSize, roiSize, roiSize), new ArrayList<>()),
                    new CornerData(new Point(imageToProcess.width(), imageToProcess.height()), new Rect(imageToProcess.width() - roiSize, imageToProcess.height() - roiSize, roiSize, roiSize), new ArrayList<>())
            );

            for (var corner : cornerData) {
                var roi = new Mat(imageToProcess, corner.rect());
                findMarkInRoi(roi, corner.point(), corner.rect().x, corner.rect().y)
                        .ifPresent(corner.markLocations()::add);
                roi.release();
            }

            Point tl = cornerData.get(0).markLocations().isEmpty() ? null : new Point(cornerData.get(0).markLocations().getFirst().x(), cornerData.get(0).markLocations().getFirst().y());
            Point tr = cornerData.get(1).markLocations().isEmpty() ? null : new Point(cornerData.get(1).markLocations().getFirst().x(), cornerData.get(1).markLocations().getFirst().y());
            Point bl = cornerData.get(2).markLocations().isEmpty() ? null : new Point(cornerData.get(2).markLocations().getFirst().x(), cornerData.get(2).markLocations().getFirst().y());
            Point br = cornerData.get(3).markLocations().isEmpty() ? null : new Point(cornerData.get(3).markLocations().getFirst().x(), cornerData.get(3).markLocations().getFirst().y());

            int count = (tl != null ? 1 : 0) + (tr != null ? 1 : 0) + (bl != null ? 1 : 0) + (br != null ? 1 : 0);
            if (count < 3) {
                return Optional.empty();
            }

            if (count == 3) {
                Point estimated;
                if (tl == null) {
                    if (!validateThreeCornersGeometry(br, bl, tr)) {
                        return Optional.empty();
                    }
                    estimated = new Point(tr.x + bl.x - br.x, tr.y + bl.y - br.y);
                    tl = searchMarkInEstimatedRegion(imageToProcess, estimated);
                } else if (tr == null) {
                    if (!validateThreeCornersGeometry(bl, br, tl)) {
                        return Optional.empty();
                    }
                    estimated = new Point(tl.x + br.x - bl.x, tl.y + br.y - bl.y);
                    tr = searchMarkInEstimatedRegion(imageToProcess, estimated);
                } else if (bl == null) {
                    if (!validateThreeCornersGeometry(tr, tl, br)) {
                        return Optional.empty();
                    }
                    estimated = new Point(tl.x + br.x - tr.x, tl.y + br.y - tr.y);
                    bl = searchMarkInEstimatedRegion(imageToProcess, estimated);
                } else {
                    if (!validateThreeCornersGeometry(tl, tr, bl)) {
                        return Optional.empty();
                    }
                    estimated = new Point(tr.x + bl.x - tl.x, tr.y + bl.y - tl.y);
                    br = searchMarkInEstimatedRegion(imageToProcess, estimated);
                }
            }

            src = new MatOfPoint2f(tl, tr, bl, br);

            var width = PDRectangle.A4.getWidth() - (Setting.margin * 2);
            var height = PDRectangle.A4.getHeight() - (Setting.margin * 2);
            dst = new MatOfPoint2f(
                    new Point(padding, padding),
                    new Point(padding + (width * 2), padding),
                    new Point(padding, padding + (height * 2)),
                    new Point(padding + (width * 2), padding + (height * 2))
            );

            warpMat = Imgproc.getPerspectiveTransform(src, dst);
            var destImage = new Mat();
            Imgproc.warpPerspective(imageToProcess, destImage, warpMat, new Size((width + padding) * 2, (height + padding) * 2));
            return Optional.of(destImage);
        } catch (Exception e) {
            log.error("Erreur lors de la conversion vue perspective", e);
            return Optional.empty();
        } finally {
            if (warpMat != null) warpMat.release();
            if (src != null) src.release();
            if (dst != null) dst.release();
            if (rotatedLocal && imageToProcess != null) imageToProcess.release();
        }
    }

    private Optional<MarkLocation> findMarkInRoi(Mat roi, Point origin, int offsetX, int offsetY) {
        var blurImage = new Mat();
        Imgproc.GaussianBlur(roi, blurImage, new Size(5, 5), 0);

        var binaryImage = new Mat();
        Imgproc.threshold(blurImage, binaryImage, 0, 255, Imgproc.THRESH_BINARY_INV | Imgproc.THRESH_OTSU);
        var mark = extractMarkFromBinary(binaryImage, origin, offsetX, offsetY);

        if (mark.isEmpty()) {
            Imgproc.adaptiveThreshold(blurImage, binaryImage, 255,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 25, 10);
            mark = extractMarkFromBinary(binaryImage, origin, offsetX, offsetY);
        }

        binaryImage.release();
        blurImage.release();
        return mark;
    }

    private Optional<MarkLocation> extractMarkFromBinary(Mat binaryImage, Point origin, int offsetX, int offsetY) {
        List<MatOfPoint> contours = new ArrayList<>();
        var hierarchy = new Mat();
        Imgproc.findContours(binaryImage, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        MarkLocation bestMark = null;
        double minDistance = Double.MAX_VALUE;

        for (var contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area >= 250 && area <= 3500) {
                Rect rect = Imgproc.boundingRect(contour);
                double aspectRatio = (double) rect.width / rect.height;

                if (aspectRatio >= 0.70 && aspectRatio <= 1.40) {
                    double fillRatio = area / (rect.width * rect.height);
                    if (fillRatio >= 0.65) {
                        Moments moments = Imgproc.moments(contour);
                        double cx = (moments.m10 / moments.m00) + offsetX;
                        double cy = (moments.m01 / moments.m00) + offsetY;
                        double distance = Point2D.distance(origin.x, origin.y, cx, cy);

                        if (distance < minDistance) {
                            minDistance = distance;
                            bestMark = new MarkLocation(cx, cy, (rect.width + rect.height) / 2.0);
                        }
                    }
                }
            }
            contour.release();
        }
        hierarchy.release();
        return Optional.ofNullable(bestMark);
    }

    private boolean validateThreeCornersGeometry(Point p1, Point p2, Point p3) {
        double d12 = Point2D.distance(p1.x, p1.y, p2.x, p2.y);
        double d13 = Point2D.distance(p1.x, p1.y, p3.x, p3.y);
        double d23 = Point2D.distance(p2.x, p2.y, p3.x, p3.y);

        double hyp = Math.max(d12, Math.max(d13, d23));
        double sideA, sideB;
        if (hyp == d23) {
            sideA = d12; sideB = d13;
        } else if (hyp == d13) {
            sideA = d12; sideB = d23;
        } else {
            sideA = d13; sideB = d23;
        }

        double ratio = Math.abs((sideA * sideA + sideB * sideB) - (hyp * hyp)) / (hyp * hyp);
        return ratio < 0.25;
    }

    private Point searchMarkInEstimatedRegion(Mat image, Point estimated) {
        int halfSize = 100;
        int x = (int) Math.max(0, Math.min(image.width() - (2 * halfSize), estimated.x - halfSize));
        int y = (int) Math.max(0, Math.min(image.height() - (2 * halfSize), estimated.y - halfSize));

        var roi = new Mat(image, new Rect(x, y, 2 * halfSize, 2 * halfSize));
        var mark = findMarkInRoi(roi, new Point(halfSize, halfSize), x, y);
        roi.release();

        return mark.map(m -> new Point(m.x(), m.y())).orElse(estimated);
    }

    private boolean isBlankPage(Mat image) {
        Scalar mean = Core.mean(image);
        return mean.val[0] > 248.0;
    }

    public Optional<Barcode> detectTheBarCode(Mat page) {
        int y = Math.max(0, (int) (page.rows() * 0.84));
        int h = page.rows() - y;
        int x = 0;
        int w = Math.min(page.cols(), (int) (page.cols() * 0.52));

        var roi = new Mat(page, new Rect(x, y, w, h));
        var resized = new Mat();
        Imgproc.resize(roi, resized, new Size(), 1.5, 1.5, Imgproc.INTER_LINEAR);

        try {
            var text = decodePdf417(resized);
            if (text.isPresent()) {
                var barcode = parseBarcode(text.get());
                if (barcode.isPresent()) {
                    return barcode;
                }
            }

            for (double angle : List.of(-3.0, 3.0, -6.0, 6.0, -10.0, 10.0)) {
                var rotated = rotateImage(resized, angle);
                try {
                    text = decodePdf417(rotated);
                    if (text.isPresent()) {
                        var barcode = parseBarcode(text.get());
                        if (barcode.isPresent()) {
                            return barcode;
                        }
                    }
                } finally {
                    rotated.release();
                }
            }
        } finally {
            resized.release();
            roi.release();
        }

        return Optional.empty();
    }

    public Optional<Barcode> parseBarcode(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String clean = text.trim();
        int lastDash = clean.lastIndexOf('-');
        if (lastDash > 0 && lastDash < clean.length() - 1) {
            String blueprintId = clean.substring(0, lastDash);
            String pageStr = clean.substring(lastDash + 1);
            try {
                int pageNumber = Integer.parseInt(pageStr);
                return Optional.of(new Barcode(blueprintId, pageNumber));
            } catch (NumberFormatException e) {
                log.error("Numéro de page invalide dans le code-barres : '{}'", text);
            }
        }
        return Optional.empty();
    }

    private static Mat rotateImage(Mat src, double angle) {
        Point center = new Point(src.cols() / 2.0, src.rows() / 2.0);
        Mat rotMatrix = Imgproc.getRotationMatrix2D(center, angle, 1.0);
        Mat dst = new Mat();
        Imgproc.warpAffine(src, dst, rotMatrix, src.size(), Imgproc.INTER_LINEAR);
        rotMatrix.release();
        return dst;
    }

    private Optional<String> decodePdf417(Mat mat) {
        try {
            int width = mat.cols();
            int height = mat.rows();
            byte[] buffer = new byte[width * height];
            mat.get(0, 0, buffer);

            var source = new GrayscaleLuminanceSource(width, height, buffer);
            var image = new BinaryBitmap(new HybridBinarizer(source));

            try {
                var result = new PDF417Reader().decode(image, this.hints);
                return Optional.of(result.getText());
            } catch (com.google.zxing.NotFoundException | ChecksumException | FormatException ignored) {
            }

            image = new BinaryBitmap(new GlobalHistogramBinarizer(source));
            try {
                var result = new PDF417Reader().decode(image, this.hints);
                return Optional.of(result.getText());
            } catch (com.google.zxing.NotFoundException | ChecksumException | FormatException ignored) {
            }

            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private boolean correctTheQuestions(String groupId, String evaluationId, String fileName, Path evalDir, Mat page, Barcode barcode) {
        var opBlueprintAssessment = blueprintPaperAssessmentRepository.findByIdAndGroupId(barcode.blueprintAssessmentId(), groupId);
        if (opBlueprintAssessment.isPresent()) {
            var blueprintAssessment = opBlueprintAssessment.get();

            var opBlueprintPage = blueprintAssessment.blueprintPages.stream()
                    .filter(blueprintPage -> blueprintPage.number != null && blueprintPage.number == barcode.pageNumber())
                    .findFirst();

            if (opBlueprintPage.isPresent()) {
                var blueprintPage = opBlueprintPage.get();

                List<Mat> crops = new ArrayList<>();
                List<BlueprintAnswer> allAnswers = new ArrayList<>();

                for (var blueprintQuestion : blueprintPage.blueprintQuestions) {
                    for (var blueprintAnswer : blueprintQuestion.blueprintAnswers) {
                        final int x = (int) Math.round(getX(blueprintAnswer) - (28 - (Setting.checkBox * 2)) / 2);
                        final int y = (int) Math.round(getY(blueprintAnswer) - (28 - (Setting.checkBox * 2)) / 2);
                        crops.add(safeCrop28(page, x, y));
                        allAnswers.add(blueprintAnswer);
                    }
                }

                try {
                    int[] predictedClasses = checkboxClassifier.predictClasses(crops);
                    for (int i = 0; i < allAnswers.size(); i++) {
                        var blueprintAnswer = allAnswers.get(i);
                        int predictedClassId = predictedClasses[i];
                        blueprintAnswer.response = checkboxClassifier.isChecked(predictedClassId);
                    }
                } finally {
                    crops.forEach(Mat::release);
                }

                for (var blueprintQuestion : blueprintPage.blueprintQuestions) {
                    blueprintQuestion.markAsCorrectedAndSetQuestionState();
                }
                blueprintPage.markAsCorrected();
                blueprintPaperAssessmentRepository.save(blueprintAssessment);

                markOnThePageTheQuestionsStateAndSave(evalDir, page, blueprintPage);
            } else {
                this.saveUncorrectedPage(groupId, evaluationId, fileName, UncorrectedPageSource.PAGE_NOT_BELONG);
                return false;
            }
        } else {
            this.saveUncorrectedPage(groupId, evaluationId, fileName, UncorrectedPageSource.PAGE_NOT_BELONG);
            return false;
        }
        return true;
    }

    private static float getY(BlueprintAnswer blueprintAnswer) {
        return (PDRectangle.A4.getHeight() - blueprintAnswer.y - Setting.margin - (Setting.checkBox / 2)) * 2 + Setting.padding;
    }

    private static float getX(BlueprintAnswer blueprintAnswer) {
        return (blueprintAnswer.x - Setting.margin - (Setting.checkBox / 2)) * 2 + Setting.padding;
    }

    private Mat safeCrop28(Mat page, int x, int y) {
        if (x >= 0 && y >= 0 && x + 28 <= page.cols() && y + 28 <= page.rows()) {
            return page.submat(new Rect(x, y, 28, 28));
        }
        Mat fallback = new Mat(28, 28, CvType.CV_8UC1, new Scalar(255));
        int srcX = Math.max(0, Math.min(x, page.cols()));
        int srcY = Math.max(0, Math.min(y, page.rows()));
        int srcW = Math.max(0, Math.min(28 - (srcX - x), page.cols() - srcX));
        int srcH = Math.max(0, Math.min(28 - (srcY - y), page.rows() - srcY));

        if (srcW > 0 && srcH > 0) {
            int dstX = Math.max(0, srcX - x);
            int dstY = Math.max(0, srcY - y);
            Mat srcRoi = page.submat(new Rect(srcX, srcY, srcW, srcH));
            Mat dstRoi = fallback.submat(new Rect(dstX, dstY, srcW, srcH));
            srcRoi.copyTo(dstRoi);
            srcRoi.release();
            dstRoi.release();
        }
        return fallback;
    }

    private void markOnThePageTheQuestionsStateAndSave(Path evalDir, Mat result, BlueprintPage blueprintPage) {
        var colorMat = new Mat();
        Imgproc.cvtColor(result, colorMat, Imgproc.COLOR_GRAY2RGB);

        blueprintPage.blueprintQuestions.forEach(question -> {
            question.blueprintAnswers.sort(Comparator.comparing(blueprintAnswer -> blueprintAnswer.y));
            final var x = getX(question.blueprintAnswers.getFirst()) - 12;
            final var start = getY(question.blueprintAnswers.getFirst()) + Setting.checkBox * 2 + 6;
            final var end = getY(question.blueprintAnswers.getLast()) - 6;
            var state = question.state != null ? question.state : tk.tahri.eval.desktop.document.entities.blueprint.QuestionState.EMPTY;
            switch (state) {
                case CORRECT -> drawLineAndMark(colorMat, x, start, end, new Scalar(0, 255, 0), question.scoreIfCorrect);
                case EMPTY -> drawLineAndMark(colorMat, x, start, end, new Scalar(0, 140, 255), question.scoreIfEmpty);
                case INCORRECT -> drawLineAndMark(colorMat, x, start, end, new Scalar(0, 0, 255), question.scoreIfIncorrect);
            }
        });

        final var pagePath = evalDir.resolve("corrected").resolve(blueprintPage.id + ".jpg");
        Imgcodecs.imwrite(pagePath.toString(), colorMat);
        colorMat.release();
    }

    private void drawLineAndMark(Mat colorMat, double x, double start, double end, Scalar color, double mark) {
        final double fontSize = 0.5;
        final int thickness = 2;
        final int font = Imgproc.FONT_ITALIC;

        Imgproc.line(colorMat, new Point(x, start), new Point(x, end), color, 3);
        var strMark = String.format("%.2f", mark).replace(".00", "");
        if (!strMark.equals("0")) {
            if (strMark.endsWith("0")) strMark = strMark.substring(0, strMark.length() - 1);
        }

        var size = Imgproc.getTextSize(strMark, font, fontSize, thickness, null);
        Imgproc.putText(colorMat, strMark, new Point(x - size.width - 4, (start + end) / 2 + size.height / 2), font, fontSize, color, thickness);
    }

    private void saveUncorrectedPage(String groupId, String evaluationId, String fileName, UncorrectedPageSource message) {
        var uncorrectedPage = new UncorrectedPage(groupId, evaluationId, fileName, message);
        this.uncorrectedPageService.save(uncorrectedPage);
    }

    private static final class GrayscaleLuminanceSource extends com.google.zxing.LuminanceSource {
        private final byte[] data;

        GrayscaleLuminanceSource(int width, int height, byte[] data) {
            super(width, height);
            this.data = data;
        }

        @Override
        public byte[] getRow(int y, byte[] row) {
            if (row == null || row.length < getWidth()) {
                row = new byte[getWidth()];
            }
            System.arraycopy(data, y * getWidth(), row, 0, getWidth());
            return row;
        }

        @Override
        public byte[] getMatrix() {
            return data;
        }
    }
}