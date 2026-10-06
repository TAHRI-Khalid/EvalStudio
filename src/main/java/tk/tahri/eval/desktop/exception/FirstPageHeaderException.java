package tk.tahri.eval.desktop.exception;

public class FirstPageHeaderException extends RuntimeException {
    public FirstPageHeaderException(Throwable cause) {
        super("Failed to draw first page header", cause);
    }
}
