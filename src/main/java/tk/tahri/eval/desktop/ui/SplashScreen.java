package tk.tahri.eval.desktop.ui;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import tk.tahri.eval.desktop.config.AppVersion;

/**
 * Écran de démarrage (Splash Screen) affiché immédiatement au lancement de l'application
 * pour informer l'utilisateur de l'initialisation des services et de Spring Boot.
 */
public class SplashScreen {

    private final Stage stage;
    private final Label statusLabel;
    private final ProgressBar progressBar;
    private final VBox cardRoot;

    public SplashScreen() {
        this.stage = new Stage();
        stage.initStyle(StageStyle.TRANSPARENT);

        // Conteneur de carte moderne
        cardRoot = new VBox(18);
        cardRoot.setAlignment(Pos.CENTER);
        cardRoot.setPadding(new Insets(28, 32, 28, 32));
        cardRoot.setPrefWidth(420);
        cardRoot.setMaxWidth(420);

        // Style moderne avec bordure douce et coins arrondis
        cardRoot.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 16px;" +
            "-fx-border-color: rgba(0, 0, 0, 0.08);" +
            "-fx-border-radius: 16px;" +
            "-fx-border-width: 1px;"
        );

        // Ombre portée douce
        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.rgb(0, 0, 0, 0.16));
        shadow.setRadius(24);
        shadow.setOffsetY(8);
        cardRoot.setEffect(shadow);

        // En-tête : Logo + Titre + Sous-titre
        HBox brandHeader = new HBox(14);
        brandHeader.setAlignment(Pos.CENTER_LEFT);

        ImageView iconView = new ImageView();
        try {
            var iconStream = getClass().getResourceAsStream("/icons/app-icon.png");
            if (iconStream != null) {
                Image appIcon = new Image(iconStream);
                iconView.setImage(appIcon);
                iconView.setFitWidth(52);
                iconView.setFitHeight(52);
                iconView.setPreserveRatio(true);
                iconView.setSmooth(true);
                stage.getIcons().add(appIcon);
            }
        } catch (Exception ignored) {}

        VBox titleBox = new VBox(2);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("EvalStudio");
        titleLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: 800; -fx-text-fill: #0f172a;");

        Label subtitleLabel = new Label("Studio d'Évaluation & Correction Automatique");
        subtitleLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 500; -fx-text-fill: #64748b;");

        titleBox.getChildren().addAll(titleLabel, subtitleLabel);
        brandHeader.getChildren().addAll(iconView, titleBox);

        // Barre de progression indéterminée moderne
        progressBar = new ProgressBar(-1);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(6);
        progressBar.setStyle(
            "-fx-accent: #2563eb;" +
            "-fx-background-radius: 4px;" +
            "-fx-border-radius: 4px;"
        );

        // Pied : Libellé de statut et badge de version
        HBox footerBox = new HBox(10);
        footerBox.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label("Démarrage en cours...");
        statusLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 500; -fx-text-fill: #475569;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label versionLabel = new Label(AppVersion.getVersion());
        versionLabel.setStyle(
            "-fx-font-size: 11px;" +
            "-fx-font-weight: 600;" +
            "-fx-text-fill: #64748b;" +
            "-fx-background-color: #f1f5f9;" +
            "-fx-background-radius: 6px;" +
            "-fx-padding: 2 6 2 6;"
        );

        footerBox.getChildren().addAll(statusLabel, spacer, versionLabel);

        cardRoot.getChildren().addAll(brandHeader, progressBar, footerBox);

        // Wrapper transparent pour préserver le débordement de l'ombre portée
        VBox wrapper = new VBox(cardRoot);
        wrapper.setPadding(new Insets(20));
        wrapper.setStyle("-fx-background-color: transparent;");
        wrapper.setAlignment(Pos.CENTER);

        Scene scene = new Scene(wrapper);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        stage.centerOnScreen();
    }

    /**
     * Affiche l'écran de démarrage avec une transition douce en fondu d'entrée.
     */
    public void show() {
        stage.show();
        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), cardRoot);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);
        fadeIn.play();
    }

    /**
     * Met à jour le message d'état affiché à l'utilisateur.
     */
    public void setProgressText(String message) {
        if (message != null) {
            statusLabel.setText(message);
        }
    }

    /**
     * Ferme l'écran avec une transition douce en fondu de sortie.
     */
    public void closeWithFade(Runnable onClosed) {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(250), cardRoot);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(e -> {
            stage.close();
            if (onClosed != null) {
                onClosed.run();
            }
        });
        fadeOut.play();
    }

    public Stage getStage() {
        return stage;
    }
}
