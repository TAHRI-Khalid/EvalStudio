package tk.tahri.eval.desktop.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tk.tahri.eval.desktop.model.Answer;
import tk.tahri.eval.desktop.model.Evaluation;
import tk.tahri.eval.desktop.model.Question;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BaremeValidationTest {

    private final List<Answer> sampleAnswers = List.of(
            new Answer(UUID.randomUUID().toString(), "Choix 1", true),
            new Answer(UUID.randomUUID().toString(), "Choix 2", false)
    );

    @Test
    @DisplayName("Question : la note pour une réponse correcte doit toujours être strictement positive (> 0)")
    void testQuestionScoreIfCorrectMustBeStrictlyPositive() {
        // Valide (> 0)
        assertDoesNotThrow(() -> new Question(UUID.randomUUID().toString(), "Q?", null, false, 1.0, 0.0, 0.0, sampleAnswers));
        assertDoesNotThrow(() -> new Question(UUID.randomUUID().toString(), "Q?", null, false, 0.25, 0.0, 0.0, sampleAnswers));

        // Invalide (<= 0)
        IllegalArgumentException exZero = assertThrows(IllegalArgumentException.class, () ->
                new Question(UUID.randomUUID().toString(), "Q?", null, false, 0.0, 0.0, 0.0, sampleAnswers));
        assertTrue(exZero.getMessage().contains("strictement positive"));

        IllegalArgumentException exNeg = assertThrows(IllegalArgumentException.class, () ->
                new Question(UUID.randomUUID().toString(), "Q?", null, false, -1.0, 0.0, 0.0, sampleAnswers));
        assertTrue(exNeg.getMessage().contains("strictement positive"));
    }

    @Test
    @DisplayName("Question : la note pour une réponse fausse doit toujours être 0 ou négative (<= 0)")
    void testQuestionScoreIfIncorrectMustBeZeroOrNegative() {
        // Valide (0 ou négatif)
        assertDoesNotThrow(() -> new Question(UUID.randomUUID().toString(), "Q?", null, false, 1.0, 0.0, 0.0, sampleAnswers));
        assertDoesNotThrow(() -> new Question(UUID.randomUUID().toString(), "Q?", null, false, 1.0, 0.0, -0.5, sampleAnswers));
        assertDoesNotThrow(() -> new Question(UUID.randomUUID().toString(), "Q?", null, false, 1.0, 0.0, -2.0, sampleAnswers));

        // Invalide (> 0)
        IllegalArgumentException exPos = assertThrows(IllegalArgumentException.class, () ->
                new Question(UUID.randomUUID().toString(), "Q?", null, false, 1.0, 0.0, 0.5, sampleAnswers));
        assertTrue(exPos.getMessage().contains("0 ou négative") || exPos.getMessage().contains("0 ou négatif"));
    }

    @Test
    @DisplayName("Question : la note sans réponse peut être positive, zéro ou négative")
    void testQuestionScoreIfEmptyCanBePositiveZeroOrNegative() {
        // Positif
        assertDoesNotThrow(() -> new Question(UUID.randomUUID().toString(), "Q?", null, false, 1.0, 0.5, -0.5, sampleAnswers));
        // Zéro
        assertDoesNotThrow(() -> new Question(UUID.randomUUID().toString(), "Q?", null, false, 1.0, 0.0, -0.5, sampleAnswers));
        // Négatif
        assertDoesNotThrow(() -> new Question(UUID.randomUUID().toString(), "Q?", null, false, 1.0, -0.25, -0.5, sampleAnswers));
    }

    @Test
    @DisplayName("Evaluation : validation des setters et du constructeur pour le barème")
    void testEvaluationBaremeSetters() {
        Evaluation eval = new Evaluation();

        // Réponse correcte
        assertDoesNotThrow(() -> eval.setDefaultScoreIfCorrect(2.5));
        assertEquals(2.5, eval.getDefaultScoreIfCorrect());
        assertThrows(IllegalArgumentException.class, () -> eval.setDefaultScoreIfCorrect(0.0));
        assertThrows(IllegalArgumentException.class, () -> eval.setDefaultScoreIfCorrect(-1.0));

        // Réponse fausse
        assertDoesNotThrow(() -> eval.setDefaultScoreIfIncorrect(0.0));
        assertEquals(0.0, eval.getDefaultScoreIfIncorrect());
        assertDoesNotThrow(() -> eval.setDefaultScoreIfIncorrect(-0.75));
        assertEquals(-0.75, eval.getDefaultScoreIfIncorrect());
        assertThrows(IllegalArgumentException.class, () -> eval.setDefaultScoreIfIncorrect(0.5));

        // Sans réponse (positif, zéro ou négatif)
        assertDoesNotThrow(() -> eval.setDefaultScoreIfEmpty(0.5));
        assertEquals(0.5, eval.getDefaultScoreIfEmpty());
        assertDoesNotThrow(() -> eval.setDefaultScoreIfEmpty(0.0));
        assertEquals(0.0, eval.getDefaultScoreIfEmpty());
        assertDoesNotThrow(() -> eval.setDefaultScoreIfEmpty(-0.25));
        assertEquals(-0.25, eval.getDefaultScoreIfEmpty());
    }

    @Test
    @DisplayName("EvaluationService : validation statique du barème")
    void testEvaluationServiceValidateBareme() {
        // Valide
        assertDoesNotThrow(() -> EvaluationService.validateBareme(1.0, 0.0, 0.0));
        assertDoesNotThrow(() -> EvaluationService.validateBareme(2.0, 0.5, -1.0));
        assertDoesNotThrow(() -> EvaluationService.validateBareme(1.0, -0.5, -0.5));

        // Réponse correcte <= 0
        assertThrows(IllegalArgumentException.class, () -> EvaluationService.validateBareme(0.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> EvaluationService.validateBareme(-1.0, 0.0, -0.5));

        // Réponse fausse > 0
        assertThrows(IllegalArgumentException.class, () -> EvaluationService.validateBareme(1.0, 0.0, 0.5));
    }

    @Test
    @DisplayName("QuestionExcelService : vérification du barème lors du parsing Excel")
    void testQuestionExcelServiceBaremeValidation() {
        QuestionExcelService excelService = new QuestionExcelService();

        // Si correct <= 0, doit lever une IllegalArgumentException immédiatement
        assertThrows(IllegalArgumentException.class, () ->
                excelService.parseQuestionsFromExcel(new ByteArrayInputStream(new byte[0]), 0.0, 0.0, 0.0));

        // Si incorrect > 0, doit lever une IllegalArgumentException immédiatement
        assertThrows(IllegalArgumentException.class, () ->
                excelService.parseQuestionsFromExcel(new ByteArrayInputStream(new byte[0]), 1.0, 0.0, 0.5));
    }
}
