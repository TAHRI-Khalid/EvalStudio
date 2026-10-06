package tk.tahri.eval.desktop.document.dto;

public record StudentGrade(
        String assessmentId,
        String enrollmentId,
        String matriculation,
        String firstName,
        String lastName,
        Double score,
        long correctedQuestions,
        long totalQuestions,
        int correctedPages,
        int totalPages,
        boolean fullyCorrected
) {
    public String fullName() {
        return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
    }

    public String getMatriculation() { return matriculation; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public Double getScore() { return score; }
    public String getFullName() { return fullName(); }
    public String getAssessmentId() { return assessmentId; }
    public String getEnrollmentId() { return enrollmentId; }
    public long getCorrectedQuestions() { return correctedQuestions; }
    public long getTotalQuestions() { return totalQuestions; }
    public int getCorrectedPages() { return correctedPages; }
    public int getTotalPages() { return totalPages; }
    public boolean isFullyCorrected() { return fullyCorrected; }
}
