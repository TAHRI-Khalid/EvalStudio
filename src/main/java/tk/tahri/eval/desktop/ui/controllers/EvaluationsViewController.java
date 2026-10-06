package tk.tahri.eval.desktop.ui.controllers;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import tk.tahri.eval.desktop.document.services.DocumentService;
import tk.tahri.eval.desktop.model.Group;
import tk.tahri.eval.desktop.model.Evaluation;
import tk.tahri.eval.desktop.model.EvaluationState;
import tk.tahri.eval.desktop.service.GroupService;
import tk.tahri.eval.desktop.service.EvaluationService;
import tk.tahri.eval.desktop.ui.NotificationService;

import java.io.File;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.ResourceBundle;

@Component
public class EvaluationsViewController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(EvaluationsViewController.class);

    @FXML private Button btnNewEval;

    @FXML private TableView<Evaluation> evalTable;
    @FXML private TableColumn<Evaluation, String> colDesignation;
    @FXML private TableColumn<Evaluation, String> colType;
    @FXML private TableColumn<Evaluation, String> colDate;
    @FXML private TableColumn<Evaluation, String> colState;
    @FXML private TableColumn<Evaluation, Integer> colQuestions;
    @FXML private TableColumn<Evaluation, Void> colActions;

    private final GroupService groupService;
    private final EvaluationService evaluationService;
    private final DocumentService documentService;
    private final NotificationService notificationService;
    private final MainLayoutController mainLayoutController;

    private final ObservableList<Evaluation> evalList = FXCollections.observableArrayList();
    private Group currentGroup;

    public EvaluationsViewController(GroupService groupService,
                                     EvaluationService evaluationService,
                                     DocumentService documentService,
                                     NotificationService notificationService,
                                     @Lazy MainLayoutController mainLayoutController) {
        this.groupService = groupService;
        this.evaluationService = evaluationService;
        this.documentService = documentService;
        this.notificationService = notificationService;
        this.mainLayoutController = mainLayoutController;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTable();
        setupIcons();
        setupListeners();
        if (mainLayoutController != null) {
            mainLayoutController.clearActiveEvaluation();
        }
    }

    private void setupTable() {
        colDesignation.setCellValueFactory(new PropertyValueFactory<>("designation"));
        if (colType != null) {
            colType.setCellValueFactory(cellData -> {
                if (cellData.getValue() != null && cellData.getValue().getType() != null) {
                    return new javafx.beans.property.SimpleStringProperty(cellData.getValue().getType());
                }
                return new javafx.beans.property.SimpleStringProperty(Evaluation.TYPE_CONTROLE_CONTINU);
            });
            colType.setCellFactory(col -> new TableCell<>() {
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
        }
        colDate.setCellValueFactory(cellData -> {
            if (cellData.getValue() != null && cellData.getValue().getCreatedAt() != null) {
                return new javafx.beans.property.SimpleStringProperty(
                        cellData.getValue().getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                );
            }
            return new javafx.beans.property.SimpleStringProperty("-");
        });

        colState.setCellValueFactory(cellData -> {
            if (cellData.getValue() != null && cellData.getValue().getState() != null) {
                return new javafx.beans.property.SimpleStringProperty(cellData.getValue().getState().name());
            }
            return new javafx.beans.property.SimpleStringProperty("CONCEPTION");
        });
        colState.setCellFactory(col -> new TableCell<>() {
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
                        case "CONCEPTION" -> {
                            badge.setText("Brouillon / Conception");
                            badge.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b;");
                        }
                        case "PRINTED" -> {
                            badge.setText("Prêt pour impression");
                            badge.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #92400e;");
                        }
                        case "ARCHIVED" -> {
                            badge.setText("Copies scannées / Noté");
                            badge.setStyle("-fx-background-color: #d1fae5; -fx-text-fill: #065f46;");
                        }
                        default -> badge.setText(item);
                    }
                    setGraphic(badge);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                }
            }
        });

        colQuestions.setCellValueFactory(cellData -> {
            int count = (cellData.getValue() != null && cellData.getValue().getQuestions() != null)
                    ? cellData.getValue().getQuestions().size() : 0;
            return new javafx.beans.property.SimpleObjectProperty<>(count);
        });

        // Colonne Actions intégrée dans chaque ligne
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnEdit = new Button("Éditer");
            private final Button btnDelete = new Button();
            private final HBox pane = new HBox(8, btnEdit, btnDelete);

            {
                pane.setAlignment(Pos.CENTER);

                btnEdit.getStyleClass().add("card-action-btn");
                btnEdit.setStyle("-fx-text-fill: #004faa; -fx-font-weight: bold;");
                FontIcon editIcon = new FontIcon(Feather.EDIT_3);
                editIcon.setIconSize(13);
                btnEdit.setGraphic(editIcon);
                btnEdit.setOnAction(e -> {
                    Evaluation item = getTableView().getItems().get(getIndex());
                    if (item != null) {
                        if (item.getState() == EvaluationState.CONCEPTION) {
                            mainLayoutController.openEvaluationBuilder(item.getId());
                        } else {
                            notificationService.showWarning(
                                    "Modification impossible",
                                    "Cette évaluation a déjà été imprimée ou archivée. Ses questions et paramètres ne peuvent plus être modifiés."
                            );
                        }
                    }
                });

                btnDelete.getStyleClass().add("card-action-btn");
                FontIcon trashIcon = new FontIcon(Feather.TRASH_2);
                trashIcon.setIconColor(javafx.scene.paint.Color.web("#ba1a1a"));
                trashIcon.setIconSize(13);
                btnDelete.setGraphic(trashIcon);
                btnDelete.setTooltip(new Tooltip("Supprimer l'évaluation"));
                btnDelete.setOnAction(e -> {
                    Evaluation item = getTableView().getItems().get(getIndex());
                    if (item != null) {
                        deleteEvaluation(item);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    Evaluation eval = getTableView().getItems().get(getIndex());
                    boolean isLocked = eval != null && eval.getState() != EvaluationState.CONCEPTION;
                    btnEdit.setDisable(isLocked);
                    if (isLocked) {
                        btnEdit.setTooltip(new Tooltip("Évaluation verrouillée (imprimée ou archivée) : modification impossible"));
                    } else {
                        btnEdit.setTooltip(new Tooltip("Ouvrir le concepteur de questions"));
                    }
                    setGraphic(pane);
                }
            }
        });

        // Double-clic sur une ligne pour ouvrir le concepteur (uniquement si en conception)
        evalTable.setRowFactory(tv -> {
            TableRow<Evaluation> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    Evaluation eval = row.getItem();
                    if (eval != null) {
                        if (eval.getState() == EvaluationState.CONCEPTION) {
                            mainLayoutController.openEvaluationBuilder(eval.getId());
                        } else {
                            notificationService.showWarning(
                                    "Modification impossible",
                                    "Cette évaluation a déjà été imprimée ou archivée. Ses questions et paramètres ne peuvent plus être modifiés."
                            );
                        }
                    }
                }
            });
            return row;
        });

        evalTable.setItems(evalList);
    }

    private void setupIcons() {
        if (btnNewEval != null) btnNewEval.setGraphic(new FontIcon(Feather.PLUS));
    }

    private void setupListeners() {
        evalTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                if (mainLayoutController != null) {
                    mainLayoutController.setActiveEvaluation(newVal);
                }
            } else {
                if (mainLayoutController != null) {
                    mainLayoutController.clearActiveEvaluation();
                }
            }
        });
    }

    public Evaluation getSelectedEvaluation() {
        return evalTable != null ? evalTable.getSelectionModel().getSelectedItem() : null;
    }

    public void setGroup(Group group) {
        this.currentGroup = group;
        if (mainLayoutController != null) {
            mainLayoutController.clearActiveEvaluation();
        }
        if (group != null) {
            loadEvaluations(group.getId());
        } else {
            evalList.clear();
        }
    }

    public void setCourse(Group course) {
        setGroup(course);
    }

    public Group getCurrentGroup() {
        return currentGroup;
    }

    public Group getCurrentCourse() {
        return getCurrentGroup();
    }

    public void selectGroup(String groupId) {
        if (groupId == null) return;
        Group g = groupService.getGroup(groupId);
        if (g != null) {
            setGroup(g);
        }
    }

    public void selectCourse(String courseId) {
        selectGroup(courseId);
    }

    public void loadEvaluations(String groupId) {
        if (groupId == null) {
            evalList.clear();
            return;
        }
        var evals = evaluationService.getEvaluationsByGroup(groupId).stream()
                .filter(e -> e.getState() == EvaluationState.CONCEPTION)
                .toList();
        evalList.setAll(evals);
    }

    @FXML
    public void onNewEvaluation() {
        if (currentGroup == null) {
            notificationService.showWarning("Sélection requise", "Veuillez d'abord sélectionner un groupe.");
            return;
        }

        Dialog<Evaluation> dialog = new Dialog<>();
        dialog.setTitle("Nouvelle Évaluation");
        dialog.setHeaderText("Création d'une évaluation pour " + currentGroup.getName());

        ButtonType btnCreateType = new ButtonType("Créer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnCreateType, ButtonType.CANCEL);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new javafx.geometry.Insets(16));

        TextField txtName = new TextField("Contrôle Continu 1");
        txtName.setPromptText("Ex : Contrôle Continu 1 ou Examen Final");
        txtName.setPrefWidth(300);

        ComboBox<String> comboType = new ComboBox<>();
        comboType.getItems().addAll(Evaluation.TYPE_CONTROLE_CONTINU, Evaluation.TYPE_FIN_MODULE);
        comboType.setValue(Evaluation.TYPE_CONTROLE_CONTINU);
        comboType.setMaxWidth(Double.MAX_VALUE);

        grid.add(new Label("Intitulé de l'évaluation * :"), 0, 0);
        grid.add(txtName, 1, 0);
        grid.add(new Label("Type d'évaluation * :"), 0, 1);
        grid.add(comboType, 1, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == btnCreateType && !txtName.getText().isBlank()) {
                return evaluationService.createEvaluation(currentGroup.getId(), txtName.getText().trim(), comboType.getValue());
            }
            return null;
        });

        Optional<Evaluation> result = dialog.showAndWait();
        result.ifPresent(created -> {
            loadEvaluations(currentGroup.getId());
            mainLayoutController.openEvaluationBuilder(created.getId());
        });
    }

    public void deleteEvaluation(Evaluation selected) {
        if (selected == null) return;

        String groupId = selected.groupId() != null ? selected.groupId()
                : (currentGroup != null ? currentGroup.getId() : null);

        if (!notificationService.confirm("Suppression", "Supprimer l'évaluation",
                "Êtes-vous sûr de vouloir supprimer définitivement « " + selected.getDesignation() + " » et tous ses documents associés ?")) {
            return;
        }

        try {
            evaluationService.deleteEvaluation(selected.getId());
            if (groupId != null) {
                loadEvaluations(groupId);
            }
            notificationService.showInfo("Supprimé", "L'évaluation a été supprimée avec succès.");
        } catch (Exception e) {
            log.error("Échec de la suppression de l'évaluation", e);
            notificationService.showError("Erreur", "Impossible de supprimer l'évaluation : " + e.getMessage());
        }
    }
}
