package tk.tahri.eval.desktop.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tk.tahri.eval.desktop.model.Answer;
import tk.tahri.eval.desktop.model.Question;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QuestionExcelServiceTest {

    private QuestionExcelService service;

    @BeforeEach
    void setUp() {
        service = new QuestionExcelService();
    }

    @Test
    void testExportAndImportRoundTrip(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("questions_test.xlsx").toFile();

        // 1. Question à 2 choix (choix unique)
        Question q1 = new Question(
                UUID.randomUUID().toString(),
                "Le ciel est bleu.",
                null,
                false,
                1.0, 0.0, 0.0,
                List.of(
                        new Answer(UUID.randomUUID().toString(), "Vrai", true),
                        new Answer(UUID.randomUUID().toString(), "Faux", false)
                )
        );

        // 2. Question à 7 choix (choix multiple)
        Question q2 = new Question(
                UUID.randomUUID().toString(),
                "Sélectionnez les jours du week-end :",
                null,
                true,
                2.0, 0.0, -1.0,
                List.of(
                        new Answer(UUID.randomUUID().toString(), "Lundi", false),
                        new Answer(UUID.randomUUID().toString(), "Mardi", false),
                        new Answer(UUID.randomUUID().toString(), "Mercredi", false),
                        new Answer(UUID.randomUUID().toString(), "Jeudi", false),
                        new Answer(UUID.randomUUID().toString(), "Vendredi", false),
                        new Answer(UUID.randomUUID().toString(), "Samedi", true),
                        new Answer(UUID.randomUUID().toString(), "Dimanche", true)
                )
        );

        // Export
        service.exportQuestionsToExcel(List.of(q1, q2), file);
        assertTrue(file.exists());
        assertTrue(file.length() > 0);

        // Import
        try (FileInputStream fis = new FileInputStream(file)) {
            List<Question> imported = service.parseQuestionsFromExcel(fis, 1.5, 0.0, -0.5);

            assertEquals(2, imported.size());

            // Vérification Question 1
            Question res1 = imported.get(0);
            assertEquals("Le ciel est bleu.", res1.content());
            assertFalse(res1.multipleChoice());
            assertEquals(1.5, res1.scoreIfCorrect());
            assertEquals(2, res1.answers().size());
            assertEquals("Vrai", res1.answers().get(0).content());
            assertTrue(res1.answers().get(0).correct());
            assertEquals("Faux", res1.answers().get(1).content());
            assertFalse(res1.answers().get(1).correct());

            // Vérification Question 2
            Question res2 = imported.get(1);
            assertEquals("Sélectionnez les jours du week-end :", res2.content());
            assertTrue(res2.multipleChoice());
            assertEquals(7, res2.answers().size());
            assertFalse(res2.answers().get(0).correct()); // Lundi
            assertTrue(res2.answers().get(5).correct());  // Samedi (F)
            assertTrue(res2.answers().get(6).correct());  // Dimanche (G)
        }
    }

    @Test
    void testRejectsLessThanTwoAnswers(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("invalid_min.xlsx").toFile();

        // 1 seule réponse
        Question q = new Question(
                UUID.randomUUID().toString(),
                "Question invalide",
                null,
                false,
                1.0, 0.0, 0.0,
                List.of(new Answer(UUID.randomUUID().toString(), "Unique choix", true))
        );

        service.exportQuestionsToExcel(List.of(q), file);

        try (FileInputStream fis = new FileInputStream(file)) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.parseQuestionsFromExcel(fis, 1.0, 0.0, 0.0));
            assertTrue(ex.getMessage().contains("au moins 2 choix de réponse"));
        }
    }

    @Test
    void testRejectsNonExistentOptionLetter(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("invalid_letter.xlsx").toFile();

        // Créer un fichier où la bonne réponse marquée est 'D' mais il n'y a que A et B
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sh = wb.createSheet();
            org.apache.poi.ss.usermodel.Row header = sh.createRow(0);
            header.createCell(0).setCellValue("Question");
            header.createCell(1).setCellValue("Bonnes Réponses");
            header.createCell(2).setCellValue("Option A");
            header.createCell(3).setCellValue("Option B");

            org.apache.poi.ss.usermodel.Row r = sh.createRow(1);
            r.createCell(0).setCellValue("Question test");
            r.createCell(1).setCellValue("D"); // Inexistant
            r.createCell(2).setCellValue("Choix 1");
            r.createCell(3).setCellValue("Choix 2");

            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                wb.write(fos);
            }
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.parseQuestionsFromExcel(fis, 1.0, 0.0, 0.0));
            assertTrue(ex.getMessage().contains("ne correspond à aucune option renseignée"));
        }
    }

    @Test
    void testExportEmptyListGeneratesTemplate(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("template.xlsx").toFile();

        service.exportQuestionsToExcel(List.of(), file);
        assertTrue(file.exists());
        assertTrue(file.length() > 0);

        try (org.apache.poi.ss.usermodel.Workbook wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(file)) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.getSheetAt(0);
            assertNotNull(sheet);
            org.apache.poi.ss.usermodel.Row header = sheet.getRow(0);
            assertNotNull(header);
            assertEquals("Question", header.getCell(0).getStringCellValue());
            assertEquals("Bonnes Réponses", header.getCell(1).getStringCellValue());
            assertEquals("Option A", header.getCell(2).getStringCellValue());
            assertEquals("Option G", header.getCell(8).getStringCellValue());
        }
    }
}
