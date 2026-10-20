package tk.tahri.eval.desktop.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tk.tahri.eval.desktop.document.dto.QuestionStatistics;
import tk.tahri.eval.desktop.document.entities.blueprint.BlueprintPage;
import tk.tahri.eval.desktop.document.entities.blueprint.BlueprintPaperAssessment;
import tk.tahri.eval.desktop.document.entities.blueprint.BlueprintQuestion;
import tk.tahri.eval.desktop.document.entities.blueprint.QuestionState;
import tk.tahri.eval.desktop.document.repositories.BlueprintPaperAssessmentRepository;
import tk.tahri.eval.desktop.document.services.GradeService;
import tk.tahri.eval.desktop.model.Answer;
import tk.tahri.eval.desktop.model.Evaluation;
import tk.tahri.eval.desktop.model.Question;
import tk.tahri.eval.desktop.repository.EvaluationRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class GradeServiceTest {

    @Mock
    private EvaluationRepository evaluationRepository;

    @Mock
    private BlueprintPaperAssessmentRepository blueprintPaperAssessmentRepository;

    private GradeService gradeService;

    @BeforeEach
    void setUp() {
        gradeService = new GradeService(evaluationRepository, blueprintPaperAssessmentRepository);
    }

    @Test
    void testComputeQuestionStatistics() {
        String q1Id = UUID.randomUUID().toString();
        String q2Id = UUID.randomUUID().toString();

        Question q1 = new Question(q1Id, "Question 1 : 2+2?", null, false, 1.0, 0.0, 0.0, List.of(new Answer("a1", "4", true)));
        Question q2 = new Question(q2Id, "Question 2 : Capitale de France?", null, false, 1.0, 0.0, 0.0, List.of(new Answer("a2", "Paris", true)));

        Evaluation evaluation = new Evaluation();
        evaluation.setQuestions(List.of(q1, q2));

        // Étudiant 1 : Q1 CORRECT, Q2 INCORRECT
        BlueprintPaperAssessment assessment1 = new BlueprintPaperAssessment();
        BlueprintPage page1 = new BlueprintPage(1);
        BlueprintQuestion bq1_1 = new BlueprintQuestion();
        bq1_1.id = q1Id;
        bq1_1.corrected = true;
        bq1_1.state = QuestionState.CORRECT;

        BlueprintQuestion bq1_2 = new BlueprintQuestion();
        bq1_2.id = q2Id;
        bq1_2.corrected = true;
        bq1_2.state = QuestionState.INCORRECT;
        page1.blueprintQuestions = List.of(bq1_1, bq1_2);
        assessment1.blueprintPages = List.of(page1);

        // Étudiant 2 : Q1 CORRECT, Q2 EMPTY
        BlueprintPaperAssessment assessment2 = new BlueprintPaperAssessment();
        BlueprintPage page2 = new BlueprintPage(1);
        BlueprintQuestion bq2_1 = new BlueprintQuestion();
        bq2_1.id = q1Id;
        bq2_1.corrected = true;
        bq2_1.state = QuestionState.CORRECT;

        BlueprintQuestion bq2_2 = new BlueprintQuestion();
        bq2_2.id = q2Id;
        bq2_2.corrected = true;
        bq2_2.state = QuestionState.EMPTY;
        page2.blueprintQuestions = List.of(bq2_1, bq2_2);
        assessment2.blueprintPages = List.of(page2);

        // Étudiant 3 : Q1 INCORRECT, Q2 CORRECT
        BlueprintPaperAssessment assessment3 = new BlueprintPaperAssessment();
        BlueprintPage page3 = new BlueprintPage(1);
        BlueprintQuestion bq3_1 = new BlueprintQuestion();
        bq3_1.id = q1Id;
        bq3_1.corrected = true;
        bq3_1.state = QuestionState.INCORRECT;

        BlueprintQuestion bq3_2 = new BlueprintQuestion();
        bq3_2.id = q2Id;
        bq3_2.corrected = true;
        bq3_2.state = QuestionState.CORRECT;
        page3.blueprintQuestions = List.of(bq3_1, bq3_2);
        assessment3.blueprintPages = List.of(page3);

        List<QuestionStatistics> stats = gradeService.computeQuestionStatistics(
                evaluation,
                List.of(assessment1, assessment2, assessment3)
        );

        assertEquals(2, stats.size());

        // Vérification Question 1
        QuestionStatistics s1 = stats.get(0);
        assertEquals(1, s1.questionNumber());
        assertEquals(q1Id, s1.questionId());
        assertEquals("Question 1 : 2+2?", s1.content());
        assertEquals(2, s1.correctCount());
        assertEquals(0, s1.emptyCount());
        assertEquals(1, s1.incorrectCount());
        assertEquals(3, s1.totalEvaluated());
        assertEquals(66.67, s1.successRate(), 0.01);

        // Vérification Question 2
        QuestionStatistics s2 = stats.get(1);
        assertEquals(2, s2.questionNumber());
        assertEquals(q2Id, s2.questionId());
        assertEquals("Question 2 : Capitale de France?", s2.content());
        assertEquals(1, s2.correctCount());
        assertEquals(1, s2.emptyCount());
        assertEquals(1, s2.incorrectCount());
        assertEquals(3, s2.totalEvaluated());
        assertEquals(33.33, s2.successRate(), 0.01);
    }

    @Test
    void testCalculateGradesWithNegativeScoresForIncorrectAnswers() {
        String evalId = "eval-1";
        String groupId = "group-1";

        Evaluation evaluation = new Evaluation();
        evaluation.setId(evalId);
        evaluation.setGroupId(groupId);
        evaluation.setSameScore(true);
        evaluation.setDefaultScoreIfCorrect(1.0);
        evaluation.setDefaultScoreIfEmpty(0.0);
        evaluation.setDefaultScoreIfIncorrect(-0.5);

        // Étudiant avec 1 correct (+1.0) et 1 incorrect (-0.5) => Total 0.5
        BlueprintPaperAssessment assessment = new BlueprintPaperAssessment();
        assessment.id = "assess-1";
        assessment.groupId = groupId;
        assessment.evaluationId = evalId;

        BlueprintPage page = new BlueprintPage(1);
        BlueprintQuestion bq1 = new BlueprintQuestion();
        bq1.corrected = true;
        bq1.state = QuestionState.CORRECT;

        BlueprintQuestion bq2 = new BlueprintQuestion();
        bq2.corrected = true;
        bq2.state = QuestionState.INCORRECT;

        page.blueprintQuestions = List.of(bq1, bq2);
        page.markAsCorrected();
        assessment.blueprintPages = List.of(page);

        org.mockito.Mockito.when(evaluationRepository.findById(evalId)).thenReturn(java.util.Optional.of(evaluation));
        org.mockito.Mockito.when(blueprintPaperAssessmentRepository.findByGroupIdAndEvaluationId(groupId, evalId))
                .thenReturn(List.of(assessment));

        var response = gradeService.getGrades(groupId, evalId);
        assertNotNull(response);
        assertEquals(1, response.students().size());

        var studentGrade = response.students().get(0);
        assertEquals(0.5, studentGrade.score(), 0.001, "La note doit déduire 0.5 point pour la mauvaise réponse (1.0 - 0.5 = 0.5)");
    }

    @Test
    void testCalculateGradesCanBeNegativeWhenIncorrectAnswersExceedCorrect() {
        String evalId = "eval-2";
        String groupId = "group-1";

        Evaluation evaluation = new Evaluation();
        evaluation.setId(evalId);
        evaluation.setGroupId(groupId);
        evaluation.setSameScore(true);
        evaluation.setDefaultScoreIfCorrect(1.0);
        evaluation.setDefaultScoreIfEmpty(0.0);
        evaluation.setDefaultScoreIfIncorrect(-0.75);

        // Étudiant avec 1 correct (+1.0) et 2 incorrects (-1.5) => Total -0.5
        BlueprintPaperAssessment assessment = new BlueprintPaperAssessment();
        assessment.id = "assess-2";
        assessment.groupId = groupId;
        assessment.evaluationId = evalId;

        BlueprintPage page = new BlueprintPage(1);
        BlueprintQuestion bq1 = new BlueprintQuestion();
        bq1.corrected = true;
        bq1.state = QuestionState.CORRECT;

        BlueprintQuestion bq2 = new BlueprintQuestion();
        bq2.corrected = true;
        bq2.state = QuestionState.INCORRECT;

        BlueprintQuestion bq3 = new BlueprintQuestion();
        bq3.corrected = true;
        bq3.state = QuestionState.INCORRECT;

        page.blueprintQuestions = List.of(bq1, bq2, bq3);
        page.markAsCorrected();
        assessment.blueprintPages = List.of(page);

        org.mockito.Mockito.when(evaluationRepository.findById(evalId)).thenReturn(java.util.Optional.of(evaluation));
        org.mockito.Mockito.when(blueprintPaperAssessmentRepository.findByGroupIdAndEvaluationId(groupId, evalId))
                .thenReturn(List.of(assessment));

        var response = gradeService.getGrades(groupId, evalId);
        assertNotNull(response);
        var studentGrade = response.students().get(0);
        assertEquals(-0.5, studentGrade.score(), 0.001, "La note doit pouvoir être négative (1.0 - 0.75 - 0.75 = -0.5)");
        assertEquals(-0.5, response.statistics().min());
        assertEquals(-0.5, response.statistics().max());
        assertEquals(-0.5, response.statistics().average());
    }
}
