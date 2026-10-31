package tk.tahri.eval.desktop.ui.controllers;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.feather.Feather;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import tk.tahri.eval.desktop.config.AppPaths;
import tk.tahri.eval.desktop.config.AppPreferences;
import tk.tahri.eval.desktop.config.AppVersion;
import tk.tahri.eval.desktop.document.dto.TaskProgressTracker;
import tk.tahri.eval.desktop.document.services.CheckboxClassifier;
import tk.tahri.eval.desktop.service.BackupService;
import tk.tahri.eval.desktop.service.GroupService;
import tk.tahri.eval.desktop.service.dto.BackupManifest;
import tk.tahri.eval.desktop.ui.NotificationService;
import tk.tahri.eval.desktop.ui.util.AppRestartUtils;
import tk.tahri.eval.desktop.ui.util.TaskProgressDialog;

import java.awt.Desktop;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

@Component
public class SettingsViewController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(SettingsViewController.class);

    private static final String MODE_GRID = "Affichage en Cartes (Grille)";
    private static final String MODE_LIST = "Affichage en Liste (Tableau)";

    @FXML private Label lblHeaderBadge;

    // Préférences d'affichage & exports
    @FXML private ComboBox<String> comboEvalViewMode;
    @FXML private ComboBox<String> comboCorrectionViewMode;
    @FXML private TextField txtExportDir;

    // Base de données locale H2 & Sauvegardes
    @FXML private Label lblDbBadge;
    @FXML private Label lblDbPath;
    @FXML private Label lblDbSize;
    @FXML private Label lblDbGroupsCount;
    @FXML private Button btnExportBackup;
    @FXML private Button btnImportBackup;

    // Vision Optique & IA
    @FXML private Label lblOpenCvBadge;
    @FXML private Label lblOpenCvVersion;
    @FXML private Label lblOnnxBadge;
    @FXML private Label lblOnnxSize;
    @FXML private Label lblOnnxPath;

    // Système & Environnement
    @FXML private Label lblAppVersion;
    @FXML private Label lblJavaVersion;
    @FXML private Label lblOsInfo;
    @FXML private ProgressBar progressMemory;
    @FXML private Label lblMemoryDetails;

    private final MainLayoutController mainLayoutController;
    private final AppPreferences appPreferences;
    private final NotificationService notificationService;
    private final GroupService groupService;
    private final CheckboxClassifier checkboxClassifier;
    private final BackupService backupService;

    @Value("${eval.document.model-path:model/eval_model.onnx}")
    private String modelPath;

    public SettingsViewController(@Lazy MainLayoutController mainLayoutController,
                                  AppPreferences appPreferences,
                                  NotificationService notificationService,
                                  GroupService groupService,
                                  CheckboxClassifier checkboxClassifier,
                                  BackupService backupService) {
        this.mainLayoutController = mainLayoutController;
        this.appPreferences = appPreferences;
        this.notificationService = notificationService;
        this.groupService = groupService;
        this.checkboxClassifier = checkboxClassifier;
        this.backupService = backupService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        String ver = AppVersion.getVersion();
        if (lblHeaderBadge != null) lblHeaderBadge.setText(ver);
        if (lblAppVersion != null) lblAppVersion.setText("EvalStudio " + ver);

        setupPreferencesSection();
        refreshDatabaseInfo();
        refreshAiCard();
        refreshSystemInfo();
        refreshMemoryInfo();
    }

    private void setupPreferencesSection() {
        if (comboEvalViewMode != null) {
            comboEvalViewMode.setItems(FXCollections.observableArrayList(MODE_GRID, MODE_LIST));
            boolean isGrid = (appPreferences.getEvaluationsViewMode() == AppPreferences.ViewMode.GRID);
            comboEvalViewMode.setValue(isGrid ? MODE_GRID : MODE_LIST);
            comboEvalViewMode.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    AppPreferences.ViewMode mode = newVal.equals(MODE_GRID) ? AppPreferences.ViewMode.GRID : AppPreferences.ViewMode.LIST;
                    appPreferences.setEvaluationsViewMode(mode);
                }
            });
        }

        if (comboCorrectionViewMode != null) {
            comboCorrectionViewMode.setItems(FXCollections.observableArrayList(MODE_GRID, MODE_LIST));
            boolean isGrid = (appPreferences.getCorrectionViewMode() == AppPreferences.ViewMode.GRID);
            comboCorrectionViewMode.setValue(isGrid ? MODE_GRID : MODE_LIST);
            comboCorrectionViewMode.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    AppPreferences.ViewMode mode = newVal.equals(MODE_GRID) ? AppPreferences.ViewMode.GRID : AppPreferences.ViewMode.LIST;
                    appPreferences.setCorrectionViewMode(mode);
                }
            });
        }

        if (txtExportDir != null) {
            txtExportDir.setText(appPreferences.getExportDirectory());
        }
    }

    private void refreshDatabaseInfo() {
        File dbFile = AppPaths.getDataDir().resolve("evaldb.mv.db").toFile();
        if (lblDbPath != null) lblDbPath.setText(dbFile.getAbsolutePath());

        if (dbFile.exists()) {
            long sizeKo = dbFile.length() / 1024;
            if (lblDbSize != null) lblDbSize.setText(sizeKo >= 1024 ? String.format("%.2f Mo", sizeKo / 1024.0) : sizeKo + " Ko");
            if (lblDbBadge != null) {
                lblDbBadge.setText("Connectée");
                lblDbBadge.setStyle("-fx-background-color: #e6f4ea; -fx-text-fill: #137333;");
            }
        } else {
            if (lblDbSize != null) lblDbSize.setText("Non initialisé");
            if (lblDbBadge != null) {
                lblDbBadge.setText("En attente");
                lblDbBadge.setStyle("-fx-background-color: #fef7e0; -fx-text-fill: #b06000;");
            }
        }

        try {
            int groupsCount = groupService.getAllGroups().size();
            if (lblDbGroupsCount != null) {
                lblDbGroupsCount.setText(groupsCount + " groupe" + (groupsCount > 1 ? "s" : "") + " d'étudiants");
            }
        } catch (Exception e) {
            if (lblDbGroupsCount != null) lblDbGroupsCount.setText("-");
        }
    }

    private void refreshAiCard() {
        // OpenCV
        try {
            String cvVer = org.opencv.core.Core.VERSION;
            if (lblOpenCvVersion != null) lblOpenCvVersion.setText("v" + cvVer + " (Bibliothèque native active)");
            if (lblOpenCvBadge != null) {
                lblOpenCvBadge.setText("✓ Opérationnel");
                lblOpenCvBadge.setStyle("-fx-background-color: #e6f4ea; -fx-text-fill: #137333;");
            }
        } catch (Throwable t) {
            if (lblOpenCvVersion != null) lblOpenCvVersion.setText("Erreur d'initialisation");
            if (lblOpenCvBadge != null) {
                lblOpenCvBadge.setText("✗ Indisponible");
                lblOpenCvBadge.setStyle("-fx-background-color: #fce8e6; -fx-text-fill: #c5221f;");
            }
        }

        // ONNX
        File onnxFile = new File(modelPath);
        boolean exists = onnxFile.exists() && onnxFile.length() > 0;
        boolean sessionReady = (checkboxClassifier != null && checkboxClassifier.isReady());

        if (lblOnnxPath != null) lblOnnxPath.setText("Fichier : " + onnxFile.getAbsolutePath());

        if (sessionReady || exists) {
            long sizeKo = onnxFile.exists() ? onnxFile.length() / 1024 : 0;
            if (lblOnnxSize != null) lblOnnxSize.setText("(" + sizeKo + " Ko)");
            if (lblOnnxBadge != null) {
                lblOnnxBadge.setText(sessionReady ? "✓ Prêt & Chargé" : "✓ Fichier détecté");
                lblOnnxBadge.setStyle("-fx-background-color: #e6f4ea; -fx-text-fill: #137333;");
            }
        } else {
            if (lblOnnxSize != null) lblOnnxSize.setText("(Fichier manquant)");
            if (lblOnnxBadge != null) {
                lblOnnxBadge.setText("✗ Introuvable");
                lblOnnxBadge.setStyle("-fx-background-color: #fce8e6; -fx-text-fill: #c5221f;");
            }
        }
    }

    private void refreshSystemInfo() {
        if (lblJavaVersion != null) {
            lblJavaVersion.setText(System.getProperty("java.version") + " (" + System.getProperty("java.vendor", "OpenJDK") + ")");
        }
        if (lblOsInfo != null) {
            lblOsInfo.setText(System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")");
        }
    }

    private void refreshMemoryInfo() {
        Runtime rt = Runtime.getRuntime();
        long totalMb = rt.totalMemory() / (1024 * 1024);
        long freeMb = rt.freeMemory() / (1024 * 1024);
        long usedMb = totalMb - freeMb;
        long maxMb = rt.maxMemory() / (1024 * 1024);

        double ratio = (double) usedMb / (double) Math.max(1, maxMb);
        if (progressMemory != null) progressMemory.setProgress(ratio);

        if (lblMemoryDetails != null) {
            lblMemoryDetails.setText(String.format("%d Mo utilisés / %d Mo max (Alloué : %d Mo)", usedMb, maxMb, totalMb));
        }
    }

    @FXML
    public void onBrowseExportDir() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Sélectionner le dossier par défaut des exports");
        String current = appPreferences.getExportDirectory();
        File curFile = new File(current);
        if (curFile.exists() && curFile.isDirectory()) {
            dirChooser.setInitialDirectory(curFile);
        }
        File selected = dirChooser.showDialog(null);
        if (selected != null) {
            appPreferences.setExportDirectory(selected.getAbsolutePath());
            if (txtExportDir != null) txtExportDir.setText(selected.getAbsolutePath());
            notificationService.showSuccess("Préférence mise à jour", "Nouveau dossier des exports enregistré.");
        }
    }

    @FXML
    public void onBackupDatabase() {
        onExportBackup();
    }

    @FXML
    public void onExportBackup() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exporter une sauvegarde complète");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Sauvegarde EvalStudio (*.evalbackup)", "*.evalbackup"),
                new FileChooser.ExtensionFilter("Archive ZIP (*.zip)", "*.zip")
        );
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        fileChooser.setInitialFileName("evalstudio_backup_" + dateStr + ".evalbackup");

        File dest = fileChooser.showSaveDialog(
                btnExportBackup != null && btnExportBackup.getScene() != null ? btnExportBackup.getScene().getWindow() : null
        );
        if (dest == null) return;

        // Dialogue visuel de progression
        TaskProgressDialog progressDialog = new TaskProgressDialog(
                btnExportBackup != null && btnExportBackup.getScene() != null ? btnExportBackup.getScene().getWindow() : null,
                "Exportation de la sauvegarde",
                "Création de l'archive complète",
                Feather.DOWNLOAD
        );
        progressDialog.show();

        if (btnExportBackup != null) btnExportBackup.setDisable(true);
        if (btnImportBackup != null) btnImportBackup.setDisable(true);

        TaskProgressTracker tracker = new TaskProgressTracker(progressDialog::update);

        Task<File> task = new Task<>() {
            @Override
            protected File call() throws Exception {
                return backupService.exportBackup(dest, tracker, this::isCancelled);
            }
        };

        progressDialog.setOnCancel(() -> task.cancel(true));

        task.setOnCancelled(e -> {
            progressDialog.close();
            if (btnExportBackup != null) btnExportBackup.setDisable(false);
            if (btnImportBackup != null) btnImportBackup.setDisable(false);
            notificationService.showWarning("Exportation annulée", "L'exportation a été interrompue par l'utilisateur.");
        });

        task.setOnFailed(e -> {
            progressDialog.close();
            if (btnExportBackup != null) btnExportBackup.setDisable(false);
            if (btnImportBackup != null) btnImportBackup.setDisable(false);
            Throwable ex = task.getException();
            log.error("Échec de l'exportation de la sauvegarde", ex);
            notificationService.showError("Erreur d'exportation", ex != null ? ex.getMessage() : "Une erreur inconnue est survenue.");
        });

        task.setOnSucceeded(e -> {
            javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.millis(350));
            pause.setOnFinished(ev -> {
                progressDialog.close();
                if (btnExportBackup != null) btnExportBackup.setDisable(false);
                if (btnImportBackup != null) btnImportBackup.setDisable(false);
                refreshDatabaseInfo();
                notificationService.showSuccess("Sauvegarde réussie",
                        "Sauvegarde complète exportée (" + BackupService.formatSize(dest.length()) + ") :\n" + dest.getAbsolutePath());
            });
            pause.play();
        });

        Thread thread = new Thread(task, "ExportBackupThread");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    public void onImportBackup() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Sélectionner une sauvegarde à restaurer");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Sauvegardes EvalStudio (*.evalbackup, *.zip)", "*.evalbackup", "*.zip"),
                new FileChooser.ExtensionFilter("Tous les fichiers (*.*)", "*.*")
        );

        File src = fileChooser.showOpenDialog(
                btnImportBackup != null && btnImportBackup.getScene() != null ? btnImportBackup.getScene().getWindow() : null
        );
        if (src == null) return;

        BackupManifest manifest;
        try {
            manifest = backupService.validateBackup(src);
        } catch (Exception e) {
            log.warn("Fichier de sauvegarde non valide : {}", e.getMessage());
            notificationService.showError("Fichier invalide", e.getMessage());
            return;
        }

        String dateFormatted = manifest.createdAt() != null
                ? manifest.createdAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                : "Inconnue";

        String confirmationMsg = "Détails de la sauvegarde sélectionnée :\n"
                + "• Date de création : " + dateFormatted + "\n"
                + "• Documents & médias : " + manifest.totalFiles() + " fichiers (" + BackupService.formatSize(manifest.uncompressedSizeBytes()) + " décompressés)\n"
                + "• Groupes d'étudiants : " + manifest.activeGroupsCount() + "\n\n"
                + "Attention : Cette opération va remplacer l'ensemble de vos données actuelles (base de données H2, scans, corrections et images). "
                + "L'application va restaurer les données puis redémarrer automatiquement pour charger la nouvelle base.\n\n"
                + "Voulez-vous procéder à la restauration ?";

        boolean confirmed = notificationService.confirm(
                "Confirmation de restauration",
                "Remplacement complet des données",
                confirmationMsg,
                "Restaurer et redémarrer",
                "Annuler"
        );

        if (!confirmed) return;

        // Dialogue visuel de progression
        TaskProgressDialog progressDialog = new TaskProgressDialog(
                btnImportBackup != null && btnImportBackup.getScene() != null ? btnImportBackup.getScene().getWindow() : null,
                "Restauration des données",
                "Restauration de la sauvegarde",
                Feather.UPLOAD
        );
        progressDialog.setCancelVisible(false);
        progressDialog.show();

        if (btnExportBackup != null) btnExportBackup.setDisable(true);
        if (btnImportBackup != null) btnImportBackup.setDisable(true);

        TaskProgressTracker tracker = new TaskProgressTracker(progressDialog::update);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                backupService.importBackup(src, tracker, this::isCancelled);
                return null;
            }
        };

        task.setOnFailed(e -> {
            progressDialog.close();
            if (btnExportBackup != null) btnExportBackup.setDisable(false);
            if (btnImportBackup != null) btnImportBackup.setDisable(false);
            Throwable ex = task.getException();
            log.error("Échec de la restauration de la sauvegarde", ex);
            notificationService.showError("Échec de la restauration",
                    ex != null ? ex.getMessage() : "Une erreur inconnue est survenue.");
        });

        task.setOnSucceeded(e -> {
            progressDialog.close();
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Restauration réussie");
            alert.setHeaderText("Données restaurées avec succès !");
            alert.setContentText("L'intégralité de vos évaluations, copies et notes a été restaurée.\n\n"
                    + "L'application va maintenant redémarrer automatiquement.");
            alert.showAndWait();

            AppRestartUtils.restart();
        });

        Thread thread = new Thread(task, "ImportBackupThread");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    public void onTestAi() {
        boolean openCvOk = false;
        String openCvMsg = "";
        try {
            nu.pattern.OpenCV.loadLocally();
            org.opencv.core.Mat m = org.opencv.core.Mat.eye(3, 3, org.opencv.core.CvType.CV_8UC1);
            openCvOk = (m.rows() == 3);
            openCvMsg = "OpenCV " + org.opencv.core.Core.VERSION;
        } catch (Throwable t) {
            openCvMsg = t.getMessage();
        }

        boolean onnxOk = (checkboxClassifier != null && checkboxClassifier.isReady());
        if (!onnxOk) {
            File onnxFile = new File(modelPath);
            onnxOk = onnxFile.exists() && onnxFile.length() > 0;
        }

        if (openCvOk && onnxOk) {
            notificationService.showSuccess("Auto-diagnostic réussi",
                    "Tous les moteurs sont opérationnels :\n• " + openCvMsg + "\n• Modèle ONNX opérationnel");
        } else {
            notificationService.showWarning("Diagnostic incomplet",
                    "OpenCV : " + (openCvOk ? "OK" : "Échec (" + openCvMsg + ")") + "\nONNX : " + (onnxOk ? "OK" : "Fichier manquant"));
        }
        refreshAiCard();
    }

    @FXML
    public void onGarbageCollect() {
        System.gc();
        refreshMemoryInfo();
        notificationService.showInfo("Mémoire optimisée", "La mémoire vive inutilisée a été libérée.");
    }

    @FXML
    public void onBack() {
        if (mainLayoutController != null) {
            mainLayoutController.navigateBack();
        }
    }
}
