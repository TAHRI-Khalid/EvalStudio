package tk.tahri.eval.desktop.ui.util;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import tk.tahri.eval.desktop.document.dto.TaskProgress;

public class TaskProgressDialog {

    private final Stage stage;
    private final Label lblStageTitle;
    private final Label lblDetail;
    private final Label lblPercentage;
    private final Label lblSteps;
    private final ProgressBar progressBar;
    private final Label lblElapsed;
    private final Label lblEta;
    private final Label lblSuccess;
    private final Label lblWarning;
    private final Button btnCancel;

    private Runnable onCancelAction;
    private volatile boolean cancelled = false;

    public TaskProgressDialog(Window owner, String dialogTitle, String operationHeader, Feather icon) {
        this.stage = new Stage();
        this.stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            this.stage.initOwner(owner);
        }
        this.stage.setTitle(dialogTitle);
        this.stage.initStyle(StageStyle.UTILITY);
        this.stage.setResizable(false);

        // Header
        FontIcon headerIcon = new FontIcon(icon != null ? icon : Feather.LOADER);
        headerIcon.setIconSize(24);
        headerIcon.setIconColor(javafx.scene.paint.Color.web("#004faa"));

        Label lblHeader = new Label(operationHeader);
        lblHeader.setStyle("-fx-font-weight: 800; -fx-font-size: 15px; -fx-text-fill: #181c20;");

        lblStageTitle = new Label("Initialisation...");
        lblStageTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #004faa; -fx-background-color: #e0e7ff; -fx-padding: 3 8; -fx-background-radius: 6px;");

        HBox headerBox = new HBox(10, headerIcon, lblHeader, new Pane(), lblStageTitle);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(headerBox.getChildren().get(2), Priority.ALWAYS);

        // Progress bar and percentage
        progressBar = new ProgressBar(0.0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(10);
        progressBar.setStyle("-fx-accent: #004faa;");

        lblPercentage = new Label("0 %");
        lblPercentage.setStyle("-fx-font-weight: 800; -fx-font-size: 14px; -fx-text-fill: #004faa;");

        lblSteps = new Label("0 / 0");
        lblSteps.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b; -fx-font-weight: 600;");

        HBox percentBox = new HBox(8, lblSteps, new Pane(), lblPercentage);
        percentBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(percentBox.getChildren().get(1), Priority.ALWAYS);

        lblDetail = new Label("Préparation du traitement...");
        lblDetail.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569;");
        lblDetail.setWrapText(true);
        lblDetail.setMaxWidth(480);

        // Time and metrics badges
        lblElapsed = new Label("Écoulé : 00:00");
        lblElapsed.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569; -fx-background-color: #f1f5f9; -fx-padding: 3 8; -fx-background-radius: 4px;");

        lblEta = new Label("Restant : --:--");
        lblEta.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569; -fx-background-color: #f1f5f9; -fx-padding: 3 8; -fx-background-radius: 4px;");

        lblSuccess = new Label("Reconnues : 0");
        lblSuccess.setStyle("-fx-font-size: 11px; -fx-text-fill: #15803d; -fx-background-color: #dcfce7; -fx-padding: 3 8; -fx-background-radius: 4px; -fx-font-weight: 600;");
        lblSuccess.setVisible(false);
        lblSuccess.setManaged(false);

        lblWarning = new Label("Rejetées : 0");
        lblWarning.setStyle("-fx-font-size: 11px; -fx-text-fill: #b91c1c; -fx-background-color: #fee2e2; -fx-padding: 3 8; -fx-background-radius: 4px; -fx-font-weight: 600;");
        lblWarning.setVisible(false);
        lblWarning.setManaged(false);

        HBox metricsBox = new HBox(8, lblElapsed, lblEta, lblSuccess, lblWarning);
        metricsBox.setAlignment(Pos.CENTER_LEFT);

        // Footer Cancel button
        btnCancel = new Button("Annuler");
        btnCancel.setStyle("-fx-background-color: #ffffff; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 6 16; -fx-cursor: hand; -fx-font-size: 12px;");
        btnCancel.setOnAction(e -> handleCancel());

        HBox footerBox = new HBox(btnCancel);
        footerBox.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(14, headerBox, percentBox, progressBar, lblDetail, metricsBox, footerBox);
        root.setPadding(new Insets(20));
        root.setPrefWidth(520);
        root.setStyle("-fx-background-color: #ffffff; -fx-font-family: 'Segoe UI', system-ui, sans-serif;");

        Scene scene = new Scene(root);
        try {
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        } catch (Exception ignored) {
        }

        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            if (!cancelled) {
                e.consume();
                handleCancel();
            }
        });
    }

    public void setOnCancel(Runnable onCancelAction) {
        this.onCancelAction = onCancelAction;
    }

    private void handleCancel() {
        this.cancelled = true;
        btnCancel.setDisable(true);
        btnCancel.setText("Annulation en cours...");
        lblDetail.setText("Arrêt du traitement demandé...");
        if (onCancelAction != null) {
            onCancelAction.run();
        }
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void update(TaskProgress progress) {
        Platform.runLater(() -> {
            if (cancelled) return;

            lblStageTitle.setText(progress.stageTitle());
            progressBar.setProgress(progress.progressRatio());
            lblPercentage.setText(progress.percentage() + " %");
            lblSteps.setText(progress.currentStep() + " / " + progress.totalSteps());
            lblDetail.setText(progress.currentDetail());
            lblElapsed.setText("Écoulé : " + progress.formattedElapsed());
            lblEta.setText("Restant : ~" + progress.formattedEta());

            if (progress.successCount() > 0 || progress.warningCount() > 0) {
                lblSuccess.setVisible(true);
                lblSuccess.setManaged(true);
                lblSuccess.setText("Reconnues : " + progress.successCount());

                lblWarning.setVisible(true);
                lblWarning.setManaged(true);
                lblWarning.setText("Rejetées : " + progress.warningCount());
            }
        });
    }

    public void show() {
        Platform.runLater(stage::show);
    }

    public void close() {
        Platform.runLater(stage::close);
    }
}
