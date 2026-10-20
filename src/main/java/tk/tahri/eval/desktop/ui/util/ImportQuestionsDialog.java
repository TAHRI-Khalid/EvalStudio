package tk.tahri.eval.desktop.ui.util;

import atlantafx.base.theme.Styles;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import tk.tahri.eval.desktop.model.Evaluation;
import tk.tahri.eval.desktop.model.Group;
import tk.tahri.eval.desktop.model.Question;
import tk.tahri.eval.desktop.service.EvaluationService;
import tk.tahri.eval.desktop.service.GroupService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Boîte de dialogue permettant d'importer tout ou partie des questions
 * issues d'une autre évaluation d'un groupe donné.
 */
public class ImportQuestionsDialog extends Dialog<ImportQuestionsDialog.ImportResult> {

    public record ImportResult(
            List<Question> selectedQuestions,
            boolean replaceExisting,
            boolean applyDefaultScore
    ) {}

    private static class QuestionSelectionItem {
        private final Question question;
        private final int originalIndex;
        private final BooleanProperty selected = new SimpleBooleanProperty(false);

        public QuestionSelectionItem(Question question, int originalIndex) {
            this.question = question;
            this.originalIndex = originalIndex;
        }

        public Question getQuestion() { return question; }
        public int getOriginalIndex() { return originalIndex; }
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean val) { selected.set(val); }
        public BooleanProperty selectedProperty() { return selected; }
    }

    private final GroupService groupService;
    private final EvaluationService evaluationService;
    private final String currentEvaluationId;
    private final String currentGroupId;
    private final boolean hasExistingQuestions;
    private final boolean sameScoreActive;
    private final double defaultScoreCorrect;

    private ComboBox<Group> comboGroups;
    private ComboBox<Evaluation> comboEvaluations;
    private CheckBox chkSelectAll;
    private Label lblSelectedCount;
    private VBox questionsContainer;
    private ScrollPane scrollPaneQuestions;

    private RadioButton rbAppend;
    private RadioButton rbReplace;
    private CheckBox chkApplyDefaultBareme;

    private Button btnImport;
    private final ButtonType btnImportType = new ButtonType("Importer", ButtonBar.ButtonData.OK_DONE);

    private final List<QuestionSelectionItem> currentItems = new ArrayList<>();
    private boolean isBatchUpdating = false;

    public ImportQuestionsDialog(Window owner,
                                 GroupService groupService,
                                 EvaluationService evaluationService,
                                 String currentEvaluationId,
                                 String currentGroupId,
                                 boolean hasExistingQuestions,
                                 boolean sameScoreActive,
                                 double defaultScoreCorrect) {
        this.groupService = groupService;
        this.evaluationService = evaluationService;
        this.currentEvaluationId = currentEvaluationId;
        this.currentGroupId = currentGroupId;
        this.hasExistingQuestions = hasExistingQuestions;
        this.sameScoreActive = sameScoreActive;
        this.defaultScoreCorrect = defaultScoreCorrect;

        initOwner(owner);
        setTitle("Importer des questions depuis une autre évaluation");
        setResizable(true);

        DialogPane pane = getDialogPane();
        pane.getButtonTypes().addAll(btnImportType, ButtonType.CANCEL);
        pane.setPrefWidth(720);
        pane.setPrefHeight(600);
        pane.setMinWidth(580);
        pane.setMinHeight(450);

        pane.setContent(buildContent());

        btnImport = (Button) pane.lookupButton(btnImportType);
        if (btnImport != null) {
            btnImport.getStyleClass().add(Styles.ACCENT);
            btnImport.setGraphic(new FontIcon(Feather.DOWNLOAD));
            btnImport.setDisable(true);
        }

        setResultConverter(button -> {
            if (button == btnImportType) {
                List<Question> selected = currentItems.stream()
                        .filter(QuestionSelectionItem::isSelected)
                        .map(QuestionSelectionItem::getQuestion)
                        .collect(Collectors.toList());

                boolean replace = rbReplace != null && rbReplace.isSelected();
                boolean applyDefault = chkApplyDefaultBareme != null && chkApplyDefaultBareme.isSelected();
                return new ImportResult(selected, replace, applyDefault);
            }
            return null;
        });

        loadInitialData();
    }

    private Node buildContent() {
        VBox root = new VBox(14);
        root.setPadding(new Insets(16, 16, 8, 16));

        // En-tête explicatif
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-padding: 0 0 10 0; -fx-border-color: transparent transparent -color-border-muted transparent; -fx-border-width: 0 0 1 0;");

        FontIcon iconHeader = new FontIcon(Feather.COPY);
        iconHeader.setIconSize(24);
        iconHeader.setIconColor(javafx.scene.paint.Color.web("#004faa"));

        VBox titleBox = new VBox(2);
        Label lblTitle = new Label("Sélection des questions à réutiliser");
        lblTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: -color-fg-default;");
        Label lblSub = new Label("Choisissez le groupe et l'évaluation source, puis cochez les questions souhaitées.");
        lblSub.setStyle("-fx-font-size: 12px; -fx-text-fill: -color-fg-muted;");
        titleBox.getChildren().addAll(lblTitle, lblSub);
        header.getChildren().addAll(iconHeader, titleBox);

        // Sélecteurs : Groupe & Évaluation
        GridPane selectorsGrid = new GridPane();
        selectorsGrid.setHgap(14);
        selectorsGrid.setVgap(6);

        Label lblGroup = new Label("Groupe source :");
        lblGroup.setStyle("-fx-font-weight: 600; -fx-font-size: 12px;");
        comboGroups = new ComboBox<>();
        comboGroups.setMaxWidth(Double.MAX_VALUE);
        comboGroups.setPromptText("Sélectionnez un groupe");

        comboGroups.setConverter(new StringConverter<>() {
            @Override
            public String toString(Group g) {
                if (g == null) return "";
                String sec = g.getSection() != null && !g.getSection().isBlank() ? " • " + g.getSection() : "";
                return g.getName() + sec;
            }
            @Override
            public Group fromString(String string) { return null; }
        });

        Label lblEval = new Label("Évaluation source :");
        lblEval.setStyle("-fx-font-weight: 600; -fx-font-size: 12px;");
        comboEvaluations = new ComboBox<>();
        comboEvaluations.setMaxWidth(Double.MAX_VALUE);
        comboEvaluations.setPromptText("Sélectionnez une évaluation");

        comboEvaluations.setConverter(new StringConverter<>() {
            @Override
            public String toString(Evaluation ev) {
                if (ev == null) return "";
                int count = ev.getQuestions() != null ? ev.getQuestions().size() : 0;
                return ev.getDesignation() + " (" + count + " question" + (count > 1 ? "s" : "") + ")";
            }
            @Override
            public Evaluation fromString(String string) { return null; }
        });

        selectorsGrid.add(lblGroup, 0, 0);
        selectorsGrid.add(comboGroups, 0, 1);
        selectorsGrid.add(lblEval, 1, 0);
        selectorsGrid.add(comboEvaluations, 1, 1);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);
        col1.setHgrow(Priority.ALWAYS);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(50);
        col2.setHgrow(Priority.ALWAYS);
        selectorsGrid.getColumnConstraints().addAll(col1, col2);

        // Barre de contrôle de la liste de questions
        HBox listBar = new HBox(10);
        listBar.setAlignment(Pos.CENTER_LEFT);

        chkSelectAll = new CheckBox("Tout sélectionner");
        chkSelectAll.setStyle("-fx-font-weight: 600; -fx-font-size: 13px;");
        chkSelectAll.setOnAction(e -> handleSelectAll());

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        lblSelectedCount = new Label("0 sélectionnée(s)");
        lblSelectedCount.getStyleClass().add("tag-badge");
        lblSelectedCount.setStyle("-fx-font-size: 12px; -fx-font-weight: 600;");

        listBar.getChildren().addAll(chkSelectAll, spacer, lblSelectedCount);

        // Conteneur avec défilement des questions
        questionsContainer = new VBox(8);
        questionsContainer.setStyle("-fx-padding: 4;");

        scrollPaneQuestions = new ScrollPane(questionsContainer);
        scrollPaneQuestions.setFitToWidth(true);
        scrollPaneQuestions.setStyle("-fx-background-color: transparent; -fx-border-color: -color-border-muted; -fx-border-radius: 8px; -fx-background-radius: 8px;");
        VBox.setVgrow(scrollPaneQuestions, Priority.ALWAYS);

        // Options d'importation (Ajouter / Remplacer + Barème)
        VBox optionsBox = new VBox(8);
        optionsBox.setStyle("-fx-padding: 10 12; -fx-background-color: -color-bg-subtle; -fx-background-radius: 8px; -fx-border-color: -color-border-muted; -fx-border-radius: 8px;");

        if (hasExistingQuestions) {
            Label lblMode = new Label("Mode d'insertion :");
            lblMode.setStyle("-fx-font-weight: 600; -fx-font-size: 12px;");

            ToggleGroup tgMode = new ToggleGroup();
            rbAppend = new RadioButton("Ajouter à la suite des questions actuelles");
            rbAppend.setToggleGroup(tgMode);
            rbAppend.setSelected(true);

            rbReplace = new RadioButton("Remplacer toutes les questions existantes de l'évaluation");
            rbReplace.setToggleGroup(tgMode);

            HBox modeRadios = new HBox(16, rbAppend, rbReplace);
            optionsBox.getChildren().addAll(lblMode, modeRadios);
        }

        chkApplyDefaultBareme = new CheckBox(
                sameScoreActive
                        ? String.format("Harmoniser avec le barème par défaut de l'évaluation courante (%.2f pt(s) par question)", defaultScoreCorrect)
                        : "Conserver les points et le barème d'origine de chaque question"
        );
        chkApplyDefaultBareme.setSelected(sameScoreActive);
        optionsBox.getChildren().add(chkApplyDefaultBareme);

        root.getChildren().addAll(header, selectorsGrid, listBar, scrollPaneQuestions, optionsBox);

        // Écouteurs de changement
        comboGroups.setOnAction(e -> onGroupSelected());
        comboEvaluations.setOnAction(e -> onEvaluationSelected());

        return root;
    }

    private void loadInitialData() {
        List<Group> allGroups = groupService.getAllGroups();
        comboGroups.setItems(FXCollections.observableArrayList(allGroups));

        // Sélection par défaut : le groupe actuel si trouvé, sinon le premier
        Group initialGroup = null;
        if (currentGroupId != null) {
            initialGroup = allGroups.stream()
                    .filter(g -> Objects.equals(g.getId(), currentGroupId))
                    .findFirst()
                    .orElse(null);
        }
        if (initialGroup == null && !allGroups.isEmpty()) {
            initialGroup = allGroups.get(0);
        }

        if (initialGroup != null) {
            comboGroups.getSelectionModel().select(initialGroup);
            loadEvaluationsForGroup(initialGroup.getId());
        } else {
            showEmptyState("Aucun groupe disponible.");
        }
    }

    private void onGroupSelected() {
        Group selected = comboGroups.getValue();
        if (selected != null) {
            loadEvaluationsForGroup(selected.getId());
        } else {
            comboEvaluations.setItems(FXCollections.emptyObservableList());
            showEmptyState("Sélectionnez un groupe.");
        }
    }

    private void loadEvaluationsForGroup(String groupId) {
        List<Evaluation> evals = evaluationService.getEvaluationsByGroup(groupId);
        // Exclure l'évaluation courante pour éviter d'importer une évaluation dans elle-même
        List<Evaluation> filtered = evals.stream()
                .filter(ev -> !Objects.equals(ev.getId(), currentEvaluationId))
                .collect(Collectors.toList());

        comboEvaluations.setItems(FXCollections.observableArrayList(filtered));

        if (!filtered.isEmpty()) {
            comboEvaluations.getSelectionModel().selectFirst();
            loadQuestionsForEvaluation(comboEvaluations.getValue());
        } else {
            comboEvaluations.setValue(null);
            showEmptyState("Aucune autre évaluation trouvée dans ce groupe.");
        }
    }

    private void onEvaluationSelected() {
        Evaluation selected = comboEvaluations.getValue();
        if (selected != null) {
            loadQuestionsForEvaluation(selected);
        } else {
            showEmptyState("Sélectionnez une évaluation source.");
        }
    }

    private void loadQuestionsForEvaluation(Evaluation eval) {
        currentItems.clear();
        questionsContainer.getChildren().clear();

        List<Question> questions = eval.getQuestions();
        if (questions == null || questions.isEmpty()) {
            showEmptyState("Cette évaluation ne comporte aucune question.");
            updateSelectionSummary();
            return;
        }

        chkSelectAll.setDisable(false);
        int index = 1;
        for (Question q : questions) {
            QuestionSelectionItem item = new QuestionSelectionItem(q, index++);
            item.selectedProperty().addListener((obs, oldVal, newVal) -> {
                if (!isBatchUpdating) {
                    updateSelectionSummary();
                }
            });
            currentItems.add(item);
            questionsContainer.getChildren().add(buildQuestionCard(item));
        }

        // Par défaut : cocher toutes les questions pour faciliter l'import
        selectAll(true);
    }

    private Node buildQuestionCard(QuestionSelectionItem item) {
        Question q = item.getQuestion();

        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(10, 12, 10, 12));
        card.setStyle(
                "-fx-background-color: -color-bg-default; " +
                "-fx-border-color: -color-border-muted; " +
                "-fx-border-radius: 8px; " +
                "-fx-background-radius: 8px; " +
                "-fx-cursor: hand;"
        );

        CheckBox chk = new CheckBox();
        chk.selectedProperty().bindBidirectional(item.selectedProperty());

        Label lblNum = new Label("Q" + item.getOriginalIndex());
        lblNum.setStyle("-fx-font-weight: 800; -fx-font-size: 13px; -fx-text-fill: -color-accent-emphasis; -fx-min-width: 32px;");

        VBox centerBox = new VBox(4);
        HBox.setHgrow(centerBox, Priority.ALWAYS);

        String cleanContent = q.content() != null && !q.content().isBlank()
                ? q.content().trim().replaceAll("\\s+", " ")
                : "(Sans texte)";
        Label lblContent = new Label(cleanContent);
        lblContent.setWrapText(true);
        lblContent.setMaxHeight(40);
        lblContent.setStyle("-fx-font-size: 13px; -fx-text-fill: -color-fg-default;");

        HBox tags = new HBox(8);
        tags.setAlignment(Pos.CENTER_LEFT);

        Label tagType = new Label(q.multipleChoice() ? "Choix multiple" : "Choix unique");
        tagType.getStyleClass().add("tag-badge");

        int ansCount = q.answers() != null ? q.answers().size() : 0;
        Label tagAnswers = new Label(ansCount + " choix");
        tagAnswers.getStyleClass().add("tag-badge");

        tags.getChildren().addAll(tagType, tagAnswers);

        if (q.imagePath() != null && !q.imagePath().isBlank()) {
            HBox tagImg = new HBox(4);
            tagImg.setAlignment(Pos.CENTER_LEFT);
            tagImg.getStyleClass().add("tag-badge");
            FontIcon iconImg = new FontIcon(Feather.IMAGE);
            iconImg.setIconSize(12);
            tagImg.getChildren().addAll(iconImg, new Label("Image"));
            tags.getChildren().add(tagImg);
        }

        centerBox.getChildren().addAll(lblContent, tags);

        Label lblScore = new Label(String.format("+%.2f pt%s", q.scoreIfCorrect(), q.scoreIfCorrect() > 1.0 ? "s" : ""));
        lblScore.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: -color-accent-emphasis;");

        card.getChildren().addAll(chk, lblNum, centerBox, lblScore);

        // Cliquer n'importe où sur la carte inverse la sélection
        card.setOnMouseClicked(e -> {
            if (e.getTarget() != chk) {
                item.setSelected(!item.isSelected());
            }
        });

        // Feedback au survol
        card.setOnMouseEntered(e -> card.setStyle(
                "-fx-background-color: -color-bg-subtle; " +
                "-fx-border-color: -color-accent-emphasis; " +
                "-fx-border-radius: 8px; " +
                "-fx-background-radius: 8px; " +
                "-fx-cursor: hand;"
        ));
        card.setOnMouseExited(e -> card.setStyle(
                "-fx-background-color: -color-bg-default; " +
                "-fx-border-color: -color-border-muted; " +
                "-fx-border-radius: 8px; " +
                "-fx-background-radius: 8px; " +
                "-fx-cursor: hand;"
        ));

        return card;
    }

    private void handleSelectAll() {
        if (isBatchUpdating) return;
        boolean targetState = chkSelectAll.isSelected();
        selectAll(targetState);
    }

    private void selectAll(boolean select) {
        isBatchUpdating = true;
        for (QuestionSelectionItem it : currentItems) {
            it.setSelected(select);
        }
        isBatchUpdating = false;
        updateSelectionSummary();
    }

    private void updateSelectionSummary() {
        int total = currentItems.size();
        long count = currentItems.stream().filter(QuestionSelectionItem::isSelected).count();

        lblSelectedCount.setText(String.format("%d / %d sélectionnée%s", count, total, count > 1 ? "s" : ""));

        // Synchronisation de l'état de chkSelectAll
        isBatchUpdating = true;
        if (count == 0) {
            chkSelectAll.setSelected(false);
            chkSelectAll.setIndeterminate(false);
        } else if (count == total) {
            chkSelectAll.setSelected(true);
            chkSelectAll.setIndeterminate(false);
        } else {
            chkSelectAll.setIndeterminate(true);
        }
        isBatchUpdating = false;

        if (btnImport != null) {
            btnImport.setDisable(count == 0);
            btnImport.setText(count > 0 ? String.format("Importer (%d)", count) : "Importer");
        }
    }

    private void showEmptyState(String message) {
        currentItems.clear();
        questionsContainer.getChildren().clear();
        chkSelectAll.setDisable(true);
        chkSelectAll.setSelected(false);
        chkSelectAll.setIndeterminate(false);

        VBox emptyBox = new VBox(10);
        emptyBox.setAlignment(Pos.CENTER);
        emptyBox.setPadding(new Insets(32));

        FontIcon icon = new FontIcon(Feather.INFO);
        icon.setIconSize(28);
        icon.setIconColor(javafx.scene.paint.Color.web("#94a3b8"));

        Label msg = new Label(message);
        msg.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 13px; -fx-font-weight: 500;");

        emptyBox.getChildren().addAll(icon, msg);
        questionsContainer.getChildren().add(emptyBox);

        updateSelectionSummary();
    }
}
