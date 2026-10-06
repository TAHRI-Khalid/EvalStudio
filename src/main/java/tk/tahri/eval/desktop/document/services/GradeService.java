package tk.tahri.eval.desktop.document.services;

import org.springframework.stereotype.Service;
import tk.tahri.eval.desktop.document.dto.GradeStatistics;
import tk.tahri.eval.desktop.document.dto.GradesResponse;
import tk.tahri.eval.desktop.document.dto.StudentDetails;
import tk.tahri.eval.desktop.document.dto.StudentGrade;
import tk.tahri.eval.desktop.document.entities.blueprint.AssessmentStudent;
import tk.tahri.eval.desktop.document.entities.blueprint.BlueprintPaperAssessment;
import tk.tahri.eval.desktop.document.entities.blueprint.BlueprintQuestion;
import tk.tahri.eval.desktop.document.repositories.BlueprintPaperAssessmentRepository;
import tk.tahri.eval.desktop.exception.NotFoundException;
import tk.tahri.eval.desktop.repository.EvaluationRepository;

import java.util.List;

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
        evaluationRepository.findById(evaluationId)
                .filter(e -> groupId.equals(e.groupId()))
                .orElseThrow(() -> new NotFoundException("Évaluation introuvable : " + evaluationId));

        return blueprintPaperAssessmentRepository.findByGroupIdAndEvaluationId(groupId, evaluationId)
                .stream().map(this::toDetails)
                .toList();
    }

    private StudentDetails toDetails(BlueprintPaperAssessment assessment) {
        return buildDetails(assessment.student, assessment.blueprintPages.stream()
                .flatMap(p -> p.blueprintQuestions.stream())
                .toList());
    }

    private StudentDetails buildDetails(AssessmentStudent student, List<BlueprintQuestion> questions) {
        double score = questions.stream().filter(q -> q.corrected).mapToDouble(BlueprintQuestion::getScore).sum();
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

    public GradesResponse getGrades(String groupId, String evaluationId) {
        evaluationRepository.findById(evaluationId)
                .filter(e -> groupId.equals(e.groupId()))
                .orElseThrow(() -> new NotFoundException("Évaluation introuvable : " + evaluationId));

        List<BlueprintPaperAssessment> assessments =
                blueprintPaperAssessmentRepository.findByGroupIdAndEvaluationId(groupId, evaluationId);
        List<StudentGrade> students = assessments.stream()
                .map(this::toGrade)
                .toList();
        return new GradesResponse(computeStatistics(students), students);
    }

    private StudentGrade toGrade(BlueprintPaperAssessment assessment) {
        List<BlueprintQuestion> questions = assessment.blueprintPages.stream()
                .flatMap(p -> p.blueprintQuestions.stream())
                .toList();
        long correctedQuestions = questions.stream().filter(q -> q.corrected).count();
        int correctedPages = (int) assessment.blueprintPages.stream().filter(p -> p.corrected).count();
        int totalPages = assessment.blueprintPages.size();
        double score = questions.stream().filter(q -> q.corrected).mapToDouble(BlueprintQuestion::getScore).sum();

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
