package tk.tahri.eval.desktop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Question(
        String id,
        String content,
        String imagePath,
        boolean multipleChoice,
        double scoreIfCorrect,
        double scoreIfEmpty,
        double scoreIfIncorrect,
        List<Answer> answers
) {
}
