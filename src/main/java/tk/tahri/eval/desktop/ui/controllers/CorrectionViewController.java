package tk.tahri.eval.desktop.ui.controllers;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import tk.tahri.eval.desktop.document.dto.StudentGrade;
import tk.tahri.eval.desktop.document.services.DocumentProcessingService;
import tk.tahri.eval.desktop.document.services.DocumentProcessingService.ProcessingResult;
import tk.tahri.eval.desktop.document.services.DocumentService;
import tk.tahri.eval.desktop.document.services.GradeService;
import tk.tahri.eval.desktop.model.Evaluation;
import tk.tahri.eval.desktop.model.EvaluationState;
import tk.tahri.eval.desktop.model.Group;
import tk.tahri.eval.desktop.service.EvaluationService;
import tk.tahri.eval.desktop.service.GroupService;
import tk.tahri.eval.desktop.ui.NotificationService;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

@Component
public class CorrectionViewController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(CorrectionViewController.class);

    // 1. Vue Sélection de l'évaluation
    @FXML private VBox evalSelectionView;
    @FXML private Label lblEvalCount;
    @FXML private Button btnOpenPdfFromList;
    @FXML private TableView<Evaluation> evaluationsTable;
    @FXML private TableColumn<Evaluation, String> colEvalDesignation;
    @FXML private TableColumn<Evaluation, String> colEvalType;
    @FXML private TableColumn<Evaluation, String> colEvalDate;
    @FXML private TableColumn<Evaluation, String> colEvalQuestions;
    @FXML private TableColumn<Evaluation, String> colEvalState;
    @FXML private TableColumn<Evaluation, Void> colEvalAction;

    // 2. Vue Tableau de bord de l'évaluation
    @FXML private VBox dashboardView;
    @FXML private Button btnBackToEvalsList;
    @FXML private Label lblActiveEvalTitle;
    @FXML private Label lblActiveEvalState;
    @FXML private Button btnOpenPdfFromDashboard;

    @FXML private VBox dropZone;
    @FXML private Label lblDropZone;
    @FXML private Button btnBrowseFile;
    @FXML private Button btnStartCorrection;

    @FXML private ProgressBar progressBar;
    @FXML private Label lblProgressStatus;

    @FXML private Label lblAverage;
    @FXML private Label lblMin;
    @FXML private Label lblMax;
    @FXML private Label lblTotalCopies;
    @FXML private Label lblSuccessRate;
    @FXML private Label lblMedianStd;
    @FXML private Label lblDist0to5;
    @FXML private Label lblDist5to10;
    @FXML private Label lblDist10to15;
    @FXML private Label lblDist15to20;
    @FXML private ProgressBar progressSuccessRate;
    @FXML private TextField txtSearchGrade;
    @FXML private Button btnFilterAll;
    @FXML private Button btnFilterPassed;
    @FXML private Button btnFilterFailed;

    @FXML private TableView<StudentGrade> gradesTable;
    @FXML private TableColumn<StudentGrade, String> colMatricule;
    @FXML private TableColumn<StudentGrade, String> colNom;
    @FXML private TableColumn<StudentGrade, String> colPrenom;
    @FXML private TableColumn<StudentGrade, String> colQuestions;
    @FXML private TableColumn<StudentGrade, Double> colScore;
    @FXML private TableColumn<StudentGrade, Void> colAction;

    @FXML private Button btnViewStudentCopy;
    @FXML private Button btnExportExcel;
    @FXML private Button btnExportPdfReport;
    @FXML private Button btnViewAnnotatedPdf;

    private final GroupService groupService;
    private final EvaluationService evaluationService;
    private final DocumentProcessingService documentProcessingService;
    private final DocumentService documentService;
    private final GradeService gradeService;
    private final NotificationService notificationService;
    private final MainLayoutController mainLayoutController;

    private enum GradeFilter { ALL, PASSED, FAILED }
    private GradeFilter currentFilter = GradeFilter.ALL;
    private FilteredList<StudentGrade> filteredGradesList;
    private SortedList<StudentGrade> sortedGradesList;

    private Group currentGroup;
    private Evaluation currentEvaluation;
    private File selectedScanFile;
    private final ObservableList<Evaluation> printedEvaluationsList = FXCollections.observableArrayList();
    private final ObservableList<StudentGrade> studentGradesList = FXCollections.observableArrayList();

    public CorrectionViewController(GroupService groupService,
                                    EvaluationService evaluationService,
                                    DocumentProcessingService documentProcessingService,
                                    DocumentService documentService,
                                    GradeService gradeService,
                                    NotificationService notificationService,
                                    @Lazy MainLayoutController mainLayoutController) {
        this.groupService = groupService;
        this.evaluationService = evaluationService;
        this.documentProcessingService = documentProcessingService;
        this.documentService = documentService;
        this.gradeService = gradeService;
        this.notificationService = notificationService;
        this.mainLayoutController = mainLayoutController;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupEvaluationsTable();
        setupTableColumns();
        setupIcons();
        setupDropZone();
        setupListeners();
        showSelectionView();
    }

    private void setupEvaluationsTable() {
        colEvalDesignation.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDesignation()));
        colEvalDesignation.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #181c20;");
                }
            }
        });

        colEvalType.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getType() != null ? c.getValue().getType() : Evaluation.TYPE_CONTROLE_CONTINU
        ));
        colEvalType.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item);
                    badge.getStyleClass().add("tag-badge");
                    if (Evaluation.TYPE_FIN_MODULE.equals(item)) {
                        badge.setStyle("-fx-background-color: #fce8e6; -fx-text-fill: #c5221f;");
                    } else {
                        badge.setStyle("-fx-background-color: #e8f0fe; -fx-text-fill: #1a73e8;");
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        colEvalDate.setCellValueFactory(c -> {
            if (c.getValue() != null && c.getValue().getCreatedAt() != null) {
                return new SimpleStringProperty(
                        c.getValue().getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                );
            }
            return new SimpleStringProperty("-");
        });

        colEvalQuestions.setCellValueFactory(c -> {
            int qCount = (c.getValue() != null && c.getValue().getQuestions() != null) ? c.getValue().getQuestions().size() : 0;
            return new SimpleStringProperty(qCount + " question" + (qCount > 1 ? "s" : ""));
        });

        colEvalState.setCellValueFactory(c -> {
            if (c.getValue() != null && c.getValue().getState() != null) {
                return new SimpleStringProperty(c.getValue().getState().name());
            }
            return new SimpleStringProperty("PRINTED");
        });
        colEvalState.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label();
                    badge.getStyleClass().add("tag-badge");
                    switch (item) {
                        case "PRINTED" -> {
                            badge.setText("Prêt pour impression");
                            badge.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #92400e;");
                        }
                        case "ARCHIVED" -> {
                            badge.setText("Copies scannées / Noté");
                            badge.setStyle("-fx-background-color: #d1fae5; -fx-text-fill: #065f46;");
                        }
                        default -> {
                            badge.setText(item);
                            badge.setStyle("-fx-background-color: #e2e8f0; -fx-text-fill: #334155;");
                        }
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        colEvalAction.setCellFactory(col -> new TableCell<>() {
            private final Button btnPdf = new Button();
            private final Button btnOpen = new Button("Ouvrir");
            private final HBox pane = new HBox(6, btnPdf, btnOpen);

            {
                pane.setAlignment(Pos.CENTER);

                btnPdf.getStyleClass().add("card-action-btn");
                btnPdf.setGraphic(new FontIcon(Feather.FILE_TEXT));
                btnPdf.setTooltip(new Tooltip("Voir le PDF généré"));
                btnPdf.setOnAction(e -> {
                    e.consume();
                    Evaluation eval = getTableView().getItems().get(getIndex());
                    if (eval != null) {
                        openPdfForEvaluation(eval);
                    }
                });

                btnOpen.getStyleClass().add("card-action-btn");
                btnOpen.setStyle("-fx-text-fill: #004faa; -fx-font-weight: bold;");
                btnOpen.setGraphic(new FontIcon(Feather.ARROW_RIGHT));
                btnOpen.setOnAction(e -> {
                    e.consume();
                    Evaluation eval = getTableView().getItems().get(getIndex());
                    if (eval != null) {
                        openEvaluationDashboard(eval);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(pane);
                }
            }
        });

        // Un clic sur une évaluation dans le tableau ouvre directement son tableau de bord
        evaluationsTable.setRowFactory(tv -> {
            TableRow<Evaluation> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty()) {
                    openEvaluationDashboard(row.getItem());
                }
            });
            return row;
        });

        evaluationsTable.setItems(printedEvaluationsList);
    }

    private void setupTableColumns() {
        colMatricule.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().matriculation()));
        colNom.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().lastName()));
        colPrenom.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().firstName()));
        colQuestions.setCellValueFactory(c -> {
            StudentGrade g = c.getValue();
            return new SimpleStringProperty(g.correctedQuestions() + " / " + g.totalQuestions());
        });
        colScore.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().score()));
        colScore.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(empty ? "" : "-");
                    setStyle("");
                } else {
                    setText(String.format("%.2f", item));
                    if (item >= 10.0) {
                        setStyle("-fx-text-fill: #1b8744; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #ba1a1a; -fx-font-weight: bold;");
                    }
                }
            }
        });

        colAction.setCellFactory(col -> new TableCell<>() {
            private final Button btnCopy = new Button("Copie");

            {
                btnCopy.getStyleClass().add("card-action-btn");
                btnCopy.setStyle("-fx-text-fill: #004faa; -fx-font-size: 11px;");
                btnCopy.setGraphic(new FontIcon(Feather.FILE));
                btnCopy.setOnAction(e -> {
                    StudentGrade grade = getTableView().getItems().get(getIndex());
                    if (grade != null) {
                        viewIndividualCopy(grade);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    StudentGrade grade = getTableView().getItems().get(getIndex());
                    btnCopy.setDisable(grade.correctedPages() == 0);
                    setGraphic(btnCopy);
                }
            }
        });

        gradesTable.setRowFactory(tv -> {
            TableRow<StudentGrade> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    StudentGrade rowData = row.getItem();
                    if (rowData != null && rowData.correctedPages() > 0) {
                        viewIndividualCopy(rowData);
                    }
                }
            });
            return row;
        });

        filteredGradesList = new FilteredList<>(studentGradesList, p -> true);
        sortedGradesList = new SortedList<>(filteredGradesList);
        sortedGradesList.comparatorProperty().bind(gradesTable.comparatorProperty());
        gradesTable.setItems(sortedGradesList);
    }

    private void setupIcons() {
        if (btnBackToEvalsList != null) btnBackToEvalsList.setGraphic(new FontIcon(Feather.ARROW_LEFT));
        if (btnOpenPdfFromList != null) btnOpenPdfFromList.setGraphic(new FontIcon(Feather.FILE_TEXT));
        if (btnOpenPdfFromDashboard != null) btnOpenPdfFromDashboard.setGraphic(new FontIcon(Feather.FILE_TEXT));
        if (btnBrowseFile != null) btnBrowseFile.setGraphic(new FontIcon(Feather.FOLDER));
        if (btnStartCorrection != null) btnStartCorrection.setGraphic(new FontIcon(Feather.PLAY));
        if (btnViewStudentCopy != null) btnViewStudentCopy.setGraphic(new FontIcon(Feather.EYE));
        if (btnExportExcel != null) btnExportExcel.setGraphic(new FontIcon(Feather.FILE_TEXT));
        if (btnExportPdfReport != null) btnExportPdfReport.setGraphic(new FontIcon(Feather.PRINTER));
        if (btnViewAnnotatedPdf != null) btnViewAnnotatedPdf.setGraphic(new FontIcon(Feather.CHECK_SQUARE));
        if (btnFilterAll != null) btnFilterAll.setGraphic(new FontIcon(Feather.LIST));
        if (btnFilterPassed != null) btnFilterPassed.setGraphic(new FontIcon(Feather.CHECK_CIRCLE));
        if (btnFilterFailed != null) btnFilterFailed.setGraphic(new FontIcon(Feather.ALERT_CIRCLE));
    }

    private void setupDropZone() {
        dropZone.setOnDragOver(event -> {
            if (event.getGestureSource() != dropZone && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        });

        dropZone.setOnDragEntered(event -> {
            if (event.getGestureSource() != dropZone && event.getDragboard().hasFiles()) {
                if (!dropZone.getStyleClass().contains("drag-active")) {
                    dropZone.getStyleClass().add("drag-active");
                }
            }
            event.consume();
        });

        dropZone.setOnDragExited(event -> {
            dropZone.getStyleClass().remove("drag-active");
            event.consume();
        });

        dropZone.setOnDragDropped(event -> {
            dropZone.getStyleClass().remove("drag-active");
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles() && !db.getFiles().isEmpty()) {
                File file = db.getFiles().get(0);
                String name = file.getName().toLowerCase();
                if (name.endsWith(".pdf") || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                    setScanFile(file);
                    success = true;
                } else {
                    notificationService.showWarning("Format non supporté", "Veuillez glisser un fichier PDF ou une image (PNG/JPG).");
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void setupListeners() {
        evaluationsTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (btnOpenPdfFromList != null) {
                btnOpenPdfFromList.setDisable(n == null);
            }
        });

        gradesTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (btnViewStudentCopy != null) {
                btnViewStudentCopy.setDisable(n == null || n.correctedPages() == 0);
            }
            if (n != null && currentGroup != null && mainLayoutController != null) {
                mainLayoutController.setStatus("Étudiant sélectionné : " + n.fullName() + " • Note : " + (n.score() != null ? String.format("%.2f", n.score()) : "-"));
            }
        });

        if (txtSearchGrade != null) {
            txtSearchGrade.textProperty().addListener((obs, oldVal, newVal) -> updateFilterPredicate());
        }
        updateFilterButtonStyles();
    }

    @FXML
    public void onFilterAll() {
        currentFilter = GradeFilter.ALL;
        updateFilterPredicate();
        updateFilterButtonStyles();
    }

    @FXML
    public void onFilterPassed() {
        currentFilter = GradeFilter.PASSED;
        updateFilterPredicate();
        updateFilterButtonStyles();
    }

    @FXML
    public void onFilterFailed() {
        currentFilter = GradeFilter.FAILED;
        updateFilterPredicate();
        updateFilterButtonStyles();
    }

    private void updateFilterButtonStyles() {
        setFilterButtonActive(btnFilterAll, currentFilter == GradeFilter.ALL);
        setFilterButtonActive(btnFilterPassed, currentFilter == GradeFilter.PASSED);
        setFilterButtonActive(btnFilterFailed, currentFilter == GradeFilter.FAILED);
    }

    private void setFilterButtonActive(Button btn, boolean active) {
        if (btn == null) return;
        if (active) {
            if (!btn.getStyleClass().contains("active-filter")) {
                btn.getStyleClass().add("active-filter");
            }
        } else {
            btn.getStyleClass().remove("active-filter");
        }
    }

    private void updateFilterPredicate() {
        if (filteredGradesList == null) return;
        String search = (txtSearchGrade != null && txtSearchGrade.getText() != null)
                ? txtSearchGrade.getText().trim().toLowerCase() : "";

        filteredGradesList.setPredicate(grade -> {
            if (grade == null) return false;

            // Filtre par statut (Admis / Ajourné)
            if (currentFilter == GradeFilter.PASSED) {
                if (grade.score() == null || grade.score() < 10.0) return false;
            } else if (currentFilter == GradeFilter.FAILED) {
                if (grade.score() == null || grade.score() >= 10.0) return false;
            }

            // Filtre par texte de recherche
            if (!search.isEmpty()) {
                String mat = grade.matriculation() != null ? grade.matriculation().toLowerCase() : "";
                String nom = grade.lastName() != null ? grade.lastName().toLowerCase() : "";
                String prenom = grade.firstName() != null ? grade.firstName().toLowerCase() : "";
                String full = grade.fullName().toLowerCase();
                return mat.contains(search) || nom.contains(search) || prenom.contains(search) || full.contains(search);
            }

            return true;
        });
    }

    public void openEvaluationDashboard(Evaluation eval) {
        if (eval == null) return;
        this.currentEvaluation = eval;
        showDashboardView();

        if (lblActiveEvalTitle != null) {
            lblActiveEvalTitle.setText(eval.getDesignation());
        }
        updateActiveEvalStateBadge(eval.getState());

        loadDashboard(eval.getId());
        if (mainLayoutController != null) {
            mainLayoutController.setActiveEvaluation(eval);
        }
    }

    @FXML
    public void onBackToEvaluationsList() {
        this.currentEvaluation = null;
        if (lblActiveEvalTitle != null) {
            lblActiveEvalTitle.setText("Tableau de bord de l'évaluation");
        }
        updateActiveEvalStateBadge(null);
        if (mainLayoutController != null) {
            mainLayoutController.clearActiveEvaluation();
        }
        clearDashboard();
        showSelectionView();
    }

    public void showSelectionView() {
        if (dashboardView != null) {
            dashboardView.setVisible(false);
            dashboardView.setManaged(false);
        }
        if (evalSelectionView != null) {
            evalSelectionView.setVisible(true);
            evalSelectionView.setManaged(true);
        }
        if (mainLayoutController != null) {
            mainLayoutController.clearActiveEvaluation();
        }
        if (currentGroup != null) {
            loadEvaluationsForGroup(currentGroup.getId());
        }
    }

    public void showDashboardView() {
        if (evalSelectionView != null) {
            evalSelectionView.setVisible(false);
            evalSelectionView.setManaged(false);
        }
        if (dashboardView != null) {
            dashboardView.setVisible(true);
            dashboardView.setManaged(true);
        }
    }

    private void updateActiveEvalStateBadge(EvaluationState state) {
        if (lblActiveEvalState == null) return;
        if (state == null) {
            lblActiveEvalState.setText("");
            lblActiveEvalState.setStyle("");
            return;
        }
        switch (state) {
            case PRINTED -> {
                lblActiveEvalState.setText("Prêt pour impression");
                lblActiveEvalState.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #92400e;");
            }
            case ARCHIVED -> {
                lblActiveEvalState.setText("Copies scannées / Noté");
                lblActiveEvalState.setStyle("-fx-background-color: #d1fae5; -fx-text-fill: #065f46;");
            }
            default -> {
                lblActiveEvalState.setText(state.name());
                lblActiveEvalState.setStyle("-fx-background-color: #e2e8f0; -fx-text-fill: #334155;");
            }
        }
    }

    public Evaluation getSelectedEvaluation() {
        return currentEvaluation;
    }

    public void setGroup(Group group) {
        this.currentGroup = group;
        if (btnOpenPdfFromList != null) {
            btnOpenPdfFromList.setDisable(true);
        }
        if (group != null) {
            loadEvaluationsForGroup(group.getId());
        } else {
            printedEvaluationsList.clear();
            this.currentEvaluation = null;
            clearDashboard();
            if (lblEvalCount != null) {
                lblEvalCount.setText("0 épreuve(s) prête(s)");
            }
        }
        showSelectionView();
    }

    public void setCourse(Group course) { setGroup(course); }

    public Group getCurrentGroup() {
        return currentGroup;
    }

    public Group getCurrentCourse() { return getCurrentGroup(); }

    public void selectGroup(String groupId) {
        if (groupId == null) return;
        Group g = groupService.getGroup(groupId);
        if (g != null) {
            setGroup(g);
        }
    }

    public void selectCourse(String courseId) { selectGroup(courseId); }

    public void loadEvaluationsForGroup(String groupId) {
        if (btnOpenPdfFromList != null) {
            btnOpenPdfFromList.setDisable(true);
        }
        if (groupId == null) {
            printedEvaluationsList.clear();
            this.currentEvaluation = null;
            clearDashboard();
            if (lblEvalCount != null) {
                lblEvalCount.setText("0 épreuve(s) prête(s)");
            }
            return;
        }
        var evals = evaluationService.getEvaluationsByGroup(groupId);
        var readyEvals = evals.stream()
                .filter(e -> e.getState() == EvaluationState.PRINTED || e.getState() == EvaluationState.ARCHIVED)
                .toList();

        printedEvaluationsList.setAll(readyEvals);
        if (lblEvalCount != null) {
            lblEvalCount.setText(readyEvals.size() + " épreuve(s) prête(s)");
        }
    }

    public void loadEvaluationsForCourse(String courseId) { loadEvaluationsForGroup(courseId); }

    private void setScanFile(File file) {
        this.selectedScanFile = file;
        lblDropZone.setText("Fichier sélectionné : " + file.getName() + " (" + (file.length() / 1024) + " Ko)");
        btnStartCorrection.setDisable(false);
    }

    @FXML
    public void onBrowseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Sélectionner les scans des copies d'examen");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fichiers PDF ou Images", "*.pdf", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(dropZone.getScene().getWindow());
        if (file != null) {
            setScanFile(file);
        }
    }

    @FXML
    public void onStartCorrection() {
        Evaluation eval = currentEvaluation;
        if (eval == null) {
            notificationService.showWarning("Évaluation requise", "Veuillez sélectionner une évaluation.");
            return;
        }
        if (selectedScanFile == null || !selectedScanFile.exists()) {
            notificationService.showWarning("Fichier requis", "Veuillez choisir un fichier de scans.");
            return;
        }
        if (currentGroup == null) {
            notificationService.showWarning("Groupe requis", "Veuillez d'abord sélectionner un groupe.");
            return;
        }

        btnStartCorrection.setDisable(true);
        progressBar.setVisible(true);
        lblProgressStatus.setVisible(true);
        lblProgressStatus.setText("Initialisation de la reconnaissance optique...");

        Task<ProcessingResult> task = new Task<>() {
            @Override
            protected ProcessingResult call() throws Exception {
                return documentProcessingService.processScanFile(
                        currentGroup.getId(),
                        eval.getId(),
                        selectedScanFile,
                        msg -> Platform.runLater(() -> lblProgressStatus.setText(msg)),
                        progress -> Platform.runLater(() -> progressBar.setProgress(progress))
                );
            }
        };

        task.setOnSucceeded(e -> {
            btnStartCorrection.setDisable(false);
            progressBar.setVisible(false);
            lblProgressStatus.setVisible(false);
            ProcessingResult res = task.getValue();

            loadDashboard(eval.getId());
            Evaluation reloaded = evaluationService.getEvaluation(eval.getId());
            if (reloaded != null) {
                updateActiveEvalStateBadge(reloaded.getState());
            }

            notificationService.showInfo("Correction terminée",
                    "Scan traité avec succès !\n" +
                    "Pages analysées : " + res.totalPages() + "\n" +
                    "Pages corrigées : " + res.correctedPages() + "\n" +
                    "Pages non reconnues : " + res.uncorrectedPages());
        });

        task.setOnFailed(e -> {
            btnStartCorrection.setDisable(false);
            progressBar.setVisible(false);
            lblProgressStatus.setVisible(false);
            Throwable ex = task.getException();
            log.error("Échec de la correction optique", ex);
            notificationService.showError("Erreur d'analyse", "Impossible de traiter le fichier : " + ex.getMessage());
        });

        Thread.startVirtualThread(task);
    }

    private void loadDashboard(String evaluationId) {
        if (currentGroup == null) return;

        Platform.runLater(() -> {
            try {
                var resp = gradeService.getGrades(currentGroup.getId(), evaluationId);
                studentGradesList.setAll(resp.students());

                var stats = resp.statistics();
                int totalStudents = resp.students().size();
                lblTotalCopies.setText(String.valueOf(totalStudents));

                long countPassed = resp.students().stream()
                        .filter(s -> s.score() != null && s.score() >= 10.0)
                        .count();
                long countScored = resp.students().stream()
                        .filter(s -> s.score() != null)
                        .count();

                if (countScored > 0) {
                    double rate = (countPassed * 100.0) / countScored;
                    if (lblSuccessRate != null) {
                        lblSuccessRate.setText(String.format("%.1f %%", rate));
                        if (rate >= 50.0) {
                            lblSuccessRate.setStyle("-fx-text-fill: -color-success-emphasis; -fx-font-weight: 800;");
                        } else {
                            lblSuccessRate.setStyle("-fx-text-fill: -color-danger-emphasis; -fx-font-weight: 800;");
                        }
                    }
                    if (progressSuccessRate != null) {
                        progressSuccessRate.setProgress(rate / 100.0);
                    }
                } else {
                    if (lblSuccessRate != null) {
                        lblSuccessRate.setText("-");
                        lblSuccessRate.setStyle("");
                    }
                    if (progressSuccessRate != null) {
                        progressSuccessRate.setProgress(0.0);
                    }
                }

                lblAverage.setText(stats.average() != null ? String.format("%.2f / 20", stats.average()) : "-");
                lblMin.setText(stats.min() != null ? String.format("%.2f", stats.min()) : "-");
                lblMax.setText(stats.max() != null ? String.format("%.2f", stats.max()) : "-");

                if (lblMedianStd != null) {
                    if (stats.median() != null && stats.stdDeviation() != null) {
                        lblMedianStd.setText(String.format("%.2f | %.2f", stats.median(), stats.stdDeviation()));
                    } else if (stats.median() != null) {
                        lblMedianStd.setText(String.format("%.2f", stats.median()));
                    } else {
                        lblMedianStd.setText("-");
                    }
                }

                // Répartition des notes par tranches
                long d0to5 = resp.students().stream().filter(s -> s.score() != null && s.score() < 5.0).count();
                long d5to10 = resp.students().stream().filter(s -> s.score() != null && s.score() >= 5.0 && s.score() < 10.0).count();
                long d10to15 = resp.students().stream().filter(s -> s.score() != null && s.score() >= 10.0 && s.score() < 15.0).count();
                long d15to20 = resp.students().stream().filter(s -> s.score() != null && s.score() >= 15.0).count();

                if (lblDist0to5 != null) lblDist0to5.setText(String.valueOf(d0to5));
                if (lblDist5to10 != null) lblDist5to10.setText(String.valueOf(d5to10));
                if (lblDist10to15 != null) lblDist10to15.setText(String.valueOf(d10to15));
                if (lblDist15to20 != null) lblDist15to20.setText(String.valueOf(d15to20));

                boolean hasGrades = !resp.students().isEmpty();
                if (btnExportExcel != null) btnExportExcel.setDisable(!hasGrades);
                if (btnExportPdfReport != null) btnExportPdfReport.setDisable(!hasGrades);
                if (btnViewAnnotatedPdf != null) btnViewAnnotatedPdf.setDisable(!hasGrades);

                updateFilterPredicate();

                evaluationService.getEvaluation(evaluationId);
            } catch (Exception e) {
                log.error("Erreur lors du chargement des statistiques", e);
                clearDashboard();
            }
        });
    }

    private void clearDashboard() {
        studentGradesList.clear();
        lblTotalCopies.setText("0");
        lblAverage.setText("-");
        lblMin.setText("-");
        lblMax.setText("-");
        if (lblSuccessRate != null) {
            lblSuccessRate.setText("-");
            lblSuccessRate.setStyle("");
        }
        if (lblMedianStd != null) lblMedianStd.setText("-");
        if (lblDist0to5 != null) lblDist0to5.setText("0");
        if (lblDist5to10 != null) lblDist5to10.setText("0");
        if (lblDist10to15 != null) lblDist10to15.setText("0");
        if (lblDist15to20 != null) lblDist15to20.setText("0");
        if (progressSuccessRate != null) progressSuccessRate.setProgress(0.0);
        if (txtSearchGrade != null) txtSearchGrade.clear();
        currentFilter = GradeFilter.ALL;
        updateFilterButtonStyles();
        if (btnExportExcel != null) btnExportExcel.setDisable(true);
        if (btnExportPdfReport != null) btnExportPdfReport.setDisable(true);
        if (btnViewAnnotatedPdf != null) btnViewAnnotatedPdf.setDisable(true);
        if (btnViewStudentCopy != null) btnViewStudentCopy.setDisable(true);
    }

    @FXML
    public void onViewStudentCopy() {
        StudentGrade sel = gradesTable.getSelectionModel().getSelectedItem();
        if (sel != null) {
            viewIndividualCopy(sel);
        }
    }

    @FXML
    public void onViewSelectedStudentCopy() {
        onViewStudentCopy();
    }

    private void viewIndividualCopy(StudentGrade grade) {
        Evaluation eval = currentEvaluation;
        if (eval == null || grade == null || grade.assessmentId() == null) return;
        if (currentGroup == null) return;

        Platform.runLater(() -> {
            try {
                mainLayoutController.setStatus("Génération du visualiseur pour : " + grade.fullName());
                byte[] pdfBytes = documentService.generatePDFPaperAssessment(currentGroup.getId(), eval.getId(), grade.assessmentId());

                File tempPdf = File.createTempFile("copie_" + (grade.matriculation() != null ? grade.matriculation() : "etu") + "_", ".pdf");
                tempPdf.deleteOnExit();
                try (FileOutputStream fos = new FileOutputStream(tempPdf)) {
                    fos.write(pdfBytes);
                }

                String title = "Copie annotée : " + grade.fullName() + " (" + (grade.score() != null ? String.format("%.2f", grade.score()) : "-") + ")";
                mainLayoutController.openPdfViewer(tempPdf, title, () -> {
                    mainLayoutController.showCorrectionForEvaluation(currentGroup.getId(), eval.getId());
                });
            } catch (Exception e) {
                log.error("Échec de la consultation de la copie", e);
                notificationService.showError("Erreur", "Impossible de charger la copie corrigée : " + e.getMessage());
            }
        });
    }

    @FXML
    public void onExportExcel() {
        Evaluation eval = currentEvaluation;
        if (eval == null) return;
        if (currentGroup == null) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Enregistrer le relevé de notes");
        chooser.setInitialFileName("Notes_" + eval.getDesignation().replaceAll("[^a-zA-Z0-9_-]", "_") + ".xlsx");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fichier Excel (*.xlsx)", "*.xlsx"));

        File target = chooser.showSaveDialog(dropZone.getScene().getWindow());
        if (target != null) {
            try {
                documentService.exportGradesToExcel(currentGroup.getId(), eval.getId(), target);
                notificationService.showInfo("Export réussi", "Le fichier Excel a été généré avec succès :\n" + target.getName());
            } catch (Exception e) {
                log.error("Échec de l'export Excel", e);
                notificationService.showError("Erreur d'export", e.getMessage());
            }
        }
    }

    @FXML
    public void onExportPdfReport() {
        Evaluation eval = currentEvaluation;
        if (eval == null) return;
        if (currentGroup == null) return;

        Platform.runLater(() -> {
            try {
                mainLayoutController.setStatus("Génération du PV de Notes...");
                byte[] pdfBytes = documentService.generatePDFCorrectionReport(currentGroup.getId(), eval.getId());

                File tempPdf = File.createTempFile("pv_notes_" + eval.getId() + "_", ".pdf");
                tempPdf.deleteOnExit();
                try (FileOutputStream fos = new FileOutputStream(tempPdf)) {
                    fos.write(pdfBytes);
                }

                mainLayoutController.openPdfViewer(tempPdf, "PV de Notes : " + eval.getDesignation(), () -> {
                    mainLayoutController.showCorrectionForEvaluation(currentGroup.getId(), eval.getId());
                });
            } catch (Exception e) {
                log.error("Échec de l'export du PV de notes", e);
                notificationService.showError("Erreur", "Impossible de générer le PV de notes : " + e.getMessage());
            }
        });
    }

    @FXML
    public void onViewAnnotatedPdf() {
        Evaluation eval = currentEvaluation;
        if (eval == null) return;
        if (currentGroup == null) return;

        Platform.runLater(() -> {
            try {
                mainLayoutController.setStatus("Génération du livret complet des copies...");
                byte[] pdfBytes = documentService.generatePDFPaperAssessments(currentGroup.getId(), eval.getId());

                File tempPdf = File.createTempFile("copies_toutes_" + eval.getId() + "_", ".pdf");
                tempPdf.deleteOnExit();
                try (FileOutputStream fos = new FileOutputStream(tempPdf)) {
                    fos.write(pdfBytes);
                }

                String title = "Toutes les copies annotées : " + eval.getDesignation();
                mainLayoutController.openPdfViewer(tempPdf, title, () -> {
                    mainLayoutController.showCorrectionForEvaluation(currentGroup.getId(), eval.getId());
                });
            } catch (Exception e) {
                log.error("Échec de l'affichage des copies corrigées", e);
                Platform.runLater(() -> notificationService.showError("Erreur", "Aucune copie corrigée disponible pour l'instant."));
            }
        });
    }

    @FXML
    public void onOpenGeneratedPdf() {
        if (currentEvaluation != null) {
            openPdfForEvaluation(currentEvaluation);
        } else {
            notificationService.showWarning("Sélection requise", "Veuillez d'abord sélectionner une évaluation.");
        }
    }

    @FXML
    public void onOpenPdfFromList() {
        Evaluation selected = evaluationsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            openPdfForEvaluation(selected);
        } else {
            notificationService.showWarning("Sélection requise", "Veuillez sélectionner une épreuve dans la liste.");
        }
    }

    public void openPdfForEvaluation(Evaluation eval) {
        if (eval == null) return;
        File pdf = documentService.getGeneratedPdfFile(eval.getId());
        if (pdf != null && pdf.exists()) {
            String groupId = currentGroup != null ? currentGroup.getId() : eval.groupId();
            mainLayoutController.openPdfViewer(pdf, "Sujet : " + eval.getDesignation(), () -> {
                if (currentEvaluation != null && currentEvaluation.getId().equals(eval.getId())) {
                    mainLayoutController.showCorrectionForEvaluation(groupId, eval.getId());
                } else {
                    mainLayoutController.showCorrectionForEvaluation(groupId, null);
                }
            });
        } else {
            notificationService.showWarning("Fichier introuvable", "Le sujet PDF n'a pas été trouvé pour cette évaluation.");
        }
    }

    public void selectGroupAndEvaluation(String groupId, String evaluationId) {
        if (groupId != null) {
            Group g = groupService.getGroup(groupId);
            if (g != null) {
                this.currentGroup = g;
                loadEvaluationsForGroup(g.getId());
            }
        }
        if (evaluationId != null) {
            Evaluation target = null;
            for (Evaluation e : printedEvaluationsList) {
                if (e.getId().equals(evaluationId)) {
                    target = e;
                    break;
                }
            }
            if (target == null) {
                target = evaluationService.getEvaluation(evaluationId);
            }
            if (target != null) {
                openEvaluationDashboard(target);
            } else {
                showSelectionView();
            }
        } else {
            showSelectionView();
        }
    }

    public void selectCourseAndEvaluation(String courseId, String evaluationId) {
        selectGroupAndEvaluation(courseId, evaluationId);
    }
}
