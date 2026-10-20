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
    public Question {
        if (Double.isNaN(scoreIfCorrect) || Double.isInfinite(scoreIfCorrect) || scoreIfCorrect <= 0) {
            throw new IllegalArgumentException("La note pour une réponse correcte doit être strictement positive (> 0). Valeur reçue : " + scoreIfCorrect);
        }
        if (Double.isNaN(scoreIfIncorrect) || Double.isInfinite(scoreIfIncorrect) || scoreIfIncorrect > 0) {
            throw new IllegalArgumentException("La note pour une réponse fausse doit être 0 ou négative (≤ 0). Valeur reçue : " + scoreIfIncorrect);
        }
        if (Double.isNaN(scoreIfEmpty) || Double.isInfinite(scoreIfEmpty)) {
            throw new IllegalArgumentException("La note pour « sans réponse » doit être un nombre valide.");
        }
    }
}
