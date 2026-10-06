package tk.tahri.eval.desktop.document.dto;

public record StudentDetails(
        String enrollmentId,
        String matriculation,
        String firstName,
        String lastName,
        double score,
        long correctedQuestions,
        long totalQuestions
) {
    public String fullName() {
        return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
    }
}
