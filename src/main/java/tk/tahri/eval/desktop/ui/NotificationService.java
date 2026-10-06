package tk.tahri.eval.desktop.ui;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class NotificationService {

    public void showInfo(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public void showWarning(String title, String message) {
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
        if (Platform.isFxApplicationThread()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(message);
            Optional<ButtonType> result = alert.showAndWait();
            return result.isPresent() && result.get() == ButtonType.OK;
        } else {
            java.util.concurrent.CompletableFuture<Boolean> future = new java.util.concurrent.CompletableFuture<>();
            Platform.runLater(() -> {
                try {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle(title);
                    alert.setHeaderText(header);
                    alert.setContentText(message);
                    Optional<ButtonType> result = alert.showAndWait();
                    future.complete(result.isPresent() && result.get() == ButtonType.OK);
                } catch (Exception e) {
                    future.complete(false);
                }
            });
            try {
                return future.get();
            } catch (Exception e) {
                return false;
            }
        }
    }
}
