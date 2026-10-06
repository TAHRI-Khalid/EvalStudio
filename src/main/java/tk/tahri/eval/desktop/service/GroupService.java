package tk.tahri.eval.desktop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tk.tahri.eval.desktop.exception.NotFoundException;
import tk.tahri.eval.desktop.model.Group;
import tk.tahri.eval.desktop.model.GroupRole;
import tk.tahri.eval.desktop.model.Enrollment;
import tk.tahri.eval.desktop.repository.GroupRepository;
import tk.tahri.eval.desktop.repository.EnrollmentRepository;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Service
public class GroupService {

    private static final Logger log = LoggerFactory.getLogger(GroupService.class);

    private final GroupRepository groupRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ImageStorageService imageStorageService;
    private final StudentExcelService studentExcelService;
    private final EvaluationService evaluationService;

    public GroupService(GroupRepository groupRepository,
                        EnrollmentRepository enrollmentRepository,
                        ImageStorageService imageStorageService,
                        StudentExcelService studentExcelService,
                        EvaluationService evaluationService) {
        this.groupRepository = groupRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.imageStorageService = imageStorageService;
        this.studentExcelService = studentExcelService;
        this.evaluationService = evaluationService;
    }

    public List<Group> getAllGroups() {
        return groupRepository.findAllByOrderByCreatedAtDesc();
    }

    public Group getGroup(String id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Groupe introuvable : " + id));
    }

    public Group saveGroup(Group group, File logoFile) {
        if (logoFile != null && logoFile.exists()) {
            String logoFilename = imageStorageService.storeImage(logoFile, ImageStorageService.ImageFolder.GROUP_LOGOS);
            if (logoFilename != null) {
                group.setLogoUrl(logoFilename);
            }
        }
        if (group.getCreatedAt() == null) {
            group.setCreatedAt(Instant.now());
        }
        return groupRepository.save(group);
    }

    @Transactional
    public void deleteGroup(String groupId) {
        for (var eval : evaluationService.getEvaluationsByGroup(groupId)) {
            evaluationService.deleteEvaluation(eval.getId());
        }
        enrollmentRepository.deleteByGroupId(groupId);
        groupRepository.deleteById(groupId);
    }

    public List<Enrollment> getStudents(String groupId) {
        return enrollmentRepository.findByGroupIdAndRoleAndEnableTrueOrderByLastNameAsc(groupId, GroupRole.STUDENT);
    }

    public Enrollment addStudent(String groupId, String matriculation, String firstName, String lastName) {
        Enrollment student = new Enrollment(null, groupId, firstName, lastName, matriculation, GroupRole.STUDENT);
        return enrollmentRepository.save(student);
    }

    public Enrollment updateStudent(String enrollmentId, String matriculation, String firstName, String lastName) {
        Enrollment s = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new NotFoundException("Étudiant introuvable : " + enrollmentId));
        s.setMatriculation(matriculation);
        s.setFirstName(firstName);
        s.setLastName(lastName);
        return enrollmentRepository.save(s);
    }

    public void exportStudentsToExcel(String groupId, File targetFile) throws IOException {
        Group group = getGroup(groupId);
        List<Enrollment> students = getStudents(groupId);
        studentExcelService.exportStudentsToExcel(students, group.getName(), targetFile);
    }

    public void removeStudent(String enrollmentId) {
        enrollmentRepository.deleteById(enrollmentId);
    }

    public int importStudentsFromExcel(String groupId, File excelFile) throws IOException {
        try (var fis = new FileInputStream(excelFile)) {
            var importedList = studentExcelService.parseStudentsFromExcel(fis);
            int count = 0;
            for (var imp : importedList) {
                if (imp.matriculation() == null || imp.matriculation().isBlank()) continue;
                var existing = enrollmentRepository.findByGroupIdAndMatriculation(groupId, imp.matriculation());
                if (existing.isPresent()) {
                    var s = existing.get();
                    s.setFirstName(imp.firstName());
                    s.setLastName(imp.lastName());
                    s.setEnable(true);
                    enrollmentRepository.save(s);
                } else {
                    addStudent(groupId, imp.matriculation(), imp.firstName(), imp.lastName());
                }
                count++;
            }
            return count;
        }
    }

    // Backward compatibility aliases
    public List<Group> getAllCourses() { return getAllGroups(); }
    public Group getCourse(String id) { return getGroup(id); }
    public Group saveCourse(Group group, File logoFile) { return saveGroup(group, logoFile); }
    public void deleteCourse(String courseId) { deleteGroup(courseId); }
}
