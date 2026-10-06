package tk.tahri.eval.desktop.exception;

public class WriteBarcodeException extends RuntimeException {
    public WriteBarcodeException(String barcode) {
        super("Failed to write barcode: " + barcode);
    }
}
