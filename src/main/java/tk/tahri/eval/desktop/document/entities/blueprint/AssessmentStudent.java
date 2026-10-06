package tk.tahri.eval.desktop.document.entities.blueprint;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AssessmentStudent(
        String enrollmentId,
        String firstName,
        String lastName,
        String matriculation
) {
    public String fullName() {
        return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
    }
}
