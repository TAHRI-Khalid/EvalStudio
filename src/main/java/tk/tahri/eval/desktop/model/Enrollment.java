package tk.tahri.eval.desktop.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "enrollments", indexes = {
        @Index(name = "group_role_enable_idx", columnList = "groupId, role, enable")
})
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String uid;
    private String groupId;
    private String firstName;
    private String lastName;
    private String matriculation;

    @Enumerated(EnumType.STRING)
    private GroupRole role = GroupRole.STUDENT;

    private Instant enrolledAt = Instant.now();
    private boolean enable = true;

    public Enrollment() {
    }

    public Enrollment(String id, String groupId, String firstName, String lastName, String matriculation, GroupRole role) {
        this.id = id;
        this.groupId = groupId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.matriculation = matriculation;
        this.role = role != null ? role : GroupRole.STUDENT;
        this.enrolledAt = Instant.now();
        this.enable = true;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    // Compatibility methods for courseId
    public String getCourseId() {
        return groupId;
    }

    public void setCourseId(String courseId) {
        this.groupId = courseId;
    }

    public String courseId() {
        return groupId;
    }

    public String groupId() {
        return groupId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getMatriculation() {
        return matriculation;
    }

    public void setMatriculation(String matriculation) {
        this.matriculation = matriculation;
    }

    public GroupRole getRole() {
        return role;
    }

    public void setRole(GroupRole role) {
        this.role = role;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }

    public void setEnrolledAt(Instant enrolledAt) {
        this.enrolledAt = enrolledAt;
    }

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public String fullName() {
        return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
    }
}
