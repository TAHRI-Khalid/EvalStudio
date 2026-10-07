package tk.tahri.eval.desktop.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tk.tahri.eval.desktop.model.Answer;
import tk.tahri.eval.desktop.model.Question;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class QuestionExcelService {

    private static final Logger log = LoggerFactory.getLogger(QuestionExcelService.class);

    public static final int MIN_ANSWERS = 2;
    public static final int MAX_ANSWERS = 7;

    private static final String[] HEADERS = {
            "Question",
            "Bonnes Réponses",
            "Option A",
            "Option B",
            "Option C",
            "Option D",
            "Option E",
            "Option F",
            "Option G"
    };

    /**
     * Lit un fichier Excel et extrait la liste des questions avec leurs réponses.
     * Applique les barèmes par défaut passés en argument.
     */
    public List<Question> parseQuestionsFromExcel(InputStream inputStream,
                                                 double defaultScoreCorrect,
                                                 double defaultScoreEmpty,
                                                 double defaultScoreIncorrect) throws IOException {
        List<Question> questions = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("Le classeur Excel ne contient aucune feuille.");
            }

            DataFormatter formatter = new DataFormatter();
            int lastRowNum = sheet.getLastRowNum();

            // Parcourt à partir de la ligne 1 (la ligne 0 correspond aux en-têtes)
            for (int r = 1; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String questionContent = formatter.formatCellValue(row.getCell(0)).trim();
                // Ignorer les lignes entièrement vides
                if (questionContent.isEmpty()) {
                    boolean hasAnyData = false;
                    for (int c = 1; c <= 8; c++) {
                        if (!formatter.formatCellValue(row.getCell(c)).trim().isEmpty()) {
                            hasAnyData = true;
                            break;
                        }
                    }
                    if (!hasAnyData) continue;
                    throw new IllegalArgumentException("Ligne " + (r + 1) + " : L'énoncé de la question est manquant.");
                }

                String correctColVal = formatter.formatCellValue(row.getCell(1)).trim();
                if (correctColVal.isEmpty()) {
                    throw new IllegalArgumentException("Ligne " + (r + 1) + " : Aucune bonne réponse indiquée pour la question « "
                            + truncate(questionContent, 40) + " ».");
                }

                // Récupération des options A à G (colonnes 2 à 8)
                Map<Character, String> optionsMap = new LinkedHashMap<>();
                for (int i = 0; i < MAX_ANSWERS; i++) {
                    char letter = (char) ('A' + i);
                    Cell cell = row.getCell(2 + i);
                    String val = cell != null ? formatter.formatCellValue(cell).trim() : "";
                    if (!val.isEmpty()) {
                        optionsMap.put(letter, val);
                    }
                }

                // Vérification contrainte min 2 réponses
                if (optionsMap.size() < MIN_ANSWERS) {
                    throw new IllegalArgumentException("Ligne " + (r + 1) + " : La question « "
                            + truncate(questionContent, 40) + " » doit comporter au moins " + MIN_ANSWERS
                            + " choix de réponse (Option A et Option B). Trouvé : " + optionsMap.size() + " choix.");
                }

                // Extraction des lettres correctes mentionnées dans la colonne "Bonnes Réponses"
                Set<Character> correctLetters = extractCorrectLetters(correctColVal);
                if (correctLetters.isEmpty()) {
                    throw new IllegalArgumentException("Ligne " + (r + 1) + " : Impossible d'extraire une lettre de bonne réponse valide (ex: A, B, C...) depuis « "
                            + correctColVal + " » pour la question « " + truncate(questionContent, 40) + " ».");
                }

                // Vérifier que chaque lettre de bonne réponse correspond à une option réellement renseignée
                for (Character correctLetter : correctLetters) {
                    if (!optionsMap.containsKey(correctLetter)) {
                        throw new IllegalArgumentException("Ligne " + (r + 1) + " : La réponse correcte indiquée '"
                                + correctLetter + "' ne correspond à aucune option renseignée pour la question « "
                                + truncate(questionContent, 40) + " » (Options disponibles : " + optionsMap.keySet() + ").");
                    }
                }

                // Construction des entités Answer
                List<Answer> answers = new ArrayList<>();
                for (Map.Entry<Character, String> entry : optionsMap.entrySet()) {
                    char letter = entry.getKey();
                    String answerText = entry.getValue();
                    boolean isCorrect = correctLetters.contains(letter);
                    answers.add(new Answer(UUID.randomUUID().toString(), answerText, isCorrect));
                }

                boolean isMultipleChoice = correctLetters.size() > 1;

                Question question = new Question(
                        UUID.randomUUID().toString(),
                        questionContent,
                        null,
                        isMultipleChoice,
                        defaultScoreCorrect,
                        defaultScoreEmpty,
                        defaultScoreIncorrect,
                        answers
                );

                questions.add(question);
            }
        }

        if (questions.isEmpty()) {
            throw new IllegalArgumentException("Aucune question valide n'a été trouvée dans le fichier Excel.");
        }

        return questions;
    }

    /**
     * Exporte la liste des questions vers un fichier Excel .xlsx.
     */
    public void exportQuestionsToExcel(List<Question> questions, File targetFile) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Questions");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle centerStyle = workbook.createCellStyle();
            centerStyle.setAlignment(HorizontalAlignment.CENTER);

            // Ligne d'en-tête
            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(24);

            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Question q : questions) {
                Row row = sheet.createRow(rowIdx++);

                // Colonne 0: Question
                row.createCell(0).setCellValue(q.content() != null ? q.content() : "");

                List<Answer> answers = q.answers() != null ? q.answers() : Collections.emptyList();
                List<String> correctLetterList = new ArrayList<>();

                // Colonnes 2 à 8 : Options A à G
                int maxToExport = Math.min(answers.size(), MAX_ANSWERS);
                for (int i = 0; i < maxToExport; i++) {
                    Answer a = answers.get(i);
                    char letter = (char) ('A' + i);
                    if (a.correct()) {
                        correctLetterList.add(String.valueOf(letter));
                    }
                    row.createCell(2 + i).setCellValue(a.content() != null ? a.content() : "");
                }

                // Colonne 1: Bonnes Réponses (ex: "A" ou "A, C")
                Cell correctCell = row.createCell(1);
                correctCell.setCellValue(String.join(", ", correctLetterList));
                correctCell.setCellStyle(centerStyle);
            }

            // Ajustement automatique des largeurs de colonnes
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
                // Prévoir une largeur minimale confortable pour la lisibilité
                if (sheet.getColumnWidth(i) < 4000) {
                    sheet.setColumnWidth(i, 4000);
                }
            }

            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                workbook.write(fos);
            }
        }
    }

    /**
     * Génère un modèle Excel vierge pré-rempli avec des lignes d'exemple.
     */
    public void generateTemplateExcel(File targetFile) throws IOException {
        List<Question> sampleQuestions = List.of(
                new Question(
                        UUID.randomUUID().toString(),
                        "Quelle est la capitale du Maroc ?",
                        null,
                        false,
                        1.0, 0.0, 0.0,
                        List.of(
                                new Answer(UUID.randomUUID().toString(), "Casablanca", false),
                                new Answer(UUID.randomUUID().toString(), "Rabat", true),
                                new Answer(UUID.randomUUID().toString(), "Fès", false),
                                new Answer(UUID.randomUUID().toString(), "Tanger", false)
                        )
                ),
                new Question(
                        UUID.randomUUID().toString(),
                        "Quels langages sont orientés objet ?",
                        null,
                        true,
                        1.0, 0.0, 0.0,
                        List.of(
                                new Answer(UUID.randomUUID().toString(), "Java", true),
                                new Answer(UUID.randomUUID().toString(), "C", false),
                                new Answer(UUID.randomUUID().toString(), "C++", true),
                                new Answer(UUID.randomUUID().toString(), "Python", true)
                        )
                ),
                new Question(
                        UUID.randomUUID().toString(),
                        "La Terre tourne autour du Soleil.",
                        null,
                        false,
                        1.0, 0.0, 0.0,
                        List.of(
                                new Answer(UUID.randomUUID().toString(), "Vrai", true),
                                new Answer(UUID.randomUUID().toString(), "Faux", false)
                        )
                )
        );
        exportQuestionsToExcel(sampleQuestions, targetFile);
    }

    /**
     * Extrait les lettres de 'A' à 'G' d'une chaîne (ex: "A", "A, C", "a;c", "A C").
     */
    private Set<Character> extractCorrectLetters(String input) {
        Set<Character> letters = new LinkedHashSet<>();
        if (input == null) return letters;

        Pattern pattern = Pattern.compile("[A-Ga-g]");
        Matcher matcher = pattern.matcher(input);
        while (matcher.find()) {
            letters.add(Character.toUpperCase(matcher.group().charAt(0)));
        }
        return letters;
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
