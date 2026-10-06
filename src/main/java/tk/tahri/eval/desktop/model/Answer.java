package tk.tahri.eval.desktop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Answer(
        String id,
        String content,
        boolean correct
) {
}
