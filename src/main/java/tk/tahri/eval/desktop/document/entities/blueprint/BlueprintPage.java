package tk.tahri.eval.desktop.document.entities.blueprint;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BlueprintPage {
    public String id;
    public Integer number;
    public boolean corrected = false;
    public List<BlueprintQuestion> blueprintQuestions = new ArrayList<>();

    public BlueprintPage() {
    }

    public BlueprintPage(int number) {
        this.id = UUID.randomUUID().toString();
        this.number = number;
    }

    public void markAsCorrected() {
        this.corrected = true;
    }

    public void initialisation() {
        this.corrected = false;
        for (var blueprintQuestion : blueprintQuestions) {
            blueprintQuestion.initialisation();
        }
    }
}
