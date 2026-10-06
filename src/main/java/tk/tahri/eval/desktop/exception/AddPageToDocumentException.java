package tk.tahri.eval.desktop.exception;

public class AddPageToDocumentException extends RuntimeException {
    public AddPageToDocumentException() {
        super("Failed to add page to PDF document");
    }
}
