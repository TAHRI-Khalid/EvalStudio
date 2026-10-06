package tk.tahri.eval.desktop.document.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tk.tahri.eval.desktop.document.entities.UncorrectedPage;

import java.util.List;

@Repository
public interface UncorrectedPageRepository extends JpaRepository<UncorrectedPage, String> {

    List<UncorrectedPage> findByEvaluationId(String evaluationId);

    @Modifying
    @Transactional
    long deleteByEvaluationId(String evaluationId);

    @Modifying
    @Transactional
    long deleteByGroupId(String groupId);

    default long deleteByCourseId(String courseId) { return deleteByGroupId(courseId); }
}
