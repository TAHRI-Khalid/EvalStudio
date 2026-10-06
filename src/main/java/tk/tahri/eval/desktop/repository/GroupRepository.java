package tk.tahri.eval.desktop.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tk.tahri.eval.desktop.model.Group;

import java.util.List;

@Repository
public interface GroupRepository extends JpaRepository<Group, String> {
    List<Group> findAllByOrderByCreatedAtDesc();
}
