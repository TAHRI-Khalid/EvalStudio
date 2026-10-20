package tk.tahri.eval.desktop.document.services;

import org.springframework.stereotype.Service;
import tk.tahri.eval.desktop.document.dto.GradeStatistics;
import tk.tahri.eval.desktop.document.dto.GradesResponse;
import tk.tahri.eval.desktop.document.dto.QuestionStatistics;
import tk.tahri.eval.desktop.document.dto.StudentDetails;
import tk.tahri.eval.desktop.document.dto.StudentGrade;
import tk.tahri.eval.desktop.document.entities.blueprint.AssessmentStudent;
import tk.tahri.eval.desktop.document.entities.blueprint.BlueprintPaperAssessment;
import tk.tahri.eval.desktop.document.entities.blueprint.BlueprintQuestion;
import tk.tahri.eval.desktop.document.entities.blueprint.QuestionState;
import tk.tahri.eval.desktop.document.repositories.BlueprintPaperAssessmentRepository;
import tk.tahri.eval.desktop.exception.NotFoundException;
import tk.tahri.eval.desktop.model.Evaluation;
import tk.tahri.eval.desktop.model.Question;
import tk.tahri.eval.desktop.repository.EvaluationRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GradeService {

    private final EvaluationRepository evaluationRepository;
    private final BlueprintPaperAssessmentRepository blueprintPaperAssessmentRepository;

    public GradeService(EvaluationRepository evaluationRepository,
                        BlueprintPaperAssessmentRepository blueprintPaperAssessmentRepository) {
        this.evaluationRepository = evaluationRepository;
        this.blueprintPaperAssessmentRepository = blueprintPaperAssessmentRepository;
    }

    public List<StudentDetails> findStudentDetails(String groupId, String evaluationId) {
        Evaluation evaluation = evaluationRepository.findById(evaluationId)
                .filter(e -> groupId.equals(e.groupId()))
                .orElseThrow(() -> new NotFoundException("Évaluation introuvable : " + evaluationId));

        return blueprintPaperAssessmentRepository.findByGroupIdAndEvaluationId(groupId, evaluationId)
                .stream().map(a -> this.toDetails(a, evaluation))
                .toList();
    }

    private StudentDetails toDetails(BlueprintPaperAssessment assessment, Evaluation evaluation) {
        return buildDetails(assessment.student, assessment.blueprintPages.stream()
                .flatMap(p -> p.blueprintQuestions.stream())
                .toList(), evaluation);
    }

    private StudentDetails buildDetails(AssessmentStudent student, List<BlueprintQuestion> questions, Evaluation evaluation) {
        double score = questions.stream()
                .filter(q -> q.corrected)
                .mapToDouble(q -> getQuestionScore(q, evaluation))
                .sum();
        long corrected = questions.stream().filter(q -> q.corrected).count();
        return new StudentDetails(
            student != null ? student.enrollmentId() : null,
            student != null ? student.matriculation() : null,
            student != null ? student.firstName() : null,
            student != null ? student.lastName() : null,
            score,
            corrected,
            questions.size());
    }

    public double getQuestionScore(BlueprintQuestion q, Evaluation evaluation) {
        if (evaluation != null && evaluation.sameScore()) {
            QuestionState state = q.state != null ? q.state : QuestionState.EMPTY;
            return switch (state) {
                case CORRECT -> evaluation.defaultScoreIfCorrect();
                case EMPTY -> evaluation.defaultScoreIfEmpty();
                case INCORRECT -> evaluation.defaultScoreIfIncorrect();
            };
        }
        return q.getScore();
    }

    public GradesResponse getGrades(String groupId, String evaluationId) {
        Evaluation evaluation = evaluationRepository.findById(evaluationId)
                .filter(e -> groupId.equals(e.groupId()))
                .orElseThrow(() -> new NotFoundException("Évaluation introuvable : " + evaluationId));

        List<BlueprintPaperAssessment> assessments =
                blueprintPaperAssessmentRepository.findByGroupIdAndEvaluationId(groupId, evaluationId);
        List<StudentGrade> students = assessments.stream()
                .map(a -> this.toGrade(a, evaluation))
                .toList();
        List<QuestionStatistics> questionStats = computeQuestionStatistics(evaluation, assessments);
        return new GradesResponse(computeStatistics(students), students, questionStats);
    }

    public List<QuestionStatistics> computeQuestionStatistics(Evaluation evaluation, List<BlueprintPaperAssessment> assessments) {
        if (evaluation.getQuestions() == null || evaluation.getQuestions().isEmpty()) {
            return Collections.emptyList();
        }

        List<BlueprintQuestion> allCorrectedBlueprintQuestions = assessments.stream()
                .flatMap(a -> a.blueprintPages.stream())
                .flatMap(p -> p.blueprintQuestions.stream())
                .filter(bq -> bq.corrected)
                .toList();

        Map<String, List<BlueprintQuestion>> bqByQuestionId = allCorrectedBlueprintQuestions.stream()
                .filter(bq -> bq.id != null)
                .collect(Collectors.groupingBy(bq -> bq.id));

        List<QuestionStatistics> result = new ArrayList<>();
        List<Question> modelQuestions = evaluation.getQuestions();

        for (int i = 0; i < modelQuestions.size(); i++) {
            Question q = modelQuestions.get(i);
            int questionNumber = i + 1;
            List<BlueprintQuestion> answersForQ = bqByQuestionId.getOrDefault(q.id(), Collections.emptyList());

            long correctCount = answersForQ.stream()
                    .filter(bq -> bq.state == QuestionState.CORRECT)
                    .count();
            long emptyCount = answersForQ.stream()
                    .filter(bq -> bq.state == QuestionState.EMPTY)
                    .count();
            long incorrectCount = answersForQ.stream()
                    .filter(bq -> bq.state == QuestionState.INCORRECT)
                    .count();

            long totalEvaluated = correctCount + emptyCount + incorrectCount;
            double successRate = totalEvaluated > 0
                    ? round((correctCount * 100.0) / totalEvaluated)
                    : 0.0;

            result.add(new QuestionStatistics(
                    q.id(),
                    questionNumber,
                    q.content() != null ? q.content() : "",
                    correctCount,
                    emptyCount,
                    incorrectCount,
                    totalEvaluated,
                    successRate
            ));
        }

        return result;
    }

    private StudentGrade toGrade(BlueprintPaperAssessment assessment, Evaluation evaluation) {
        List<BlueprintQuestion> questions = assessment.blueprintPages.stream()
                .flatMap(p -> p.blueprintQuestions.stream())
                .toList();
        long correctedQuestions = questions.stream().filter(q -> q.corrected).count();
        int correctedPages = (int) assessment.blueprintPages.stream().filter(p -> p.corrected).count();
        int totalPages = assessment.blueprintPages.size();
        double score = questions.stream()
                .filter(q -> q.corrected)
                .mapToDouble(q -> getQuestionScore(q, evaluation))
                .sum();

        return new StudentGrade(
                assessment.getId(),
                assessment.student != null ? assessment.student.enrollmentId() : null,
                assessment.student != null ? assessment.student.matriculation() : null,
                assessment.student != null ? assessment.student.firstName() : null,
                assessment.student != null ? assessment.student.lastName() : null,
                correctedQuestions == 0 ? null : score,
                correctedQuestions,
                questions.size(),
                correctedPages,
                totalPages,
                totalPages > 0 && correctedPages == totalPages);
    }

    public StudentGrade toGrade(BlueprintPaperAssessment assessment) {
        return toGrade(assessment, null);
    }

    private GradeStatistics computeStatistics(List<StudentGrade> students) {
        List<Double> scores = students.stream()
                .map(StudentGrade::score)
                .filter(s -> s != null)
                .sorted()
                .toList();
        if (scores.isEmpty()) {
            return new GradeStatistics(null, null, null, null, null, null);
        }

        double max = scores.getLast();
        double min = scores.getFirst();
        double average = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double median = median(scores);
        double variance = scores.stream()
                .mapToDouble(s -> Math.pow(s - average, 2))
                .average()
                .orElse(0.0);
        double stdDeviation = Math.sqrt(variance);

        return new GradeStatistics(
                round(max),
                round(min),
                round(average),
                round(median),
                round(variance),
                round(stdDeviation));
    }

    private double median(List<Double> sortedScores) {
        int size = sortedScores.size();
        int middle = size / 2;
        if (size % 2 == 1) {
            return sortedScores.get(middle);
        }
        return (sortedScores.get(middle - 1) + sortedScores.get(middle)) / 2.0;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
