package tk.tahri.eval.desktop.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tk.tahri.eval.desktop.document.repositories.BlueprintPaperAssessmentRepository;
import tk.tahri.eval.desktop.document.repositories.UncorrectedPageRepository;
import tk.tahri.eval.desktop.model.Answer;
import tk.tahri.eval.desktop.model.Question;
import tk.tahri.eval.desktop.repository.EvaluationRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EvaluationCloneQuestionTest {

    private EvaluationService evaluationService;

    @BeforeEach
    void setUp() {
        EvaluationRepository evalRepo = Mockito.mock(EvaluationRepository.class);
        BlueprintPaperAssessmentRepository bpRepo = Mockito.mock(BlueprintPaperAssessmentRepository.class);
        UncorrectedPageRepository uncorrectedRepo = Mockito.mock(UncorrectedPageRepository.class);
        evaluationService = new EvaluationService(evalRepo, bpRepo, uncorrectedRepo);
    }

    @Test
    @DisplayName("Le clonage régénère un nouvel identifiant unique pour la question et les réponses")
    void testCloneQuestionRegeneratesUuids() {
        Answer a1 = new Answer("ans-id-1", "Choix 1", true);
        Answer a2 = new Answer("ans-id-2", "Choix 2", false);
        Question source = new Question("orig-q-id", "Énoncé question ?", "img.png", false, 2.0, 0.0, -0.5, List.of(a1, a2));

        Question cloned = evaluationService.cloneQuestion(source, null, null, null);

        assertNotNull(cloned);
        assertNotEquals("orig-q-id", cloned.id(), "L'id de la question doit être régénéré");
        assertEquals("Énoncé question ?", cloned.content());
        assertEquals("img.png", cloned.imagePath());
        assertFalse(cloned.multipleChoice());
        assertEquals(2.0, cloned.scoreIfCorrect());
        assertEquals(0.0, cloned.scoreIfEmpty());
        assertEquals(-0.5, cloned.scoreIfIncorrect());

        assertEquals(2, cloned.answers().size());
        assertNotEquals("ans-id-1", cloned.answers().get(0).id(), "L'id de la réponse 1 doit être régénéré");
        assertEquals("Choix 1", cloned.answers().get(0).content());
        assertTrue(cloned.answers().get(0).correct());

        assertNotEquals("ans-id-2", cloned.answers().get(1).id(), "L'id de la réponse 2 doit être régénéré");
        assertEquals("Choix 2", cloned.answers().get(1).content());
        assertFalse(cloned.answers().get(1).correct());
    }

    @Test
    @DisplayName("Le clonage permet de surcharger le barème si demandé")
    void testCloneQuestionWithBaremeOverride() {
        Answer a1 = new Answer("ans-id-1", "Réponse", true);
        Question source = new Question("orig-id", "Texte", null, false, 3.0, -1.0, -2.0, List.of(a1));

        Question cloned = evaluationService.cloneQuestion(source, 1.5, 0.0, 0.0);

        assertNotNull(cloned);
        assertEquals(1.5, cloned.scoreIfCorrect());
        assertEquals(0.0, cloned.scoreIfEmpty());
        assertEquals(0.0, cloned.scoreIfIncorrect());
    }

    @Test
    @DisplayName("Le clonage d'une liste de questions fonctionne sur l'ensemble")
    void testCloneQuestionsList() {
        Question q1 = new Question("q1", "Texte 1", null, false, 1.0, 0.0, 0.0, List.of(new Answer("a1", "R1", true)));
        Question q2 = new Question("q2", "Texte 2", null, true, 2.0, 0.0, 0.0, List.of(new Answer("a2", "R2", true)));

        List<Question> clonedList = evaluationService.cloneQuestions(List.of(q1, q2), null, null, null);

        assertEquals(2, clonedList.size());
        assertNotEquals(clonedList.get(0).id(), clonedList.get(1).id());
        assertNotEquals("q1", clonedList.get(0).id());
        assertNotEquals("q2", clonedList.get(1).id());
    }
}
