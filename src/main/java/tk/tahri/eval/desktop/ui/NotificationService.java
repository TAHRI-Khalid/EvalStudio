package tk.tahri.eval.desktop.ui;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.Optional;

@Service
public class NotificationService {

    public enum ToastType { INFO, SUCCESS, WARNING, ERROR }

    public interface ToastPresenter {
        void showToast(String message, ToastType type);
    }

    private ToastPresenter toastPresenter;

    public void setToastPresenter(ToastPresenter presenter) {
        this.toastPresenter = presenter;
    }

    public void showToast(String message, ToastType type) {
        if (toastPresenter != null) {
            Platform.runLater(() -> toastPresenter.showToast(message, type));
        } else {
            switch (type) {
                case ERROR -> showError("Erreur", message);
                case WARNING -> showWarning("Attention", message);
                default -> showInfo("Information", message);
            }
        }
    }

    public void showToast(String message) {
        showToast(message, ToastType.INFO);
    }

    public void showInfo(String title, String message) {
        if (toastPresenter != null) {
            showToast(message, ToastType.INFO);
            return;
        }
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public void showWarning(String title, String message) {
        if (toastPresenter != null) {
            showToast(message, ToastType.WARNING);
            return;
        }
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText("Une erreur est survenue");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public boolean confirm(String title, String header, String message) {
        return confirm(title, header, message, "Confirmer", "Annuler");
    }

    public boolean confirm(String title, String header, String message, String confirmText, String cancelText) {
        if (Platform.isFxApplicationThread()) {
            return showConfirmDialog(title, header, message, confirmText, cancelText);
        } else {
            java.util.concurrent.CompletableFuture<Boolean> future = new java.util.concurrent.CompletableFuture<>();
            Platform.runLater(() -> future.complete(showConfirmDialog(title, header, message, confirmText, cancelText)));
            try {
                return future.get();
            } catch (Exception e) {
                return false;
            }
        }
    }

    private boolean showConfirmDialog(String title, String header, String message, String confirmText, String cancelText) {
        try {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(message);

            try {
                URL cssUrl = getClass().getResource("/css/style.css");
                if (cssUrl != null) {
                    alert.getDialogPane().getStylesheets().add(cssUrl.toExternalForm());
                }
            } catch (Exception ignored) {
            }

            javafx.scene.control.ButtonType btnConfirm = new javafx.scene.control.ButtonType(
                    confirmText != null ? confirmText : "Confirmer", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
            javafx.scene.control.ButtonType btnCancel = new javafx.scene.control.ButtonType(
                    cancelText != null ? cancelText : "Annuler", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getDialogPane().getButtonTypes().setAll(btnConfirm, btnCancel);

            javafx.stage.Window owner = javafx.stage.Window.getWindows().stream()
                    .filter(javafx.stage.Window::isShowing)
                    .findFirst()
                    .orElse(null);
            if (owner != null) {
                alert.initOwner(owner);
            }

            // Bouton rouge pour les actions de suppression ou destructives
            boolean isDestructive = isDestructiveAction(title, header, message, confirmText);
            javafx.scene.Node confirmBtn = alert.getDialogPane().lookupButton(btnConfirm);
            if (confirmBtn != null && isDestructive) {
                confirmBtn.getStyleClass().add("btn-danger");
                String baseStyle = "-fx-background-color: #ba1a1a; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6px; -fx-cursor: hand; -fx-padding: 8 16;";
                String hoverStyle = "-fx-background-color: #931212; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6px; -fx-cursor: hand; -fx-padding: 8 16;";
                confirmBtn.setStyle(baseStyle);
                confirmBtn.setOnMouseEntered(e -> confirmBtn.setStyle(hoverStyle));
                confirmBtn.setOnMouseExited(e -> confirmBtn.setStyle(baseStyle));
            }

            javafx.scene.Node cancelBtn = alert.getDialogPane().lookupButton(btnCancel);
            if (cancelBtn != null) {
                cancelBtn.setStyle("-fx-background-radius: 6px; -fx-padding: 8 16; -fx-cursor: hand;");
            }

            Optional<javafx.scene.control.ButtonType> result = alert.showAndWait();
            return result.isPresent() && (result.get() == btnConfirm || result.get() == javafx.scene.control.ButtonType.OK);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isDestructiveAction(String title, String header, String message, String confirmText) {
        String combined = ((title != null ? title : "") + " "
                + (header != null ? header : "") + " "
                + (message != null ? message : "") + " "
                + (confirmText != null ? confirmText : "")).toLowerCase();

        return combined.contains("supprim")
                || combined.contains("retir")
                || combined.contains("effac")
                || combined.contains("reinitialis")
                || combined.contains("réinitialis")
                || combined.contains("reset")
                || combined.contains("delete")
                || combined.contains("remove");
    }
}
