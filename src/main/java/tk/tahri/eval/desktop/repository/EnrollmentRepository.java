package tk.tahri.eval.desktop.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tk.tahri.eval.desktop.model.GroupRole;
import tk.tahri.eval.desktop.model.Enrollment;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, String> {

    List<Enrollment> findByGroupId(String groupId);

    List<Enrollment> findByGroupIdAndEnableTrue(String groupId);

    List<Enrollment> findByGroupIdAndRole(String groupId, GroupRole role);

    List<Enrollment> findByGroupIdAndRoleAndEnableTrue(String groupId, GroupRole role);

    Page<Enrollment> findByGroupIdAndRoleAndEnableTrue(String groupId, GroupRole role, Pageable pageable);

    long countByGroupIdAndRoleAndEnableTrue(String groupId, GroupRole role);

    List<Enrollment> findByGroupIdAndRoleAndEnableTrueOrderByLastNameAsc(String groupId, GroupRole role);

    @Modifying
    @Transactional
    void deleteByGroupId(String groupId);

    Optional<Enrollment> findByGroupIdAndMatriculation(String groupId, String matriculation);

    // Backward compatibility aliases delegating to groupId
    default List<Enrollment> findByCourseId(String courseId) { return findByGroupId(courseId); }
    default List<Enrollment> findByCourseIdAndRole(String courseId, GroupRole role) { return findByGroupIdAndRole(courseId, role); }
    default List<Enrollment> findByCourseIdAndRoleAndEnableTrueOrderByLastNameAsc(String courseId, GroupRole role) { return findByGroupIdAndRoleAndEnableTrueOrderByLastNameAsc(courseId, role); }
    default void deleteByCourseId(String courseId) { deleteByGroupId(courseId); }
    default Optional<Enrollment> findByCourseIdAndMatriculation(String courseId, String matriculation) { return findByGroupIdAndMatriculation(courseId, matriculation); }
}
