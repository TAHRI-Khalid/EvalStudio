package tk.tahri.eval.desktop.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "evaluations", indexes = {
        @Index(name = "group_created_idx", columnList = "groupId, createdAt")
})
public class Evaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    public static final String TYPE_FIN_MODULE = "Évaluation de Fin de Module";
    public static final String TYPE_CONTROLE_CONTINU = "Contrôle Continu";

    @Enumerated(EnumType.STRING)
    private EvaluationState state = EvaluationState.CONCEPTION;

    private LocalDate createdAt = LocalDate.now();
    private String designation;
    private String type = TYPE_CONTROLE_CONTINU;
    private LocalDateTime startDateTime;
    private Duration duration = Duration.ofMinutes(60);

    private boolean sameScore = true;
    private double defaultScoreIfCorrect = 1.0;
    private double defaultScoreIfEmpty = 0.0;
    private double defaultScoreIfIncorrect = 0.0;

    private boolean questionShuffle = false;
    private boolean answersShuffle = false;
    private int columnCount = 1;
    private boolean doubleSided = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<Question> questions = new ArrayList<>();

    private String groupId;
    private String uid;
    private boolean enable = true;

    public Evaluation() {
    }

    public Evaluation(String id, EvaluationState state, LocalDate createdAt, String designation,
                      LocalDateTime startDateTime, Duration duration, boolean sameScore,
                      double defaultScoreIfCorrect, double defaultScoreIfEmpty, double defaultScoreIfIncorrect,
                      boolean questionShuffle, boolean answersShuffle, int columnCount,
                      boolean doubleSided, List<Question> questions, String groupId, String uid, boolean enable) {
        if (Double.isNaN(defaultScoreIfCorrect) || Double.isInfinite(defaultScoreIfCorrect) || defaultScoreIfCorrect <= 0) {
            throw new IllegalArgumentException("La note pour une réponse correcte doit être strictement positive (> 0). Valeur reçue : " + defaultScoreIfCorrect);
        }
        if (Double.isNaN(defaultScoreIfIncorrect) || Double.isInfinite(defaultScoreIfIncorrect) || defaultScoreIfIncorrect > 0) {
            throw new IllegalArgumentException("La note pour une réponse fausse doit être 0 ou négative (≤ 0). Valeur reçue : " + defaultScoreIfIncorrect);
        }
        if (Double.isNaN(defaultScoreIfEmpty) || Double.isInfinite(defaultScoreIfEmpty)) {
            throw new IllegalArgumentException("La note pour « sans réponse » doit être un nombre valide.");
        }
        this.id = id;
        this.state = state != null ? state : EvaluationState.CONCEPTION;
        this.createdAt = createdAt != null ? createdAt : LocalDate.now();
        this.designation = designation;
        this.startDateTime = startDateTime;
        this.duration = duration;
        this.sameScore = sameScore;
        this.defaultScoreIfCorrect = defaultScoreIfCorrect;
        this.defaultScoreIfEmpty = defaultScoreIfEmpty;
        this.defaultScoreIfIncorrect = defaultScoreIfIncorrect;
        this.questionShuffle = questionShuffle;
        this.answersShuffle = answersShuffle;
        this.columnCount = columnCount > 0 ? columnCount : 1;
        this.doubleSided = doubleSided;
        this.questions = questions != null ? questions : new ArrayList<>();
        this.groupId = groupId;
        this.uid = uid;
        this.enable = enable;
    }

    // Record-style accessors
    public String id() { return id; }
    public EvaluationState state() { return state; }
    public LocalDate createdAt() { return createdAt; }
    public String designation() { return designation; }
    public String type() { return type; }
    public LocalDateTime startDateTime() { return startDateTime; }
    public Duration duration() { return duration; }
    public boolean sameScore() { return sameScore; }
    public double defaultScoreIfCorrect() { return defaultScoreIfCorrect; }
    public double defaultScoreIfEmpty() { return defaultScoreIfEmpty; }
    public double defaultScoreIfIncorrect() { return defaultScoreIfIncorrect; }
    public boolean questionShuffle() { return questionShuffle; }
    public boolean answersShuffle() { return answersShuffle; }
    public int columnCount() { return columnCount; }
    public boolean doubleSided() { return doubleSided; }
    public List<Question> questions() { return questions; }
    public String groupId() { return groupId; }
    public String courseId() { return groupId; }
    public String uid() { return uid; }
    public boolean enable() { return enable; }

    // Standard JavaBeans getters & setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public EvaluationState getState() { return state; }
    public void setState(EvaluationState state) { this.state = state; }
    public LocalDate getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDate createdAt) { this.createdAt = createdAt; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public LocalDateTime getStartDateTime() { return startDateTime; }
    public void setStartDateTime(LocalDateTime startDateTime) { this.startDateTime = startDateTime; }
    public Duration getDuration() { return duration; }
    public void setDuration(Duration duration) { this.duration = duration; }
    public boolean isSameScore() { return sameScore; }
    public void setSameScore(boolean sameScore) { this.sameScore = sameScore; }
    public double getDefaultScoreIfCorrect() { return defaultScoreIfCorrect; }
    public void setDefaultScoreIfCorrect(double defaultScoreIfCorrect) {
        if (Double.isNaN(defaultScoreIfCorrect) || Double.isInfinite(defaultScoreIfCorrect) || defaultScoreIfCorrect <= 0) {
            throw new IllegalArgumentException("La note pour une réponse correcte doit être strictement positive (> 0). Valeur reçue : " + defaultScoreIfCorrect);
        }
        this.defaultScoreIfCorrect = defaultScoreIfCorrect;
    }
    public double getDefaultScoreIfEmpty() { return defaultScoreIfEmpty; }
    public void setDefaultScoreIfEmpty(double defaultScoreIfEmpty) {
        if (Double.isNaN(defaultScoreIfEmpty) || Double.isInfinite(defaultScoreIfEmpty)) {
            throw new IllegalArgumentException("La note pour « sans réponse » doit être un nombre valide.");
        }
        this.defaultScoreIfEmpty = defaultScoreIfEmpty;
    }
    public double getDefaultScoreIfIncorrect() { return defaultScoreIfIncorrect; }
    public void setDefaultScoreIfIncorrect(double defaultScoreIfIncorrect) {
        if (Double.isNaN(defaultScoreIfIncorrect) || Double.isInfinite(defaultScoreIfIncorrect) || defaultScoreIfIncorrect > 0) {
            throw new IllegalArgumentException("La note pour une réponse fausse doit être 0 ou négative (≤ 0). Valeur reçue : " + defaultScoreIfIncorrect);
        }
        this.defaultScoreIfIncorrect = defaultScoreIfIncorrect;
    }
    public boolean isQuestionShuffle() { return questionShuffle; }
    public void setQuestionShuffle(boolean questionShuffle) { this.questionShuffle = questionShuffle; }
    public boolean isAnswersShuffle() { return answersShuffle; }
    public void setAnswersShuffle(boolean answersShuffle) { this.answersShuffle = answersShuffle; }
    public int getColumnCount() { return columnCount; }
    public void setColumnCount(int columnCount) { this.columnCount = columnCount; }
    public boolean isDoubleSided() { return doubleSided; }
    public void setDoubleSided(boolean doubleSided) { this.doubleSided = doubleSided; }
    public List<Question> getQuestions() { return questions; }
    public void setQuestions(List<Question> questions) { this.questions = questions; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getCourseId() { return groupId; }
    public void setCourseId(String courseId) { this.groupId = courseId; }
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }
    public boolean isEnable() { return enable; }
    public void setEnable(boolean enable) { this.enable = enable; }

    @Override
    public String toString() {
        return designation != null ? designation : "Évaluation sans nom";
    }
}
