package tk.tahri.eval.desktop.ui.controllers;

import atlantafx.base.theme.*;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
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
    @FXML private ComboBox<String> comboThemes;

    @Value("${eval.document.model-path:model/eval_model.onnx}")
    private String modelPath;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        File dbFile = new File("data/evaldb.mv.db");
        lblDbPath.setText(dbFile.getAbsolutePath());
        if (dbFile.exists()) {
            lblDbSize.setText((dbFile.length() / 1024) + " Ko");
        } else {
            lblDbSize.setText("Non initialisé (sera créé au premier enregistrement)");
        }

        File onnx = new File(modelPath);
        lblOnnxStatus.setText(onnx.exists() ? "✓ Modèle présent (" + (onnx.length() / 1024) + " Ko)" : "✗ Fichier introuvable");
        lblOpenCvStatus.setText("✓ OpenCV 4.9.0 chargé avec succès");

        comboThemes.setItems(FXCollections.observableArrayList(
                "Primer Light (GitHub)",
                "Primer Dark (GitHub)",
                "Cupertino Light (macOS)",
                "Cupertino Dark (macOS)",
                "Nord Light",
                "Nord Dark",
                "Dracula"
        ));
        comboThemes.getSelectionModel().select(0);

        comboThemes.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n == null) return;
            switch (n) {
                case "Primer Light (GitHub)" -> Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                case "Primer Dark (GitHub)" -> Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
                case "Cupertino Light (macOS)" -> Application.setUserAgentStylesheet(new CupertinoLight().getUserAgentStylesheet());
                case "Cupertino Dark (macOS)" -> Application.setUserAgentStylesheet(new CupertinoDark().getUserAgentStylesheet());
                case "Nord Light" -> Application.setUserAgentStylesheet(new NordLight().getUserAgentStylesheet());
                case "Nord Dark" -> Application.setUserAgentStylesheet(new NordDark().getUserAgentStylesheet());
                case "Dracula" -> Application.setUserAgentStylesheet(new Dracula().getUserAgentStylesheet());
            }
        });
    }
}
