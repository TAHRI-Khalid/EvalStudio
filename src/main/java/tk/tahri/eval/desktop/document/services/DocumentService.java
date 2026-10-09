package tk.tahri.eval.desktop.document.services;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tk.tahri.eval.desktop.document.SharedAssessmentData;
import tk.tahri.eval.desktop.document.dto.StudentDetails;
import tk.tahri.eval.desktop.document.dto.TaskProgress;
import tk.tahri.eval.desktop.document.dto.TaskProgressTracker;
import tk.tahri.eval.desktop.document.entities.Setting;
import tk.tahri.eval.desktop.document.entities.blueprint.AssessmentStudent;
import tk.tahri.eval.desktop.document.entities.blueprint.BlueprintPaperAssessment;
import tk.tahri.eval.desktop.document.reports.CorrectionReport;
import tk.tahri.eval.desktop.document.reports.OneColumnAssessment;
import tk.tahri.eval.desktop.document.reports.TwoColumnAssessment;
import tk.tahri.eval.desktop.document.repositories.BlueprintPaperAssessmentRepository;
import tk.tahri.eval.desktop.exception.InvalidEvaluationStateException;
import tk.tahri.eval.desktop.exception.NotFoundException;
import tk.tahri.eval.desktop.model.Group;
import tk.tahri.eval.desktop.model.GroupRole;
import tk.tahri.eval.desktop.model.Evaluation;
import tk.tahri.eval.desktop.model.EvaluationState;
import tk.tahri.eval.desktop.repository.GroupRepository;
import tk.tahri.eval.desktop.repository.EnrollmentRepository;
import tk.tahri.eval.desktop.repository.EvaluationRepository;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final EvaluationRepository evaluationRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final GroupRepository groupRepository;
    private final BlueprintPaperAssessmentRepository blueprintPaperAssessmentRepository;
    private final GradeService gradeService;

    @Value("${eval.document.upload-dir:upload}")
    private String uploadDirPath;

    public DocumentService(EvaluationRepository evaluationRepository,
                           EnrollmentRepository enrollmentRepository,
                           GroupRepository groupRepository,
                           BlueprintPaperAssessmentRepository blueprintPaperAssessmentRepository,
                           GradeService gradeService) {
        this.evaluationRepository = evaluationRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.groupRepository = groupRepository;
        this.blueprintPaperAssessmentRepository = blueprintPaperAssessmentRepository;
        this.gradeService = gradeService;
    }

    public Path evaluationDir(String evaluationId) {
        return Path.of(uploadDirPath, "evaluations", evaluationId);
    }

    private PDType0Font loadFont(PDDocument document) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fonts/" + Setting.font)) {
            if (in == null) {
                File fontFile = new File("src/main/resources/fonts/" + Setting.font);
                if (fontFile.exists()) {
                    return PDType0Font.load(document, fontFile);
                }
                throw new IOException("Font not found in classpath: /fonts/" + Setting.font);
            }
            return PDType0Font.load(document, in, true);
        }
    }

    private record EvaluationData(Evaluation evaluation, Group group) {
    }

    private EvaluationData loadEvaluationData(String groupId, String evaluationId) {
        Evaluation evaluation = evaluationRepository.findById(evaluationId)
                .filter(e -> groupId.equals(e.groupId()))
                .orElseThrow(() -> new NotFoundException("Évaluation introuvable : " + evaluationId));
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("Groupe introuvable : " + groupId));
        return new EvaluationData(evaluation, group);
    }

    @Transactional
    public File generateEvaluation(String groupId, String evaluationId) throws Exception {
        return generateEvaluation(groupId, evaluationId, (Consumer<TaskProgress>) null);
    }

    @Transactional
    public File generateEvaluation(String groupId, String evaluationId, Consumer<TaskProgress> progressConsumer) throws Exception {
        TaskProgressTracker tracker = progressConsumer != null ? new TaskProgressTracker(progressConsumer) : null;
        return generateEvaluation(groupId, evaluationId, tracker);
    }

    private File generateEvaluation(String groupId, String evaluationId, TaskProgressTracker tracker) throws Exception {
        EvaluationData data = loadEvaluationData(groupId, evaluationId);

        if (data.evaluation().questions() == null || data.evaluation().questions().isEmpty()) {
            throw new InvalidEvaluationStateException("L'évaluation ne contient aucune question");
        }

        var enrollments = enrollmentRepository.findByGroupIdAndRoleAndEnableTrueOrderByLastNameAsc(groupId, GroupRole.STUDENT);
        if (enrollments.isEmpty()) {
            throw new InvalidEvaluationStateException("Ce groupe ne comporte aucun étudiant inscrit");
        }

        int total = enrollments.size();
        if (tracker != null) {
            tracker.setStage("Phase 1/2 : Rendu des sujets avec codes-barres", total, 0.0, 0.85);
        }

        final var document = new PDDocument();
        final var font = loadFont(document);
        final var sharedAssessmentData = new SharedAssessmentData(document, false, data.evaluation(), data.group(), font);

        var blueprintAssessments = new ArrayList<BlueprintPaperAssessment>();
        var pdfBytes = new ByteArrayOutputStream();
        try {
            int current = 0;
            for (var enrollment : enrollments) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Génération interrompue par l'utilisateur.");
                }
                current++;
                var student = new AssessmentStudent(
                        enrollment.getId(), enrollment.getFirstName(), enrollment.getLastName(), enrollment.getMatriculation());

                if (data.evaluation().columnCount() == 2) {
                    var assessmentPages = new TwoColumnAssessment(sharedAssessmentData, student);
                    assessmentPages.addPagesToDocument();
                    blueprintAssessments.add(assessmentPages.getBlueprintAssessment());
                } else {
                    var assessmentPages = new OneColumnAssessment(sharedAssessmentData, student);
                    assessmentPages.addPagesToDocument();
                    blueprintAssessments.add(assessmentPages.getBlueprintAssessment());
                }

                if (tracker != null) {
                    String studentName = (enrollment.getLastName() != null ? enrollment.getLastName() : "") + " " +
                            (enrollment.getFirstName() != null ? enrollment.getFirstName() : "");
                    tracker.updateStep(current, "Sujet " + current + " / " + total + " : " + studentName.trim());
                }
            }

            if (tracker != null) {
                tracker.setStage("Phase 2/2 : Finalisation et enregistrement du fichier PDF", 1, 0.85, 0.15);
                tracker.updateStep(1, "Compression et écriture du PDF sur disque...");
            }
            document.save(pdfBytes);
        } finally {
            document.close();
        }

        var root = evaluationDir(evaluationId);
        Files.createDirectories(root);
        Files.createDirectories(root.resolve("pages"));
        Files.createDirectories(root.resolve("files"));
        Files.createDirectories(root.resolve("corrected"));

        Evaluation eval = data.evaluation();
        eval.setState(EvaluationState.PRINTED);
        evaluationRepository.save(eval);

        blueprintPaperAssessmentRepository.deleteByGroupIdAndEvaluationId(groupId, evaluationId);
        blueprintPaperAssessmentRepository.saveAll(blueprintAssessments);

        Path pdfFile = root.resolve("eval_" + evaluationId + ".pdf");
        Files.write(pdfFile, pdfBytes.toByteArray());
        log.info("PDF d'évaluation généré pour {} ({} étudiants) dans {}", evaluationId, enrollments.size(), pdfFile.toAbsolutePath());

        if (tracker != null) {
            tracker.setComplete("Génération terminée avec succès (" + total + " sujets générés).");
        }
        return pdfFile.toFile();
    }

    public byte[] generateDraftEvaluation(String groupId, String evaluationId) throws Exception {
        EvaluationData data = loadEvaluationData(groupId, evaluationId);

        var enrollments = new ArrayList<>(enrollmentRepository.findByGroupIdAndRoleAndEnableTrueOrderByLastNameAsc(groupId, GroupRole.STUDENT));
        AssessmentStudent student;
        if (enrollments.isEmpty()) {
            student = new AssessmentStudent("0", "Exemple", "Étudiant", "ETU001");
        } else {
            java.util.Collections.shuffle(enrollments);
            var enrollment = enrollments.getFirst();
            student = new AssessmentStudent(enrollment.getId(), enrollment.getFirstName(), enrollment.getLastName(), enrollment.getMatriculation());
        }

        final var document = new PDDocument();
        final var font = loadFont(document);
        final var sharedAssessmentData = new SharedAssessmentData(document, true, data.evaluation(), data.group(), font);

        try {
            if (data.evaluation().columnCount() == 2) {
                new TwoColumnAssessment(sharedAssessmentData, student).addPagesToDocument();
            } else {
                new OneColumnAssessment(sharedAssessmentData, student).addPagesToDocument();
            }
            var byteArrayOutputStream = new ByteArrayOutputStream();
            document.save(byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        } finally {
            document.close();
        }
    }

    public File getGeneratedPdfFile(String evaluationId) {
        Path file = evaluationDir(evaluationId).resolve("eval_" + evaluationId + ".pdf");
        return Files.exists(file) ? file.toFile() : null;
    }

    public byte[] generatePDFPaperAssessments(String groupId, String evaluationId) throws IOException {
        loadEvaluationData(groupId, evaluationId);

        var assessments = blueprintPaperAssessmentRepository.findByGroupIdAndEvaluationId(groupId, evaluationId);
        if (assessments.isEmpty()) {
            throw new NotFoundException("Aucune copie papier trouvée pour l'évaluation : " + evaluationId);
        }

        var images = assessments.stream()
                .flatMap(assessment -> assessment.blueprintPages.stream())
                .map(blueprintPage -> blueprintPage.id + ".jpg")
                .toList();

        return imagesToPdf(evaluationId, images);
    }

    public byte[] generatePDFPaperAssessment(String groupId, String evaluationId, String assessmentId) throws IOException {
        loadEvaluationData(groupId, evaluationId);

        var assessment = blueprintPaperAssessmentRepository.findByIdAndGroupId(assessmentId, groupId)
                .or(() -> blueprintPaperAssessmentRepository.findById(assessmentId))
                .orElseThrow(() -> new NotFoundException("Copie introuvable pour l'étudiant : " + assessmentId));

        var images = assessment.blueprintPages.stream()
                .map(blueprintPage -> blueprintPage.id + ".jpg")
                .toList();

        return imagesToPdf(evaluationId, images);
    }

    private byte[] imagesToPdf(String evaluationId, List<String> imageNames) throws IOException {
        var root = evaluationDir(evaluationId).resolve("corrected");
        if (!Files.exists(root)) {
            throw new NotFoundException("Le répertoire des copies corrigées n'existe pas. Veuillez d'abord lancer la correction optique.");
        }

        final var document = new PDDocument();
        try {
            for (var imageName : imageNames) {
                var file = root.resolve(imageName).toFile();
                if (file.exists()) {
                    var page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    try (var canvas = new PDPageContentStream(document, page)) {
                        var pdImageXObject = org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromFileByExtension(file, document);
                        canvas.drawImage(pdImageXObject, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
                    }
                }
            }
            if (document.getNumberOfPages() == 0) {
                throw new NotFoundException("Aucune page corrigée n'a été trouvée pour cette copie.");
            }
            var byteArrayOutputStream = new ByteArrayOutputStream();
            document.save(byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        } finally {
            document.close();
        }
    }

    public byte[] generatePDFCorrectionReport(String groupId, String evaluationId) throws Exception {
        EvaluationData data = loadEvaluationData(groupId, evaluationId);
        List<StudentDetails> studentDetailsList = gradeService.findStudentDetails(groupId, evaluationId);

        final var document = new PDDocument();
        final var font = loadFont(document);

        try {
            final var report = new CorrectionReport(data.evaluation(), data.group(), studentDetailsList, document, font);
            report.process();

            var byteArrayOutputStream = new ByteArrayOutputStream();
            document.save(byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        } finally {
            document.close();
        }
    }

    public void exportGradesToExcel(String groupId, String evaluationId, File targetFile) throws Exception {
        var data = loadEvaluationData(groupId, evaluationId);
        var studentDetailsList = gradeService.findStudentDetails(groupId, evaluationId);
        var questionStatsList = gradeService.computeQuestionStatistics(
                data.evaluation(),
                blueprintPaperAssessmentRepository.findByGroupIdAndEvaluationId(groupId, evaluationId)
        );

        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Notes");

            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("ID");
            row.createCell(1).setCellValue("Nom");
            row.createCell(2).setCellValue("Prénom");
            row.createCell(3).setCellValue("Note");

            int i = 1;
            for (var student : studentDetailsList) {
                row = sheet.createRow(i);
                row.createCell(0).setCellValue(student.matriculation() == null ? "" : student.matriculation());
                row.createCell(1).setCellValue(student.lastName());
                row.createCell(2).setCellValue(student.firstName());
                row.createCell(3).setCellValue(student.score());
                i++;
            }

            if (questionStatsList != null && !questionStatsList.isEmpty()) {
                var qSheet = workbook.createSheet("Analyse Questions");
                var qHeader = qSheet.createRow(0);
                qHeader.createCell(0).setCellValue("N° Question");
                qHeader.createCell(1).setCellValue("Énoncé");
                qHeader.createCell(2).setCellValue("Réponses correctes");
                qHeader.createCell(3).setCellValue("Non répondu");
                qHeader.createCell(4).setCellValue("Mauvaises réponses");
                qHeader.createCell(5).setCellValue("Total copies");
                qHeader.createCell(6).setCellValue("Taux de réussite (%)");

                int qi = 1;
                for (var qs : questionStatsList) {
                    var qRow = qSheet.createRow(qi++);
                    qRow.createCell(0).setCellValue("Q" + qs.questionNumber());
                    qRow.createCell(1).setCellValue(qs.content());
                    qRow.createCell(2).setCellValue(qs.correctCount());
                    qRow.createCell(3).setCellValue(qs.emptyCount());
                    qRow.createCell(4).setCellValue(qs.incorrectCount());
                    qRow.createCell(5).setCellValue(qs.totalEvaluated());
                    qRow.createCell(6).setCellValue(qs.successRate());
                }
            }

            try (var fos = new java.io.FileOutputStream(targetFile)) {
                workbook.write(fos);
            }
        }
    }
}
