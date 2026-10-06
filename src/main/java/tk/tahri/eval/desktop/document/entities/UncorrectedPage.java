package tk.tahri.eval.desktop.document.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "uncorrected_pages", indexes = {
        @Index(name = "uncorrected_eval_idx", columnList = "evaluationId")
})
public class UncorrectedPage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String filename;

    @Enumerated(EnumType.STRING)
    private UncorrectedPageSource source;

    private String groupId;
    private String evaluationId;

    public UncorrectedPage() {
    }

    public UncorrectedPage(String groupId, String evaluationId, String filename, UncorrectedPageSource source) {
        this.groupId = groupId;
        this.evaluationId = evaluationId;
        this.filename = filename;
        this.source = source;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public UncorrectedPageSource getSource() { return source; }
    public void setSource(UncorrectedPageSource source) { this.source = source; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getCourseId() { return groupId; }
    public void setCourseId(String courseId) { this.groupId = courseId; }
    public String getEvaluationId() { return evaluationId; }
    public void setEvaluationId(String evaluationId) { this.evaluationId = evaluationId; }

    public String key() {
        return evaluationId + "/" + filename;
    }
}
