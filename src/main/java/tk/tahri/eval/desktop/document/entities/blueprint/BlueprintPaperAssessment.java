package tk.tahri.eval.desktop.document.entities.blueprint;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tk.tahri.eval.desktop.model.Evaluation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "blueprint_paper_assessments", indexes = {
        @Index(name = "bp_paper_group_eval_idx", columnList = "groupId, evaluationId")
})
public class BlueprintPaperAssessment implements Persistable<String> {

    @Id
    public String id;

    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    public String groupId;
    public String evaluationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    public AssessmentStudent student;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    public List<BlueprintPage> blueprintPages = new ArrayList<>();

    public BlueprintPaperAssessment() {
    }

    public BlueprintPaperAssessment(Evaluation evaluation, AssessmentStudent student) {
        this.id = UUID.randomUUID().toString();
        this.groupId = evaluation.groupId();
        this.evaluationId = evaluation.id();
        this.student = student;
    }

    public void addBluePage() {
        this.blueprintPages.add(new BlueprintPage(blueprintPages.size() + 1));
    }

    public void addBlueQuestion(BlueprintQuestion blueprintQuestion) {
        if (!this.blueprintPages.isEmpty()) {
            this.blueprintPages.getLast().blueprintQuestions.add(blueprintQuestion);
        }
    }

    public void initialisation() {
        for (var blueprintPage : this.blueprintPages) {
            blueprintPage.initialisation();
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getCourseId() { return groupId; }
    public void setCourseId(String courseId) { this.groupId = courseId; }
    public String getEvaluationId() { return evaluationId; }
    public void setEvaluationId(String evaluationId) { this.evaluationId = evaluationId; }
    public AssessmentStudent getStudent() { return student; }
    public void setStudent(AssessmentStudent student) { this.student = student; }
    public List<BlueprintPage> getBlueprintPages() { return blueprintPages; }
    public void setBlueprintPages(List<BlueprintPage> blueprintPages) { this.blueprintPages = blueprintPages; }
}
