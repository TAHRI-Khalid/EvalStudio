package tk.tahri.eval.desktop.ui.controllers;

import atlantafx.base.theme.Styles;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import tk.tahri.eval.desktop.document.dto.TaskProgress;
import tk.tahri.eval.desktop.document.services.DocumentService;
import tk.tahri.eval.desktop.model.Answer;
import tk.tahri.eval.desktop.model.Evaluation;
import tk.tahri.eval.desktop.model.EvaluationState;
import tk.tahri.eval.desktop.model.Question;
import tk.tahri.eval.desktop.service.EvaluationService;
import tk.tahri.eval.desktop.service.ImageStorageService;
import tk.tahri.eval.desktop.service.QuestionExcelService;
import tk.tahri.eval.desktop.ui.NotificationService;
import tk.tahri.eval.desktop.ui.util.DesktopFileViewer;
import tk.tahri.eval.desktop.ui.util.ImageUtils;
import tk.tahri.eval.desktop.ui.util.ImageUploadBox;
import tk.tahri.eval.desktop.ui.util.TaskProgressDialog;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class EvaluationBuilderController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(EvaluationBuilderController.class);

    @FXML private Label lblTitle;
    @FXML private Label lblSaveStatus;
    @FXML private Label lblTotalPoints;
    @FXML private Label lblQuestionsCount;
    @FXML private Label lblHeaderTotalPoints;
    @FXML private HBox lockedBanner;
    @FXML private TextField txtDesignation;
    @FXML private ComboBox<String> comboType;
    @FXML private Spinner<Integer> spinDuration;
    @FXML private ComboBox<Integer> comboColumns;
    @FXML private CheckBox chkShuffleQuestions;
    @FXML private CheckBox chkShuffleAnswers;
    @FXML private CheckBox chkDoubleSided;

    @FXML private TextField txtScoreCorrect;
    @FXML private TextField txtScoreEmpty;
    @FXML private TextField txtScoreIncorrect;

    @FXML private ListView<Question> questionsListView;
    @FXML private Button btnAddQuestion;
    @FXML private Button btnImportExcel;
    @FXML private Button btnExportExcel;
    @FXML private Button btnPreviewDraft;
    @FXML private Button btnGeneratePdf;
    @FXML private Button btnBack;

    @FXML private VBox progressBox;
    @FXML private ProgressBar progressBar;
    @FXML private Label lblProgress;

    private final EvaluationService evaluationService;
    private final DocumentService documentService;
    private final NotificationService notificationService;
    private final MainLayoutController mainLayoutController;
    private final ImageStorageService imageStorageService;
    private final QuestionExcelService questionExcelService;

    private Evaluation currentEvaluation;
    private final ObservableList<Question> questionsList = FXCollections.observableArrayList();
    private final javafx.animation.PauseTransition autoSaveDebounce = new javafx.animation.PauseTransition(javafx.util.Duration.millis(400));
    private boolean isUpdatingUi = false;

    public EvaluationBuilderController(EvaluationService evaluationService,
                                       DocumentService documentService,
                                       NotificationService notificationService,
                                       @Lazy MainLayoutController mainLayoutController,
                                       ImageStorageService imageStorageService,
                                       QuestionExcelService questionExcelService) {
        this.evaluationService = evaluationService;
        this.documentService = documentService;
        this.notificationService = notificationService;
        this.mainLayoutController = mainLayoutController;
        this.imageStorageService = imageStorageService;
        this.questionExcelService = questionExcelService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        autoSaveDebounce.setOnFinished(e -> saveEvaluationData());
        setupIcons();
        setupSpinners();
        setupListView();
        setupInputListeners();
    }

    private void setupIcons() {
        if (btnAddQuestion != null) btnAddQuestion.setGraphic(new FontIcon(Feather.PLUS));
        if (btnImportExcel != null) {
            btnImportExcel.setGraphic(new FontIcon(Feather.UPLOAD));
            btnImportExcel.setTooltip(new Tooltip("Importer des questions depuis un fichier Excel (.xlsx)"));
        }
        if (btnExportExcel != null) {
            btnExportExcel.setGraphic(new FontIcon(Feather.DOWNLOAD));
            btnExportExcel.setTooltip(new Tooltip("Exporter les questions vers un fichier Excel (.xlsx)"));
        }
        if (btnPreviewDraft != null) btnPreviewDraft.setGraphic(new FontIcon(Feather.EYE));
        if (btnGeneratePdf != null) btnGeneratePdf.setGraphic(new FontIcon(Feather.FILE_TEXT));
        if (btnBack != null) btnBack.setGraphic(new FontIcon(Feather.ARROW_LEFT));
    }

    private void setupSpinners() {
        if (comboType != null) {
            comboType.setItems(FXCollections.observableArrayList(Evaluation.TYPE_CONTROLE_CONTINU, Evaluation.TYPE_FIN_MODULE));
            comboType.getSelectionModel().selectFirst();
        }
        spinDuration.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(10, 360, 60, 5));
        comboColumns.setItems(FXCollections.observableArrayList(1, 2));
        comboColumns.getSelectionModel().selectFirst();
    }

    private void setupListView() {
        questionsListView.setItems(questionsList);
        questionsListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Question item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    int index = getIndex() + 1;
                    HBox cellContent = new HBox(12);
                    cellContent.setAlignment(Pos.TOP_LEFT);
                    cellContent.setStyle("-fx-padding: 4 2;");

                    VBox contentBox = new VBox(6);
                    HBox.setHgrow(contentBox, Priority.ALWAYS);

                    HBox titleRow = new HBox(8);
                    titleRow.setAlignment(Pos.CENTER_LEFT);

                    Label title = new Label(index + ". " + item.content());
                    title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #181c20;");
                    title.setWrapText(true);
                    HBox.setHgrow(title, Priority.ALWAYS);

                    Label ptsBadge = new Label(String.format("%.2f pt%s", item.scoreIfCorrect(), item.scoreIfCorrect() > 1 ? "s" : ""));
                    ptsBadge.getStyleClass().add("tag-badge");
                    ptsBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: -color-accent-emphasis;");

                    titleRow.getChildren().addAll(title, ptsBadge);
                    contentBox.getChildren().add(titleRow);

                    if (item.imagePath() != null && !item.imagePath().isBlank()) {
                        Path imgPath = imageStorageService.getImagePath(item.imagePath(), ImageStorageService.ImageFolder.QUESTION_IMAGES, false);
                        if (imgPath != null && Files.exists(imgPath)) {
                            try {
                                Image img = ImageUtils.loadImage(imgPath);
                                if (img != null && !img.isError() && img.getWidth() > 0) {
                                    ImageView iv = new ImageView(img);
                                    iv.setFitWidth(140);
                                    iv.setFitHeight(85);
                                    iv.setPreserveRatio(true);
                                    iv.setSmooth(true);
                                    Rectangle clip = new Rectangle(140, 85);
                                    clip.setArcWidth(8);
                                    clip.setArcHeight(8);
                                    iv.setClip(clip);
                                    HBox imgBox = new HBox(iv);
                                    imgBox.setPadding(new Insets(2, 0, 4, 16));
                                    contentBox.getChildren().add(imgBox);
                                }
                            } catch (Exception ex) {
                                log.warn("Erreur chargement vignette image question: {}", ex.getMessage());
                            }
                        }
                    }

                    VBox answersBox = new VBox(3);
                    answersBox.setPadding(new Insets(0, 0, 0, 16));
                    if (item.answers() != null) {
                        for (Answer a : item.answers()) {
                            Label aLbl = new Label((a.correct() ? "✔  " : "○  ") + a.content());
                            if (a.correct()) {
                                aLbl.setStyle("-fx-text-fill: -color-success-fg; -fx-font-weight: bold;");
                            } else {
                                aLbl.setStyle("-fx-text-fill: -color-fg-muted;");
                            }
                            answersBox.getChildren().add(aLbl);
                        }
                    }
                    contentBox.getChildren().add(answersBox);

                    boolean isLocked = currentEvaluation != null && currentEvaluation.getState() != EvaluationState.CONCEPTION;

                    VBox actionButtons = new VBox(3);
                    actionButtons.setAlignment(Pos.TOP_CENTER);

                    Button btnMoveUp = new Button();
                    btnMoveUp.getStyleClass().add("card-action-btn");
                    btnMoveUp.setGraphic(new FontIcon(Feather.CHEVRON_UP));
                    btnMoveUp.setTooltip(new Tooltip("Monter d'un rang"));
                    btnMoveUp.setDisable(isLocked || getIndex() == 0);
                    btnMoveUp.setVisible(!isLocked);
                    btnMoveUp.setManaged(!isLocked);
                    btnMoveUp.setOnAction(e -> {
                        e.consume();
                        int idx = getIndex();
                        if (idx > 0) {
                            Question q = questionsList.remove(idx);
                            questionsList.add(idx - 1, q);
                            questionsListView.getSelectionModel().select(idx - 1);
                            triggerInstantAutoSave();
                        }
                    });

                    Button btnMoveDown = new Button();
                    btnMoveDown.getStyleClass().add("card-action-btn");
                    btnMoveDown.setGraphic(new FontIcon(Feather.CHEVRON_DOWN));
                    btnMoveDown.setTooltip(new Tooltip("Descendre d'un rang"));
                    btnMoveDown.setDisable(isLocked || getIndex() >= questionsList.size() - 1);
                    btnMoveDown.setVisible(!isLocked);
                    btnMoveDown.setManaged(!isLocked);
                    btnMoveDown.setOnAction(e -> {
                        e.consume();
                        int idx = getIndex();
                        if (idx < questionsList.size() - 1) {
                            Question q = questionsList.remove(idx);
                            questionsList.add(idx + 1, q);
                            questionsListView.getSelectionModel().select(idx + 1);
                            triggerInstantAutoSave();
                        }
                    });

                    Button btnEdit = new Button();
                    btnEdit.getStyleClass().add("card-action-btn");
                    btnEdit.setGraphic(new FontIcon(Feather.EDIT_2));
                    btnEdit.setTooltip(new Tooltip("Modifier la question"));
                    btnEdit.setDisable(isLocked);
                    btnEdit.setVisible(!isLocked);
                    btnEdit.setManaged(!isLocked);
                    btnEdit.setOnAction(e -> {
                        e.consume();
                        if (currentEvaluation != null && currentEvaluation.getState() != EvaluationState.CONCEPTION) {
                            notificationService.showWarning("Modification impossible", "Une évaluation imprimée ou archivée ne peut pas être modifiée.");
                            return;
                        }
                        showQuestionDialog(item);
                    });

                    Button btnDelete = new Button();
                    btnDelete.getStyleClass().add("card-action-btn");
                    FontIcon trash = new FontIcon(Feather.TRASH_2);
                    trash.setIconColor(javafx.scene.paint.Color.web("#ba1a1a"));
                    btnDelete.setGraphic(trash);
                    btnDelete.setTooltip(new Tooltip("Supprimer la question"));
                    btnDelete.setDisable(isLocked);
                    btnDelete.setVisible(!isLocked);
                    btnDelete.setManaged(!isLocked);
                    btnDelete.setOnAction(e -> {
                        e.consume();
                        if (currentEvaluation != null && currentEvaluation.getState() != EvaluationState.CONCEPTION) {
                            notificationService.showWarning("Modification impossible", "Une évaluation imprimée ou archivée ne peut pas être modifiée.");
                            return;
                        }
                        String qText = item.content() != null ? item.content().trim() : "cette question";
                        if (qText.length() > 60) qText = qText.substring(0, 57) + "...";

                        boolean confirm = notificationService.confirm(
                                "Supprimer la question",
                                "Confirmation de suppression",
                                "Êtes-vous sûr de vouloir supprimer la question :\n« " + qText + " » ?",
                                "Supprimer la question",
                                "Annuler"
                        );
                        if (!confirm) {
                            return;
                        }

                        questionsList.remove(item);
                        triggerInstantAutoSave();
                    });

                    actionButtons.getChildren().addAll(btnMoveUp, btnMoveDown, btnEdit, btnDelete);

                    cellContent.getChildren().addAll(contentBox, actionButtons);
                    setGraphic(cellContent);
                }
            }
        });

        questionsListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                if (currentEvaluation != null && currentEvaluation.getState() != EvaluationState.CONCEPTION) {
                    return;
                }
                Question selected = questionsListView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    showQuestionDialog(selected);
                }
            }
        });

        questionsList.addListener((javafx.collections.ListChangeListener<Question>) c -> {
            recalculateTotalPoints();
        });
    }

    private Double tryParseDouble(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            double val = Double.parseDouble(text.trim().replace(',', '.'));
            if (Double.isNaN(val) || Double.isInfinite(val)) return null;
            return val;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isScoreCorrectValid(String text) {
        Double val = tryParseDouble(text);
        return val != null && val > 0;
    }

    private boolean isScoreIncorrectValid(String text) {
        Double val = tryParseDouble(text);
        return val != null && val <= 0;
    }

    private boolean isScoreEmptyValid(String text) {
        Double val = tryParseDouble(text);
        return val != null;
    }

    private boolean areAllScoreFieldsValid() {
        return isScoreCorrectValid(txtScoreCorrect != null ? txtScoreCorrect.getText() : null)
                && isScoreIncorrectValid(txtScoreIncorrect != null ? txtScoreIncorrect.getText() : null)
                && isScoreEmptyValid(txtScoreEmpty != null ? txtScoreEmpty.getText() : null);
    }

    private void updateFieldValidationVisuals(TextField field, boolean isValid, String errorMsg) {
        if (field == null) return;
        if (isValid) {
            field.getStyleClass().remove(Styles.DANGER);
            field.setTooltip(null);
        } else {
            if (!field.getStyleClass().contains(Styles.DANGER)) {
                field.getStyleClass().add(Styles.DANGER);
            }
            field.setTooltip(new Tooltip(errorMsg));
        }
    }

    private void setupScoreField(TextField field, java.util.function.Predicate<Double> validator, String errorMsg, java.util.function.DoubleSupplier fallbackSupplier) {
        if (field == null) return;

        field.textProperty().addListener((o, ov, nv) -> {
            if (isUpdatingUi) return;
            Double val = tryParseDouble(nv);
            boolean valid = val != null && validator.test(val);
            updateFieldValidationVisuals(field, valid, errorMsg);
            if (valid && areAllScoreFieldsValid()) {
                triggerDebouncedAutoSave();
            } else if (!valid && lblSaveStatus != null) {
                lblSaveStatus.setText("⚠ " + errorMsg);
                lblSaveStatus.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 11px;");
            }
        });

        field.focusedProperty().addListener((o, wasFocused, isFocused) -> {
            if (!isFocused && !isUpdatingUi) {
                Double val = tryParseDouble(field.getText());
                if (val == null || !validator.test(val)) {
                    notificationService.showWarning("Barème invalide", errorMsg);
                    double fallback = fallbackSupplier.getAsDouble();
                    isUpdatingUi = true;
                    field.setText(String.valueOf(fallback));
                    isUpdatingUi = false;
                    updateFieldValidationVisuals(field, true, null);
                }
                flushAutoSave();
            }
        });
    }

    private void setupInputListeners() {
        if (txtDesignation != null) {
            txtDesignation.textProperty().addListener((o, ov, nv) -> triggerDebouncedAutoSave());
            txtDesignation.focusedProperty().addListener((o, wasFocused, isFocused) -> {
                if (!isFocused && !isUpdatingUi) {
                    flushAutoSave();
                }
            });
        }

        setupScoreField(
                txtScoreCorrect,
                v -> v > 0,
                "La note pour une réponse correcte doit être strictement positive (> 0).",
                () -> currentEvaluation != null ? currentEvaluation.getDefaultScoreIfCorrect() : 1.0
        );

        setupScoreField(
                txtScoreIncorrect,
                v -> v <= 0,
                "La note pour une réponse fausse doit être 0 ou négative (≤ 0).",
                () -> currentEvaluation != null ? currentEvaluation.getDefaultScoreIfIncorrect() : 0.0
        );

        setupScoreField(
                txtScoreEmpty,
                v -> true,
                "La note pour « sans réponse » doit être un nombre valide (positif, négatif ou zéro).",
                () -> currentEvaluation != null ? currentEvaluation.getDefaultScoreIfEmpty() : 0.0
        );

        if (comboType != null) comboType.valueProperty().addListener((o, ov, nv) -> triggerInstantAutoSave());
        if (spinDuration != null) spinDuration.valueProperty().addListener((o, ov, nv) -> triggerInstantAutoSave());
        if (comboColumns != null) comboColumns.valueProperty().addListener((o, ov, nv) -> triggerInstantAutoSave());
        if (chkShuffleQuestions != null) chkShuffleQuestions.selectedProperty().addListener((o, ov, nv) -> triggerInstantAutoSave());
        if (chkShuffleAnswers != null) chkShuffleAnswers.selectedProperty().addListener((o, ov, nv) -> triggerInstantAutoSave());
        if (chkDoubleSided != null) chkDoubleSided.selectedProperty().addListener((o, ov, nv) -> triggerInstantAutoSave());
    }

    private void triggerDebouncedAutoSave() {
        if (isUpdatingUi || currentEvaluation == null) return;
        if (currentEvaluation.getState() != EvaluationState.CONCEPTION) return;
        if (lblSaveStatus != null) {
            lblSaveStatus.setText("Enregistrement en cours...");
            lblSaveStatus.setStyle("-fx-text-fill: #b45309; -fx-font-size: 11px;");
        }
        autoSaveDebounce.playFromStart();
    }

    private void triggerInstantAutoSave() {
        if (isUpdatingUi || currentEvaluation == null) return;
        if (currentEvaluation.getState() != EvaluationState.CONCEPTION) return;
        autoSaveDebounce.stop();
        saveEvaluationData();
    }

    public void flushAutoSave() {
        if (autoSaveDebounce.getStatus() == javafx.animation.Animation.Status.RUNNING) {
            autoSaveDebounce.stop();
            saveEvaluationData();
        }
    }

    private void recalculateTotalPoints() {
        double total = questionsList.stream().mapToDouble(Question::scoreIfCorrect).sum();
        String totalText = String.format("Total : %.2f pts", total);
        if (lblTotalPoints != null) lblTotalPoints.setText(totalText);
        if (lblHeaderTotalPoints != null) lblHeaderTotalPoints.setText(String.format("Barème total : %.2f pts", total));
        if (lblQuestionsCount != null) {
            lblQuestionsCount.setText("(" + questionsList.size() + " question" + (questionsList.size() > 1 ? "s" : "") + ")");
        }
    }

    public void loadEvaluation(String evaluationId) {
        this.currentEvaluation = evaluationService.getEvaluation(evaluationId);
        if (currentEvaluation != null) {
            mainLayoutController.setActiveEvaluation(currentEvaluation);
        }
        boolean isLocked = currentEvaluation != null && currentEvaluation.getState() != EvaluationState.CONCEPTION;
        if (isLocked) {
            lblTitle.setText("Concepteur d'Évaluation : " + currentEvaluation.getDesignation() + " [Lecture Seule]");
            if (lockedBanner != null) {
                lockedBanner.setVisible(true);
                lockedBanner.setManaged(true);
            }
        } else {
            lblTitle.setText("Concepteur d'Évaluation : " + currentEvaluation.getDesignation());
            if (lockedBanner != null) {
                lockedBanner.setVisible(false);
                lockedBanner.setManaged(false);
            }
        }

        isUpdatingUi = true;
        try {
            txtDesignation.setText(currentEvaluation.getDesignation());
            txtDesignation.setDisable(isLocked);
            if (comboType != null) {
                comboType.setValue(currentEvaluation.getType() != null ? currentEvaluation.getType() : Evaluation.TYPE_CONTROLE_CONTINU);
                comboType.setDisable(isLocked);
            }
            if (currentEvaluation.getDuration() != null) {
                spinDuration.getValueFactory().setValue((int) currentEvaluation.getDuration().toMinutes());
            }
            spinDuration.setDisable(isLocked);
            comboColumns.getSelectionModel().select(Integer.valueOf(currentEvaluation.getColumnCount()));
            comboColumns.setDisable(isLocked);
            chkShuffleQuestions.setSelected(currentEvaluation.isQuestionShuffle());
            chkShuffleQuestions.setDisable(isLocked);
            chkShuffleAnswers.setSelected(currentEvaluation.isAnswersShuffle());
            chkShuffleAnswers.setDisable(isLocked);
            chkDoubleSided.setSelected(currentEvaluation.isDoubleSided());
            chkDoubleSided.setDisable(isLocked);

            txtScoreCorrect.setText(String.valueOf(currentEvaluation.getDefaultScoreIfCorrect()));
            txtScoreCorrect.setDisable(isLocked);
            txtScoreEmpty.setText(String.valueOf(currentEvaluation.getDefaultScoreIfEmpty()));
            txtScoreEmpty.setDisable(isLocked);
            txtScoreIncorrect.setText(String.valueOf(currentEvaluation.getDefaultScoreIfIncorrect()));
            txtScoreIncorrect.setDisable(isLocked);
            updateFieldValidationVisuals(txtScoreCorrect, true, null);
            updateFieldValidationVisuals(txtScoreEmpty, true, null);
            updateFieldValidationVisuals(txtScoreIncorrect, true, null);

            if (btnAddQuestion != null) btnAddQuestion.setDisable(isLocked);
            if (btnImportExcel != null) btnImportExcel.setDisable(isLocked);
            if (btnGeneratePdf != null) btnGeneratePdf.setDisable(isLocked);
            if (progressBox != null) {
                progressBox.setVisible(false);
                progressBox.setManaged(false);
            }

            if (currentEvaluation.isSameScore() && currentEvaluation.getQuestions() != null) {
                double sc = currentEvaluation.getDefaultScoreIfCorrect();
                double se = currentEvaluation.getDefaultScoreIfEmpty();
                double si = currentEvaluation.getDefaultScoreIfIncorrect();
                for (int i = 0; i < currentEvaluation.getQuestions().size(); i++) {
                    Question q = currentEvaluation.getQuestions().get(i);
                    if (q.scoreIfCorrect() != sc || q.scoreIfEmpty() != se || q.scoreIfIncorrect() != si) {
                        currentEvaluation.getQuestions().set(i, new Question(
                                q.id(), q.content(), q.imagePath(), q.multipleChoice(),
                                sc, se, si, q.answers()
                        ));
                    }
                }
            }

            questionsList.setAll(currentEvaluation.getQuestions());
            recalculateTotalPoints();
        } finally {
            isUpdatingUi = false;
        }

        if (lblSaveStatus != null) {
            lblSaveStatus.setText(isLocked ? "" : "✓ Enregistré");
            lblSaveStatus.setStyle("-fx-text-fill: #1b8744; -fx-font-weight: 600; -fx-font-size: 11px;");
        }
    }

    private void saveEvaluationData() {
        if (currentEvaluation == null) return;
        if (currentEvaluation.getState() != EvaluationState.CONCEPTION) {
            log.warn("Tentative de modification ignorée pour l'évaluation verrouillée {}", currentEvaluation.getId());
            return;
        }

        String des = txtDesignation.getText() != null ? txtDesignation.getText().trim() : "";
        currentEvaluation.setDesignation(des);
        if (lblTitle != null && !des.isEmpty()) {
            lblTitle.setText("Concepteur d'Évaluation : " + des);
        }

        if (comboType != null && comboType.getValue() != null) {
            currentEvaluation.setType(comboType.getValue());
        }
        if (spinDuration != null && spinDuration.getValue() != null) {
            currentEvaluation.setDuration(Duration.ofMinutes(spinDuration.getValue()));
        }
        if (comboColumns != null && comboColumns.getValue() != null) {
            currentEvaluation.setColumnCount(comboColumns.getValue());
        }
        if (chkShuffleQuestions != null) currentEvaluation.setQuestionShuffle(chkShuffleQuestions.isSelected());
        if (chkShuffleAnswers != null) currentEvaluation.setAnswersShuffle(chkShuffleAnswers.isSelected());
        if (chkDoubleSided != null) currentEvaluation.setDoubleSided(chkDoubleSided.isSelected());

        if (!areAllScoreFieldsValid()) {
            if (lblSaveStatus != null) {
                lblSaveStatus.setText("⚠ Barème invalide - non enregistré");
                lblSaveStatus.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 11px;");
            }
            return;
        }

        try {
            double sc = Double.parseDouble(txtScoreCorrect.getText().trim().replace(',', '.'));
            double se = Double.parseDouble(txtScoreEmpty.getText().trim().replace(',', '.'));
            double si = Double.parseDouble(txtScoreIncorrect.getText().trim().replace(',', '.'));

            boolean scoreChanged = (sc != currentEvaluation.getDefaultScoreIfCorrect()
                    || se != currentEvaluation.getDefaultScoreIfEmpty()
                    || si != currentEvaluation.getDefaultScoreIfIncorrect());

            currentEvaluation.setDefaultScoreIfCorrect(sc);
            currentEvaluation.setDefaultScoreIfEmpty(se);
            currentEvaluation.setDefaultScoreIfIncorrect(si);

            if (scoreChanged && currentEvaluation.isSameScore()) {
                for (int i = 0; i < questionsList.size(); i++) {
                    Question q = questionsList.get(i);
                    questionsList.set(i, new Question(
                            q.id(),
                            q.content(),
                            q.imagePath(),
                            q.multipleChoice(),
                            sc,
                            se,
                            si,
                            q.answers()
                    ));
                }
                questionsListView.refresh();
                recalculateTotalPoints();
            }
        } catch (Exception e) {
            log.warn("Erreur lors de la mise à jour des notes du barème : {}", e.getMessage());
            return;
        }

        currentEvaluation.setQuestions(new ArrayList<>(questionsList));
        evaluationService.saveEvaluation(currentEvaluation);
        if (mainLayoutController != null) {
            mainLayoutController.setActiveEvaluation(currentEvaluation);
        }
        if (lblSaveStatus != null) {
            lblSaveStatus.setText("✓ Enregistré à " + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
            lblSaveStatus.setStyle("-fx-text-fill: #1b8744; -fx-font-weight: 600; -fx-font-size: 11px;");
        }
    }

    private boolean validateScoresAndNotify() {
        if (!isScoreCorrectValid(txtScoreCorrect != null ? txtScoreCorrect.getText() : null)) {
            notificationService.showWarning("Barème invalide", "La note pour une réponse correcte doit être strictement positive (> 0).");
            if (txtScoreCorrect != null) txtScoreCorrect.requestFocus();
            return false;
        }
        if (!isScoreIncorrectValid(txtScoreIncorrect != null ? txtScoreIncorrect.getText() : null)) {
            notificationService.showWarning("Barème invalide", "La note pour une réponse fausse doit être 0 ou négative (≤ 0).");
            if (txtScoreIncorrect != null) txtScoreIncorrect.requestFocus();
            return false;
        }
        if (!isScoreEmptyValid(txtScoreEmpty != null ? txtScoreEmpty.getText() : null)) {
            notificationService.showWarning("Barème invalide", "La note pour « sans réponse » doit être un nombre valide (positif, négatif ou zéro).");
            if (txtScoreEmpty != null) txtScoreEmpty.requestFocus();
            return false;
        }
        return true;
    }

    @FXML
    public void onSave() {
        if (currentEvaluation != null && currentEvaluation.getState() != EvaluationState.CONCEPTION) {
            notificationService.showWarning("Modification impossible", "Cette évaluation est verrouillée (statut " + currentEvaluation.getState() + ") et ne peut plus être modifiée.");
            return;
        }
        if (!validateScoresAndNotify()) {
            return;
        }
        flushAutoSave();
        saveEvaluationData();
    }

    @FXML
    public void onAddQuestion() {
        if (currentEvaluation != null && currentEvaluation.getState() != EvaluationState.CONCEPTION) {
            notificationService.showWarning("Modification impossible", "Cette évaluation est verrouillée (statut " + currentEvaluation.getState() + ") et ne peut plus être modifiée.");
            return;
        }
        showQuestionDialog(null);
    }

    @FXML
    public void onImportExcel() {
        if (currentEvaluation != null && currentEvaluation.getState() != EvaluationState.CONCEPTION) {
            notificationService.showWarning("Modification impossible", "Cette évaluation est verrouillée (statut " + currentEvaluation.getState() + ") et ne peut plus être modifiée.");
            return;
        }

        if (!validateScoresAndNotify()) {
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Importer des questions depuis un fichier Excel");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Fichiers Excel (*.xlsx)", "*.xlsx")
        );
        Window window = questionsListView != null && questionsListView.getScene() != null ? questionsListView.getScene().getWindow() : null;
        File file = fileChooser.showOpenDialog(window);
        if (file == null) return;

        double scoreCorrect = parseDoubleOrDefault(txtScoreCorrect, 1.0);
        double scoreEmpty = parseDoubleOrDefault(txtScoreEmpty, 0.0);
        double scoreIncorrect = parseDoubleOrDefault(txtScoreIncorrect, 0.0);

        try (FileInputStream fis = new FileInputStream(file)) {
            List<Question> imported = questionExcelService.parseQuestionsFromExcel(fis, scoreCorrect, scoreEmpty, scoreIncorrect);

            if (imported.isEmpty()) {
                notificationService.showWarning("Importation vide", "Aucune question n'a été trouvée dans le fichier Excel.");
                return;
            }

            // Si la liste contient déjà des questions, demander s'il faut ajouter ou remplacer
            if (!questionsList.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Mode d'importation");
                alert.setHeaderText("Des questions existent déjà (" + questionsList.size() + " question(s))");
                alert.setContentText("Souhaitez-vous ajouter les nouvelles questions à la suite ou remplacer les questions existantes ?");

                ButtonType btnAppend = new ButtonType("Ajouter à la suite", ButtonBar.ButtonData.YES);
                ButtonType btnReplace = new ButtonType("Remplacer", ButtonBar.ButtonData.NO);
                ButtonType btnCancel = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);

                alert.getButtonTypes().setAll(btnAppend, btnReplace, btnCancel);

                javafx.scene.Node replaceNode = alert.getDialogPane().lookupButton(btnReplace);
                if (replaceNode != null) {
                    replaceNode.setStyle("-fx-background-color: #ba1a1a; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6px; -fx-cursor: hand;");
                }

                Optional<ButtonType> result = alert.showAndWait();

                if (result.isEmpty() || result.get() == btnCancel) {
                    return;
                } else if (result.get() == btnReplace) {
                    questionsList.setAll(imported);
                } else {
                    questionsList.addAll(imported);
                }
            } else {
                questionsList.addAll(imported);
            }

            recalculateTotalPoints();
            triggerInstantAutoSave();
            notificationService.showSuccess("Importation réussie", imported.size() + " question(s) importée(s) depuis Excel.");
        } catch (IllegalArgumentException ex) {
            log.warn("Validation échouée lors de l'import Excel des questions: {}", ex.getMessage());
            notificationService.showWarning("Fichier Excel invalide", ex.getMessage());
        } catch (Exception ex) {
            log.error("Erreur lors de l'import Excel des questions", ex);
            notificationService.showError("Erreur d'importation", "Impossible de lire le fichier Excel : " + ex.getMessage());
        }
    }

    @FXML
    public void onExportExcel() {
        boolean isTemplate = questionsList.isEmpty();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(isTemplate ? "Exporter le modèle Excel de questions" : "Exporter les questions vers Excel");

        String safeName = currentEvaluation != null && currentEvaluation.getDesignation() != null
                ? currentEvaluation.getDesignation().replaceAll("[^a-zA-Z0-9_-]", "_")
                : "";

        String defaultFileName = isTemplate
                ? (safeName.isEmpty() ? "Modele_Questions.xlsx" : "Modele_Questions_" + safeName + ".xlsx")
                : (safeName.isEmpty() ? "Questions.xlsx" : "Questions_" + safeName + ".xlsx");

        fileChooser.setInitialFileName(defaultFileName);
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Fichier Excel (*.xlsx)", "*.xlsx")
        );
        Window window = questionsListView != null && questionsListView.getScene() != null ? questionsListView.getScene().getWindow() : null;
        File file = fileChooser.showSaveDialog(window);
        if (file == null) return;

        try {
            questionExcelService.exportQuestionsToExcel(new ArrayList<>(questionsList), file);
            String successMsg = isTemplate
                    ? "Modèle Excel exporté avec succès vers : " + file.getName()
                    : "Questions exportées avec succès vers : " + file.getName();
            notificationService.showSuccess("Exportation réussie", successMsg);
        } catch (Exception ex) {
            log.error("Erreur lors de l'export Excel des questions", ex);
            notificationService.showError("Erreur d'exportation", "Impossible d'exporter le fichier Excel : " + ex.getMessage());
        }
    }

    private double parseDoubleOrDefault(TextField field, double def) {
        if (field == null || field.getText() == null) return def;
        try {
            return Double.parseDouble(field.getText().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void showQuestionDialog(Question existing) {
        Dialog<Question> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Ajouter une question" : "Modifier la question");
        dialog.setResizable(true);

        ButtonType btnOk = new ButtonType(existing == null ? "Ajouter" : "Enregistrer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnOk, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPadding(new Insets(15));
        content.setPrefWidth(600);

        TextArea txtQuestion = new TextArea(existing != null ? existing.content() : "");
        txtQuestion.setPromptText("Énoncé de la question...");
        txtQuestion.setPrefRowCount(2);
        txtQuestion.setWrapText(true);
        txtQuestion.setStyle("-fx-background-radius: 6px;");

        // Section Image d'illustration unifiée avec ImageUploadBox
        ImageUploadBox imageUploadBox = new ImageUploadBox(
                "Illustration de la question",
                "Format recommandé : PNG, JPG, WebP",
                ImageUploadBox.DisplayMode.LANDSCAPE
        );

        if (existing != null && existing.imagePath() != null && !existing.imagePath().isBlank()) {
            Path currentPath = imageStorageService.getImagePath(existing.imagePath(), ImageStorageService.ImageFolder.QUESTION_IMAGES, false);
            if (currentPath != null && Files.exists(currentPath)) {
                imageUploadBox.setInitialImage(currentPath, existing.imagePath());
            }
        }

        imageUploadBox.setOnChange(() -> {
            Platform.runLater(() -> {
                if (dialog.getDialogPane().getScene() != null && dialog.getDialogPane().getScene().getWindow() != null) {
                    dialog.getDialogPane().applyCss();
                    dialog.getDialogPane().layout();
                    dialog.getDialogPane().getScene().getWindow().sizeToScene();
                }
            });
        });

        Label lblAnswers = new Label("Choix de réponses (2 à 7 réponses, cochez la ou les bonnes réponses) :");
        lblAnswers.setStyle("-fx-font-weight: 600; -fx-font-size: 12px;");
        VBox answersContainer = new VBox(8);

        List<AnswerRow> answerRows = new ArrayList<>();
        Button btnAddChoice = new Button("Ajouter un choix", new FontIcon(Feather.PLUS));

        Runnable updateUI = () -> {
            int count = answerRows.size();
            boolean canDelete = count > 2;
            for (AnswerRow r : answerRows) {
                r.btnDelete.setDisable(!canDelete);
                r.btnDelete.setTooltip(new Tooltip(canDelete ? "Supprimer cette réponse" : "Une question doit comporter au moins 2 réponses"));
            }
            boolean canAdd = count < 7;
            btnAddChoice.setDisable(!canAdd);
            btnAddChoice.setTooltip(new Tooltip(canAdd ? "Ajouter un choix de réponse (max 7)" : "Maximum de 7 réponses autorisées"));

            Platform.runLater(() -> {
                if (dialog.getDialogPane().getScene() != null) {
                    Window window = dialog.getDialogPane().getScene().getWindow();
                    if (window != null) {
                        dialog.getDialogPane().applyCss();
                        dialog.getDialogPane().layout();
                        window.sizeToScene();
                        try {
                            javafx.collections.ObservableList<Screen> screens =
                                    Screen.getScreensForRectangle(window.getX(), window.getY(), window.getWidth(), window.getHeight());
                            Screen screen = screens.isEmpty() ? Screen.getPrimary() : screens.get(0);
                            if (screen != null) {
                                Rectangle2D bounds = screen.getVisualBounds();
                                if (window.getY() + window.getHeight() > bounds.getMaxY()) {
                                    window.setY(Math.max(bounds.getMinY(), bounds.getMaxY() - window.getHeight() - 15));
                                }
                            }
                        } catch (Exception ignored) {
                            // Non-blocking in headless or custom graphic environments
                        }
                    }
                }
            });
        };

        java.util.function.Consumer<AnswerRow> removeRow = row -> {
            answersContainer.getChildren().remove(row.node);
            answerRows.remove(row);
            updateUI.run();
        };

        if (existing != null && existing.answers() != null && !existing.answers().isEmpty()) {
            for (Answer a : existing.answers()) {
                AnswerRow[] rowHolder = new AnswerRow[1];
                rowHolder[0] = new AnswerRow(a.content(), a.correct(), () -> removeRow.accept(rowHolder[0]));
                answerRows.add(rowHolder[0]);
                answersContainer.getChildren().add(rowHolder[0].node);
            }
        } else {
            for (int i = 0; i < 3; i++) {
                AnswerRow[] rowHolder = new AnswerRow[1];
                rowHolder[0] = new AnswerRow("", i == 0, () -> removeRow.accept(rowHolder[0]));
                answerRows.add(rowHolder[0]);
                answersContainer.getChildren().add(rowHolder[0].node);
            }
        }
        updateUI.run();

        btnAddChoice.setOnAction(e -> {
            if (answerRows.size() >= 7) {
                return;
            }
            AnswerRow[] rowHolder = new AnswerRow[1];
            rowHolder[0] = new AnswerRow("", false, () -> removeRow.accept(rowHolder[0]));
            answerRows.add(rowHolder[0]);
            answersContainer.getChildren().add(rowHolder[0].node);
            updateUI.run();
        });

        content.getChildren().addAll(
                new Label("Énoncé :"), txtQuestion,
                new Label("Image d'illustration (optionnelle) :"), imageUploadBox,
                lblAnswers, answersContainer, btnAddChoice
        );
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(btn -> {
            if (btn == btnOk) {
                if (txtQuestion.getText().isBlank()) {
                    notificationService.showWarning("Champs requis", "L'énoncé de la question est obligatoire.");
                    return null;
                }
                List<Answer> answers = new ArrayList<>();
                for (AnswerRow row : answerRows) {
                    if (!row.text.getText().isBlank()) {
                        answers.add(new Answer(UUID.randomUUID().toString(), row.text.getText().trim(), row.check.isSelected()));
                    }
                }
                if (answers.size() < 2) {
                    notificationService.showWarning("Réponses insuffisantes", "Indiquez au moins 2 réponses valides.");
                    return null;
                }
                if (answers.size() > 7) {
                    notificationService.showWarning("Limite dépassée", "Une question ne peut pas comporter plus de 7 choix de réponses.");
                    return null;
                }
                boolean hasCorrect = answers.stream().anyMatch(Answer::correct);
                if (!hasCorrect) {
                    notificationService.showWarning("Réponse correcte requise", "Cochez au moins une réponse comme étant correcte.");
                    return null;
                }

                String finalImagePath = null;
                if (imageUploadBox.isRemoved()) {
                    finalImagePath = null;
                } else if (imageUploadBox.getFile() != null) {
                    finalImagePath = imageStorageService.storeImage(imageUploadBox.getFile(), ImageStorageService.ImageFolder.QUESTION_IMAGES);
                } else if (existing != null) {
                    finalImagePath = existing.imagePath();
                }

                boolean isMulti = answers.stream().filter(Answer::correct).count() > 1;
                double defaultCorrect = currentEvaluation != null ? currentEvaluation.getDefaultScoreIfCorrect() : 1.0;
                double defaultEmpty = currentEvaluation != null ? currentEvaluation.getDefaultScoreIfEmpty() : 0.0;
                double defaultIncorrect = currentEvaluation != null ? currentEvaluation.getDefaultScoreIfIncorrect() : 0.0;
                return new Question(
                        existing != null ? existing.id() : UUID.randomUUID().toString(),
                        txtQuestion.getText().trim(),
                        finalImagePath,
                        isMulti,
                        existing != null ? existing.scoreIfCorrect() : defaultCorrect,
                        existing != null ? existing.scoreIfEmpty() : defaultEmpty,
                        existing != null ? existing.scoreIfIncorrect() : defaultIncorrect,
                        answers
                );
            }
            return null;
        });

        dialog.showAndWait().ifPresent(q -> {
            if (existing != null) {
                int idx = questionsList.indexOf(existing);
                if (idx >= 0) questionsList.set(idx, q);
            } else {
                questionsList.add(q);
            }
            triggerInstantAutoSave();
        });
    }

    private static class AnswerRow {
        final CheckBox check = new CheckBox();
        final TextField text = new TextField();
        final Button btnDelete = new Button();
        final HBox node;

        AnswerRow(String initialText, boolean isCorrect, Runnable onDelete) {
            check.setSelected(isCorrect);
            check.setTooltip(new Tooltip("Cocher si cette réponse est correcte"));

            text.setText(initialText);
            text.setPromptText("Texte de la réponse...");
            text.setStyle("-fx-background-radius: 6px; -fx-padding: 6 10;");
            HBox.setHgrow(text, Priority.ALWAYS);

            btnDelete.getStyleClass().add("card-action-btn");
            FontIcon trashIcon = new FontIcon(Feather.TRASH_2);
            trashIcon.setIconColor(javafx.scene.paint.Color.web("#ba1a1a"));
            trashIcon.setIconSize(13);
            btnDelete.setGraphic(trashIcon);
            btnDelete.setOnAction(e -> onDelete.run());

            node = new HBox(8, check, text, btnDelete);
            node.setAlignment(Pos.CENTER_LEFT);
        }
    }

    @FXML
    public void onPreviewDraft() {
        if (currentEvaluation == null) return;
        flushAutoSave();
        saveEvaluationData();

        Thread.startVirtualThread(() -> {
            try {
                byte[] pdfBytes = documentService.generateDraftEvaluation(currentEvaluation.getGroupId(), currentEvaluation.getId());
                File tempPdf = File.createTempFile("draft_eval_", ".pdf");
                tempPdf.deleteOnExit();
                try (FileOutputStream fos = new FileOutputStream(tempPdf)) {
                    fos.write(pdfBytes);
                }
                Platform.runLater(() -> {
                    String evalId = currentEvaluation.getId();
                    String title = "Aperçu Brouillon : " + currentEvaluation.getDesignation();
                    mainLayoutController.openPdfViewer(tempPdf, title, () -> {
                        mainLayoutController.openEvaluationBuilder(evalId);
                    });
                });
            } catch (Exception e) {
                log.error("Échec de la prévisualisation brouillon", e);
                Platform.runLater(() -> notificationService.showError("Erreur de prévisualisation", e.getMessage()));
            }
        });
    }

    @FXML
    public void onGeneratePdf() {
        if (currentEvaluation == null) return;
        flushAutoSave();

        if (currentEvaluation.getState() != EvaluationState.CONCEPTION) {
            notificationService.showWarning("Déjà générée", "Cette évaluation a déjà été générée et se trouve en production.");
            return;
        }

        saveEvaluationData();

        if (questionsList.isEmpty()) {
            notificationService.showWarning("Questions manquantes", "Ajoutez au moins une question avant de générer les copies PDF.");
            return;
        }

        String groupId = currentEvaluation.getGroupId();
        String evalId = currentEvaluation.getId();

        if (groupId == null || groupId.isBlank()) {
            notificationService.showWarning("Groupe manquant", "Aucun groupe n'est associé à cette évaluation.");
            return;
        }

        if (btnGeneratePdf != null) btnGeneratePdf.setDisable(true);
        if (progressBox != null) {
            progressBox.setVisible(true);
            progressBox.setManaged(true);
        }
        if (progressBar != null) progressBar.setProgress(0.0);
        if (lblProgress != null) lblProgress.setText("Génération des copies avec codes-barres personnalisés...");

        TaskProgressDialog progressDialog = new TaskProgressDialog(
                btnGeneratePdf != null && btnGeneratePdf.getScene() != null ? btnGeneratePdf.getScene().getWindow() : null,
                "Génération des copies",
                "Génération du sujet PDF personnalisé",
                Feather.FILE_TEXT
        );
        progressDialog.show();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                documentService.generateEvaluation(groupId, evalId,
                        tp -> {
                            progressDialog.update(tp);
                            Platform.runLater(() -> {
                                if (progressBar != null) progressBar.setProgress(tp.progressRatio());
                                if (lblProgress != null) lblProgress.setText(tp.stageTitle() + " • " + tp.currentDetail());
                            });
                        });
                return null;
            }
        };

        progressDialog.setOnCancel(() -> task.cancel(true));

        task.setOnCancelled(e -> {
            progressDialog.close();
            if (btnGeneratePdf != null) btnGeneratePdf.setDisable(false);
            if (progressBox != null) {
                progressBox.setVisible(false);
                progressBox.setManaged(false);
            }
            notificationService.showWarning("Génération interrompue", "La génération des copies a été annulée par l'utilisateur.");
        });

        task.setOnSucceeded(e -> {
            progressDialog.close();
            if (btnGeneratePdf != null) btnGeneratePdf.setDisable(false);
            if (progressBox != null) {
                progressBox.setVisible(false);
                progressBox.setManaged(false);
            }
            notificationService.showSuccess("Génération terminée",
                    "Le sujet PDF avec code-barres PDF417 a été généré avec succès !\nL'épreuve est passée en production.");
            if (mainLayoutController != null) {
                mainLayoutController.showCorrectionForEvaluation(groupId, evalId);
            }
        });

        task.setOnFailed(e -> {
            progressDialog.close();
            if (btnGeneratePdf != null) btnGeneratePdf.setDisable(false);
            if (progressBox != null) {
                progressBox.setVisible(false);
                progressBox.setManaged(false);
            }
            Throwable ex = task.getException();
            log.error("Échec de la génération des copies PDF", ex);
            notificationService.showError("Erreur de génération", ex != null ? ex.getMessage() : "Une erreur est survenue lors de la génération.");
        });

        Thread.startVirtualThread(task);
    }

    @FXML
    public void onBack() {
        flushAutoSave();
        if (mainLayoutController != null) {
            mainLayoutController.navigateBack();
        } else {
            if (currentEvaluation != null && currentEvaluation.getGroupId() != null) {
                // Fallback
            }
        }
    }
}
