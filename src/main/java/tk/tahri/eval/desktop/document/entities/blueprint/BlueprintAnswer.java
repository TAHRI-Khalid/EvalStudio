package tk.tahri.eval.desktop.document.entities.blueprint;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tk.tahri.eval.desktop.model.Answer;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BlueprintAnswer {
    public String id;
    public String content;
    public boolean correct;
    public float x;
    public float y;
    public boolean response = false;

    public BlueprintAnswer() {
    }

    public BlueprintAnswer(Answer answer) {
        this.id = answer.id();
        this.content = answer.content();
        this.correct = answer.correct();
    }

    public BlueprintAnswer(Answer answer, float x, float y) {
        this.id = answer.id();
        this.content = answer.content();
        this.correct = answer.correct();
        this.x = x;
        this.y = y;
    }
}
