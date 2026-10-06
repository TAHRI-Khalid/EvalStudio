package tk.tahri.eval.desktop.exception;

public class MeasureOrDrawQuestionException extends RuntimeException {
    public MeasureOrDrawQuestionException(Throwable cause) {
        super("Failed to measure or draw question", cause);
    }
}
