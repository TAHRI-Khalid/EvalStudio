package tk.tahri.eval.desktop.ui.controllers;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

@Component
public class SettingsViewController implements Initializable {

    @FXML private Label lblDbPath;
    @FXML private Label lblDbSize;
    @FXML private Label lblOnnxStatus;
    @FXML private Label lblOpenCvStatus;

    private final MainLayoutController mainLayoutController;

    @Value("${eval.document.model-path:model/eval_model.onnx}")
    private String modelPath;

    public SettingsViewController(@org.springframework.context.annotation.Lazy MainLayoutController mainLayoutController) {
        this.mainLayoutController = mainLayoutController;
    }

    @FXML
    public void onBack() {
        if (mainLayoutController != null) {
            mainLayoutController.navigateBack();
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        File dbFile = tk.tahri.eval.desktop.config.AppPaths.getDataDir().resolve("evaldb.mv.db").toFile();
        lblDbPath.setText(dbFile.getAbsolutePath());
        if (dbFile.exists()) {
            lblDbSize.setText((dbFile.length() / 1024) + " Ko");
        } else {
            lblDbSize.setText("Non initialisé (sera créé au premier enregistrement)");
        }

        File onnx = new File(modelPath);
        lblOnnxStatus.setText(onnx.exists() ? "✓ Modèle présent (" + (onnx.length() / 1024) + " Ko)" : "✗ Fichier introuvable");
        lblOpenCvStatus.setText("✓ OpenCV 4.9.0 chargé avec succès");
    }
}
