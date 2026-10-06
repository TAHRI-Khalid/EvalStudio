package tk.tahri.eval.desktop.exception;

public class WritePageNumberException extends RuntimeException {
    public WritePageNumberException(String barcode) {
        super("Failed to write page number for barcode: " + barcode);
    }
}
