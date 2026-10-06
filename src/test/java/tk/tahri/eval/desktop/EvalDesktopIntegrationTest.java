package tk.tahri.eval.desktop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tk.tahri.eval.desktop.document.repositories.BlueprintPaperAssessmentRepository;
import tk.tahri.eval.desktop.document.services.DocumentService;
import tk.tahri.eval.desktop.model.*;
import tk.tahri.eval.desktop.repository.GroupRepository;
import tk.tahri.eval.desktop.repository.EnrollmentRepository;
import tk.tahri.eval.desktop.repository.EvaluationRepository;
import tk.tahri.eval.desktop.exception.InvalidEvaluationStateException;
import tk.tahri.eval.desktop.service.GroupService;
import tk.tahri.eval.desktop.service.EvaluationService;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = EvalDesktopApplication.class)
@ActiveProfiles("test")
public class EvalDesktopIntegrationTest {

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupService groupService;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private EvaluationService evaluationService;

    @Autowired
    private EvaluationRepository evaluationRepository;

    @Autowired
    private BlueprintPaperAssessmentRepository blueprintPaperAssessmentRepository;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private tk.tahri.eval.desktop.document.services.DocumentProcessingService documentProcessingService;

    @Autowired
    private tk.tahri.eval.desktop.document.services.GradeService gradeService;

    @Autowired
    private tk.tahri.eval.desktop.service.ImageStorageService imageStorageService;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @Test
    @DisplayName("Vérifie la résolution unique des contrôleurs UI dans le contexte Spring")
    void testControllerBeansUniqueness() {
        assertDoesNotThrow(() -> {
            var groupsCtrl = applicationContext.getBean(tk.tahri.eval.desktop.ui.controllers.GroupsViewController.class);
            assertNotNull(groupsCtrl);
            var workspaceCtrl = applicationContext.getBean(tk.tahri.eval.desktop.ui.controllers.GroupWorkspaceViewController.class);
            assertNotNull(workspaceCtrl);
        }, "Les contrôleurs GroupsViewController et GroupWorkspaceViewController doivent être uniques dans le contexte Spring");
    }

    @Test
    @DisplayName("Vérifie la persistance H2 de Group avec nom et établissement, le mapping JSON et la génération PDF des évaluations")
    void testCompleteEvaluationWorkflow() throws Exception {
        // 1. Création d'un groupe avec nom et établissement
        Group group = new Group();
        group.setName("Architecture des Ordinateurs");
        group.setEtablissement("Université Hassan II");
        group.setSection("Informatique");
        group.setYear("2025/2026");
        group = groupService.saveGroup(group, null);

        assertNotNull(group.getId(), "L'ID du groupe ne doit pas être null");
        assertEquals("Architecture des Ordinateurs", group.getName());
        assertEquals("Université Hassan II", group.getEtablissement());

        // 2. Inscription d'étudiants
        Enrollment student1 = new Enrollment(null, group.getId(), "Jean", "Dupont", "ST1001", GroupRole.STUDENT);
        Enrollment student2 = new Enrollment(null, group.getId(), "Sophie", "Martin", "ST1002", GroupRole.STUDENT);
        enrollmentRepository.saveAll(List.of(student1, student2));

        List<Enrollment> enrolledStudents = enrollmentRepository.findByGroupIdAndRole(group.getId(), GroupRole.STUDENT);
        assertEquals(2, enrolledStudents.size(), "Il doit y avoir 2 étudiants inscrits");

        // 3. Création d'une évaluation avec type "Contrôle Continu" ou "Évaluation de Fin de Module"
        Evaluation eval = evaluationService.createEvaluation(group.getId(), "Contrôle Continu 1", Evaluation.TYPE_CONTROLE_CONTINU);
        assertNotNull(eval.getId(), "L'ID de l'évaluation ne doit pas être null");
        assertEquals(EvaluationState.CONCEPTION, eval.getState());
        assertEquals(Evaluation.TYPE_CONTROLE_CONTINU, eval.getType());

        // 4. Ajout de questions avec options et image d'illustration
        java.awt.image.BufferedImage testImg = new java.awt.image.BufferedImage(100, 60, java.awt.image.BufferedImage.TYPE_INT_RGB);
        File tempImgFile = File.createTempFile("test_q_img", ".png");
        javax.imageio.ImageIO.write(testImg, "PNG", tempImgFile);
        String storedImgName = imageStorageService.storeImage(tempImgFile, tk.tahri.eval.desktop.service.ImageStorageService.ImageFolder.QUESTION_IMAGES);
        tempImgFile.delete();
        assertNotNull(storedImgName, "L'image de question doit être stockée avec succès");

        Question q1 = new Question(
                UUID.randomUUID().toString(),
                "Quelle est la base du système binaire ?",
                storedImgName,
                false,
                1.5,
                0.0,
                0.0,
                List.of(
                        new Answer(UUID.randomUUID().toString(), "Base 2", true),
                        new Answer(UUID.randomUUID().toString(), "Base 8", false),
                        new Answer(UUID.randomUUID().toString(), "Base 10", false),
                        new Answer(UUID.randomUUID().toString(), "Base 16", false)
                )
        );

        Question q2 = new Question(
                UUID.randomUUID().toString(),
                "Parmi les composants suivants, lesquels font partie de la mémoire ou du stockage ?",
                null,
                true,
                2.5,
                0.0,
                0.0,
                List.of(
                        new Answer(UUID.randomUUID().toString(), "DRAM", true),
                        new Answer(UUID.randomUUID().toString(), "SSD NVMe", true),
                        new Answer(UUID.randomUUID().toString(), "Alimentation ATX", false)
                )
        );

        eval = evaluationService.addQuestion(eval.getId(), q1);
        eval = evaluationService.addQuestion(eval.getId(), q2);

        // 5. Rechargement depuis H2 (vérifie la persistance et la désérialisation JSON via @JdbcTypeCode)
        Evaluation reloadedEval = evaluationRepository.findById(eval.getId()).orElseThrow();
        assertEquals(2, reloadedEval.getQuestions().size(), "L'évaluation doit avoir 2 questions persistées en JSON dans H2");
        assertEquals("Quelle est la base du système binaire ?", reloadedEval.getQuestions().get(0).content());
        assertEquals(storedImgName, reloadedEval.getQuestions().get(0).imagePath(), "Le chemin de l'image de la question doit être persisté");
        assertEquals(4, reloadedEval.getQuestions().get(0).answers().size());
        assertTrue(reloadedEval.getQuestions().get(0).answers().get(0).correct());

        // 6. Test de la génération de sujet brouillon PDF (PDFBox + mise en page + font + QR/barcodes)
        // Test avec un logoUrl configuré mais fichier manquant (ne doit pas lever IIOException)
        group.setLogoUrl("non_existent_logo.png");
        groupRepository.save(group);
        byte[] draftPdf = documentService.generateDraftEvaluation(group.getId(), reloadedEval.getId());
        assertNotNull(draftPdf, "Le PDF du sujet généré ne doit pas être null même si le logo est manquant");
        assertTrue(draftPdf.length > 1000, "Le PDF généré doit contenir des données valides (> 1KB)");

        // 7. Test de génération complète pour tous les étudiants (création des blueprints avec code-barre)
        File generatedPdf = documentService.generateEvaluation(group.getId(), reloadedEval.getId(), null);
        assertNotNull(generatedPdf);
        assertTrue(generatedPdf.exists());
        assertTrue(generatedPdf.length() > 2000, "Le PDF d'examen complet doit contenir les copies nominatives");

        var blueprints = blueprintPaperAssessmentRepository.findByGroupIdAndEvaluationId(group.getId(), reloadedEval.getId());
        assertEquals(2, blueprints.size(), "Un blueprint d'évaluation doit être généré pour chacun des 2 étudiants");

        // 7b. Vérification du verrouillage : une évaluation PRINTED ne peut plus être modifiée
        Evaluation printedEval = evaluationRepository.findById(reloadedEval.getId()).orElseThrow();
        assertEquals(EvaluationState.PRINTED, printedEval.getState());
        assertThrows(InvalidEvaluationStateException.class, () -> {
            evaluationService.addQuestion(printedEval.getId(), new Question(UUID.randomUUID().toString(), "Question interdite", null, false, 1.0, 0.0, 0.0, List.of()));
        }, "L'ajout d'une question sur une évaluation PRINTED doit lever InvalidEvaluationStateException");
        assertThrows(InvalidEvaluationStateException.class, () -> {
            evaluationService.saveEvaluation(printedEval);
        }, "La sauvegarde directe d'une évaluation PRINTED doit lever InvalidEvaluationStateException");

        // 8. Test de la correction optique (OMR + OpenCV + ZXing + ONNX + suppression pages non corrigées)
        var scanResult = documentProcessingService.processScanFile(group.getId(), reloadedEval.getId(), generatedPdf, null, null);
        assertNotNull(scanResult, "Le résultat du scan ne doit pas être null");
        assertTrue(scanResult.totalPages() >= 2, "Le fichier scan doit contenir au moins 2 pages");
        assertEquals(2, scanResult.correctedPages() + scanResult.uncorrectedPages(), "Les 2 pages doivent être traitées sans exception transactionnelle");

        // 8b. Vérification du verrouillage : une évaluation ARCHIVED ne peut plus être modifiée
        Evaluation archivedEval = evaluationRepository.findById(reloadedEval.getId()).orElseThrow();
        assertEquals(EvaluationState.ARCHIVED, archivedEval.getState());
        assertThrows(InvalidEvaluationStateException.class, () -> {
            evaluationService.addQuestion(archivedEval.getId(), new Question(UUID.randomUUID().toString(), "Question interdite", null, false, 1.0, 0.0, 0.0, List.of()));
        }, "L'ajout d'une question sur une évaluation ARCHIVED doit lever InvalidEvaluationStateException");

        // 9. Vérification de la consolidation des notes et de la copie corrigée individuelle
        var grades = gradeService.getGrades(group.getId(), reloadedEval.getId());
        assertNotNull(grades);
        assertEquals(2, grades.students().size(), "Il doit y avoir les notes consolidées pour les 2 étudiants");

        for (var studentGrade : grades.students()) {
            if (studentGrade.correctedPages() > 0) {
                byte[] studentCopyPdf = documentService.generatePDFPaperAssessment(group.getId(), reloadedEval.getId(), studentGrade.assessmentId());
                assertNotNull(studentCopyPdf, "Le PDF de copie individuelle de l'étudiant ne doit pas être null");
                assertTrue(studentCopyPdf.length > 500, "Le PDF de copie individuelle doit contenir des données valides");
            }
        }

        // 10. Test de suppression en cascade de l'évaluation
        Path evalDir = Path.of("upload", "evaluations", reloadedEval.getId());
        assertTrue(Files.exists(evalDir), "Le répertoire des artefacts doit exister avant la suppression");

        evaluationService.deleteEvaluation(reloadedEval.getId());
        assertTrue(evaluationRepository.findById(reloadedEval.getId()).isEmpty(), "L'évaluation doit être supprimée de la base");
        assertTrue(blueprintPaperAssessmentRepository.findByGroupIdAndEvaluationId(group.getId(), reloadedEval.getId()).isEmpty(),
                "Tous les blueprints associés doivent être supprimés en cascade de la base");
        assertFalse(Files.exists(evalDir), "Le répertoire des artefacts sur le disque doit être supprimé");

        Path qImgPath = imageStorageService.getImagePath(storedImgName, tk.tahri.eval.desktop.service.ImageStorageService.ImageFolder.QUESTION_IMAGES, false);
        if (qImgPath != null) {
            Files.deleteIfExists(qImgPath);
        }

        System.out.println(">>> Test réussi avec succès ! Groupe : " + group.getName() + " (" + group.getEtablissement() + ")");
    }
}
