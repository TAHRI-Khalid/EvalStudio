package tk.tahri.eval.desktop.exception;

public class OthersPagesHeaderException extends RuntimeException {
    public OthersPagesHeaderException(Throwable cause) {
        super("Failed to draw other pages header", cause);
    }
}
