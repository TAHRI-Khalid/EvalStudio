package tk.tahri.eval.desktop.document.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tk.tahri.eval.desktop.document.entities.blueprint.BlueprintPaperAssessment;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlueprintPaperAssessmentRepository extends JpaRepository<BlueprintPaperAssessment, String> {

    Optional<BlueprintPaperAssessment> findByIdAndGroupId(String id, String groupId);

    List<BlueprintPaperAssessment> findByGroupIdAndEvaluationId(String groupId, String evaluationId);

    @Modifying
    @Transactional
    long deleteByGroupIdAndEvaluationId(String groupId, String evaluationId);

    @Modifying
    @Transactional
    long deleteByEvaluationId(String evaluationId);

    @Modifying
    @Transactional
    long deleteByGroupId(String groupId);

    // Compatibility methods delegating to groupId
    default Optional<BlueprintPaperAssessment> findByIdAndCourseId(String id, String courseId) { return findByIdAndGroupId(id, courseId); }
    default List<BlueprintPaperAssessment> findByCourseIdAndEvaluationId(String courseId, String evaluationId) { return findByGroupIdAndEvaluationId(courseId, evaluationId); }
    default long deleteByCourseIdAndEvaluationId(String courseId, String evaluationId) { return deleteByGroupIdAndEvaluationId(courseId, evaluationId); }
    default long deleteByCourseId(String courseId) { return deleteByGroupId(courseId); }
}
