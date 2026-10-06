package tk.tahri.eval.desktop.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tk.tahri.eval.desktop.model.Evaluation;
import tk.tahri.eval.desktop.model.EvaluationState;

import java.util.List;

@Repository
public interface EvaluationRepository extends JpaRepository<Evaluation, String> {

    List<Evaluation> findByGroupId(String groupId);

    List<Evaluation> findByGroupIdOrderByCreatedAtDesc(String groupId);

    Page<Evaluation> findByGroupId(String groupId, Pageable pageable);

    Page<Evaluation> findByGroupIdAndStateIn(String groupId, List<EvaluationState> states, Pageable pageable);

    @Modifying
    @Transactional
    void deleteByGroupId(String groupId);

    // Backward compatibility aliases delegating to groupId
    default List<Evaluation> findByCourseId(String courseId) { return findByGroupId(courseId); }
    default List<Evaluation> findByCourseIdOrderByCreatedAtDesc(String courseId) { return findByGroupIdOrderByCreatedAtDesc(courseId); }
    default Page<Evaluation> findByCourseId(String courseId, Pageable pageable) { return findByGroupId(courseId, pageable); }
    default Page<Evaluation> findByCourseIdAndStateIn(String courseId, List<EvaluationState> states, Pageable pageable) { return findByGroupIdAndStateIn(courseId, states, pageable); }
    default void deleteByCourseId(String courseId) { deleteByGroupId(courseId); }
}
