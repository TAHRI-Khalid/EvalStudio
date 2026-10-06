package tk.tahri.eval.desktop.document.entities.blueprint;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tk.tahri.eval.desktop.model.Question;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BlueprintQuestion {
    public String id;
    public Integer number;
    public String content;
    public String imagePath;
    public double scoreIfCorrect;
    public double scoreIfEmpty;
    public double scoreIfIncorrect;
    public boolean corrected = false;
    public QuestionState state = QuestionState.EMPTY;
    public List<BlueprintAnswer> blueprintAnswers = new ArrayList<>();

    public BlueprintQuestion() {
    }

    public BlueprintQuestion(Question question, int questionNumber) {
        this.id = question.id();
        this.number = questionNumber;
        this.content = question.content();
        this.imagePath = question.imagePath();
        this.scoreIfCorrect = question.scoreIfCorrect();
        this.scoreIfEmpty = question.scoreIfEmpty();
        this.scoreIfIncorrect = question.scoreIfIncorrect();
        this.state = QuestionState.EMPTY;
    }

    public double getScore() {
        if (state == null) {
            return scoreIfEmpty;
        }
        return switch (state) {
            case CORRECT -> this.scoreIfCorrect;
            case EMPTY -> this.scoreIfEmpty;
            case INCORRECT -> this.scoreIfIncorrect;
        };
    }

    public void markAsCorrectedAndSetQuestionState() {
        this.corrected = true;
        boolean hasAnyResponse = this.blueprintAnswers.stream().anyMatch(a -> a.response);
        if (!hasAnyResponse) {
            this.state = QuestionState.EMPTY;
            return;
        }
        boolean allMatch = this.blueprintAnswers.stream().allMatch(a -> a.correct == a.response);
        if (allMatch) {
            this.state = QuestionState.CORRECT;
        } else {
            this.state = QuestionState.INCORRECT;
        }
    }

    public void initialisation() {
        this.corrected = false;
        this.state = QuestionState.EMPTY;
        for (var blueprintAnswer : blueprintAnswers) {
            blueprintAnswer.response = false;
        }
    }
}
