package tk.tahri.eval.desktop.ui.controllers;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import tk.tahri.eval.desktop.model.Group;
import tk.tahri.eval.desktop.model.Enrollment;
import tk.tahri.eval.desktop.service.GroupService;
import tk.tahri.eval.desktop.ui.NotificationService;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

@Component
public class StudentsViewController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(StudentsViewController.class);

    @FXML private TextField searchField;
    @FXML private Button btnImportExcel;
    @FXML private Button btnNewStudent;

    // Notification Inline
    @FXML private HBox inlineNotificationBox;
    @FXML private FontIcon inlineNotificationIcon;
    @FXML private Label lblInlineNotification;

    // Informations et Export
    @FXML private Label lblHeaderStudentCount;
    @FXML private Label lblStudentCount;
    @FXML private Button btnExportExcel;

    // Tableau des Étudiants
    @FXML private TableView<Enrollment> studentsTable;
    @FXML private TableColumn<Enrollment, String> colMatricule;
    @FXML private TableColumn<Enrollment, String> colLastName;
    @FXML private TableColumn<Enrollment, String> colFirstName;
    @FXML private TableColumn<Enrollment, String> colFullName;
    @FXML private TableColumn<Enrollment, String> colRole;
    @FXML private TableColumn<Enrollment, Void> colActions;

    private final GroupService groupService;
    private final NotificationService notificationService;
    private final MainLayoutController mainLayoutController;

    private final ObservableList<Enrollment> studentList = FXCollections.observableArrayList();
    private final List<Enrollment> fullStudentList = new ArrayList<>();
    private Group currentGroup;

    public StudentsViewController(GroupService groupService,
                                  NotificationService notificationService,
                                  @Lazy MainLayoutController mainLayoutController) {
        this.groupService = groupService;
        this.notificationService = notificationService;
        this.mainLayoutController = mainLayoutController;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTable();
        setupIcons();
        setupListeners();
    }

    private void setupTable() {
        colMatricule.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getMatriculation() != null ? data.getValue().getMatriculation() : ""
        ));
        colLastName.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getLastName() != null ? data.getValue().getLastName() : ""
        ));
        colFirstName.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getFirstName() != null ? data.getValue().getFirstName() : ""
        ));
        colFullName.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().fullName()
        ));
        colRole.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getRole() != null ? data.getValue().getRole().name() : "STUDENT"
        ));

        // Style badge du rôle
        colRole.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item.equals("TEACHER") ? "Enseignant" : "Inscrit");
                    badge.setStyle(item.equals("TEACHER")
                            ? "-fx-background-color: #fee2e2; -fx-text-fill: #b91c1c; -fx-background-radius: 12px; -fx-padding: 2 10;"
                            : "-fx-background-color: #e6f7ec; -fx-text-fill: #1b8744; -fx-background-radius: 12px; -fx-padding: 2 10; -fx-font-weight: 600; -fx-font-size: 11px;");
                    setGraphic(badge);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                }
            }
        });

        // Colonne Actions intégrée dans chaque ligne avec menu kebab
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnMenu = new Button();

            {
                btnMenu.getStyleClass().add("card-action-btn");
                FontIcon kebabIcon = new FontIcon(Feather.MORE_VERTICAL);
                kebabIcon.setIconSize(15);
                btnMenu.setGraphic(kebabIcon);
                btnMenu.setTooltip(new Tooltip("Actions"));

                btnMenu.setOnMousePressed(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size()) {
                        getTableView().getSelectionModel().select(idx);
                    }
                });

                btnMenu.setOnAction(e -> {
                    e.consume();
                    int idx = getIndex();
                    if (idx < 0 || idx >= getTableView().getItems().size()) return;
                    getTableView().getSelectionModel().select(idx);
                    Enrollment item = getTableView().getItems().get(idx);
                    if (item == null) return;

                    ContextMenu menu = new ContextMenu();

                    MenuItem itemEdit = new MenuItem("Modifier l'étudiant");
                    FontIcon editIcon = new FontIcon(Feather.EDIT_2);
                    editIcon.setIconSize(13);
                    itemEdit.setGraphic(editIcon);
                    itemEdit.setOnAction(ev -> {
                        ev.consume();
                        startEditStudent(item);
                    });

                    MenuItem itemDelete = new MenuItem("Retirer l'étudiant");
                    FontIcon trashIcon = new FontIcon(Feather.TRASH_2);
                    trashIcon.setIconColor(javafx.scene.paint.Color.web("#ba1a1a"));
                    trashIcon.setIconSize(13);
                    itemDelete.setGraphic(trashIcon);
                    itemDelete.setOnAction(ev -> {
                        ev.consume();
                        askDeleteStudent(item);
                    });

                    menu.getItems().addAll(itemEdit, itemDelete);
                    menu.show(btnMenu, Side.BOTTOM, 0, 0);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                } else {
                    setGraphic(btnMenu);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        // Double-clic sur une ligne pour modifier directement l'étudiant
        studentsTable.setRowFactory(tv -> {
            TableRow<Enrollment> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    startEditStudent(row.getItem());
                }
            });
            return row;
        });

        studentsTable.setItems(studentList);
    }

    private void setupIcons() {
        if (btnNewStudent != null) btnNewStudent.setGraphic(new FontIcon(Feather.USER_PLUS));
        if (btnImportExcel != null) {
            btnImportExcel.setGraphic(new FontIcon(Feather.UPLOAD));
            btnImportExcel.setTooltip(new Tooltip("Importer des étudiants depuis un fichier Excel (.xlsx)"));
        }
        if (btnExportExcel != null) {
            btnExportExcel.setGraphic(new FontIcon(Feather.DOWNLOAD));
            btnExportExcel.setTooltip(new Tooltip("Exporter la liste des étudiants en fichier Excel (.xlsx)"));
        }
    }

    private void setupListeners() {
        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> filterStudents(newVal));
        }
    }

    public void setGroup(Group group) {
        this.currentGroup = group;
        if (group != null) {
            loadStudents(group.getId());
        } else {
            studentList.clear();
            fullStudentList.clear();
            updateCountLabel(0);
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

    public void loadStudents(String groupId) {
        if (groupId == null) {
            studentList.clear();
            fullStudentList.clear();
            updateCountLabel(0);
            return;
        }
        var list = groupService.getStudents(groupId);
        fullStudentList.clear();
        fullStudentList.addAll(list);
        filterStudents(searchField != null ? searchField.getText() : null);
    }

    private void filterStudents(String query) {
        if (query == null || query.isBlank()) {
            studentList.setAll(fullStudentList);
        } else {
            String lower = query.trim().toLowerCase();
            List<Enrollment> filtered = fullStudentList.stream()
                    .filter(e -> (e.fullName() != null && e.fullName().toLowerCase().contains(lower)) ||
                                 (e.getMatriculation() != null && e.getMatriculation().toLowerCase().contains(lower)) ||
                                 (e.getLastName() != null && e.getLastName().toLowerCase().contains(lower)) ||
                                 (e.getFirstName() != null && e.getFirstName().toLowerCase().contains(lower)))
                    .toList();
            studentList.setAll(filtered);
        }
        updateCountLabel(studentList.size());
    }

    private void updateCountLabel(int count) {
        String text = count + " étudiant" + (count > 1 ? "s" : "");
        if (lblStudentCount != null) {
            lblStudentCount.setText(text + " inscrit" + (count > 1 ? "s" : ""));
        }
        if (lblHeaderStudentCount != null) {
            lblHeaderStudentCount.setText(text);
        }
        if (btnExportExcel != null) {
            btnExportExcel.setDisable(count == 0);
        }
    }

    @FXML
    public void onNewStudent() {
        if (currentGroup == null) {
            showInlineNotification("Veuillez sélectionner un groupe d'abord.", true);
            return;
        }
        showStudentDialog(null);
    }

    public void startEditStudent(Enrollment selected) {
        if (selected == null) return;
        showStudentDialog(selected);
    }

    private void showStudentDialog(Enrollment existing) {
        if (currentGroup == null) {
            showInlineNotification("Veuillez sélectionner un groupe d'abord.", true);
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Nouvel étudiant" : "Modifier l'étudiant");
        dialog.setHeaderText(existing == null
                ? "Ajoutez un étudiant au groupe « " + currentGroup.getName() + " »"
                : "Modifier les informations de l'étudiant");

        ButtonType btnSaveType = new ButtonType(existing == null ? "Ajouter l'étudiant" : "Enregistrer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnSaveType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        grid.setStyle("-fx-padding: 20;");
        grid.setPrefWidth(480);

        TextField txtMat = new TextField(existing != null && existing.getMatriculation() != null ? existing.getMatriculation() : "");
        txtMat.setPromptText("Ex : ETU123456");
        txtMat.setStyle("-fx-background-radius: 6px; -fx-padding: 8;");

        TextField txtNom = new TextField(existing != null && existing.getLastName() != null ? existing.getLastName() : "");
        txtNom.setPromptText("Ex : ALAMI");
        txtNom.setStyle("-fx-background-radius: 6px; -fx-padding: 8;");

        TextField txtPrenom = new TextField(existing != null && existing.getFirstName() != null ? existing.getFirstName() : "");
        txtPrenom.setPromptText("Ex : Mohammed");
        txtPrenom.setStyle("-fx-background-radius: 6px; -fx-padding: 8;");

        grid.add(new Label("Matricule / Code * :"), 0, 0); grid.add(txtMat, 1, 0);
        grid.add(new Label("Nom de famille * :"), 0, 1);    grid.add(txtNom, 1, 1);
        grid.add(new Label("Prénom :"), 0, 2);             grid.add(txtPrenom, 1, 2);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setMinWidth(140);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col1, col2);

        dialog.getDialogPane().setContent(grid);

        // Validation dynamique : désactiver le bouton d'enregistrement si Matricule ou Nom est vide
        Node saveBtn = dialog.getDialogPane().lookupButton(btnSaveType);
        if (saveBtn != null) {
            Runnable checkValid = () -> {
                boolean valid = !txtMat.getText().trim().isEmpty() && !txtNom.getText().trim().isEmpty();
                saveBtn.setDisable(!valid);
            };
            txtMat.textProperty().addListener((obs, oldV, newV) -> checkValid.run());
            txtNom.textProperty().addListener((obs, oldV, newV) -> checkValid.run());
            checkValid.run();
        }

        Platform.runLater(() -> {
            if (existing == null) {
                txtMat.requestFocus();
            } else {
                txtNom.requestFocus();
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == btnSaveType) {
            String matricule = txtMat.getText().trim();
            String nom = txtNom.getText().trim();
            String prenom = txtPrenom.getText().trim();

            if (matricule.isEmpty() || nom.isEmpty()) {
                notificationService.showWarning("Champ requis", "Le matricule et le nom de famille sont obligatoires.");
                return;
            }

            try {
                if (existing == null) {
                    groupService.addStudent(currentGroup.getId(), matricule, prenom, nom);
                    showInlineNotification("Étudiant « " + nom + " " + prenom + " » ajouté avec succès.", false);
                } else {
                    groupService.updateStudent(existing.getId(), matricule, prenom, nom);
                    showInlineNotification("Coordonnées de l'étudiant « " + nom + " " + prenom + " » mises à jour avec succès.", false);
                }
                loadStudents(currentGroup.getId());
            } catch (Exception ex) {
                log.error("Erreur lors de l'enregistrement de l'étudiant", ex);
                notificationService.showError("Erreur", "Impossible d'enregistrer l'étudiant : " + ex.getMessage());
            }
        }
    }

    public void askDeleteStudent(Enrollment selected) {
        if (selected == null || currentGroup == null) return;
        boolean confirmed = notificationService.confirm(
                "Supprimer l'étudiant",
                "Confirmation de suppression",
                "Êtes-vous sûr de vouloir retirer « " + selected.fullName() + " » de ce groupe ?"
        );
        if (confirmed) {
            try {
                String name = selected.fullName();
                groupService.removeStudent(selected.getId());
                showInlineNotification("L'étudiant « " + name + " » a été retiré du groupe.", false);
                loadStudents(currentGroup.getId());
            } catch (Exception ex) {
                log.error("Erreur lors de la suppression de l'étudiant", ex);
                notificationService.showError("Erreur", "Impossible de retirer l'étudiant : " + ex.getMessage());
            }
        }
    }

    @FXML
    public void onImportExcel() {
        if (currentGroup == null) {
            showInlineNotification("Veuillez sélectionner un groupe avant d'importer.", true);
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Sélectionner la liste des étudiants (Excel)");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Fichiers Excel (*.xlsx, *.xls)", "*.xlsx", "*.xls")
        );
        File file = fileChooser.showOpenDialog(studentsTable.getScene().getWindow());
        if (file != null) {
            try {
                int count = groupService.importStudentsFromExcel(currentGroup.getId(), file);
                showInlineNotification("Importation réussie : " + count + " étudiant(s) synchronisé(s).", false);
                loadStudents(currentGroup.getId());
            } catch (Exception ex) {
                log.error("Échec de l'importation Excel", ex);
                showInlineNotification("Erreur lors de la lecture du fichier Excel : " + ex.getMessage(), true);
            }
        }
    }

    @FXML
    public void onExportExcel() {
        if (currentGroup == null || fullStudentList.isEmpty()) {
            showInlineNotification("Aucun étudiant à exporter pour ce groupe.", true);
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Enregistrer la liste des étudiants");
        String safeName = currentGroup.getName() != null ? currentGroup.getName().replaceAll("[^a-zA-Z0-9_-]", "_") : "Groupe";
        fileChooser.setInitialFileName("Etudiants_" + safeName + ".xlsx");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Fichier Excel (*.xlsx)", "*.xlsx")
        );

        File target = fileChooser.showSaveDialog(studentsTable.getScene().getWindow());
        if (target != null) {
            try {
                groupService.exportStudentsToExcel(currentGroup.getId(), target);
                showInlineNotification("Liste des étudiants exportée avec succès sous : " + target.getName(), false);
            } catch (Exception ex) {
                log.error("Échec de l'exportation Excel", ex);
                showInlineNotification("Erreur lors de l'exportation : " + ex.getMessage(), true);
            }
        }
    }

    private void showInlineNotification(String message, boolean isError) {
        if (lblInlineNotification != null && inlineNotificationBox != null) {
            lblInlineNotification.setText(message);
            if (isError) {
                inlineNotificationBox.setStyle("-fx-background-color: #ffdad6; -fx-background-radius: 8px; -fx-padding: 8 16;");
                lblInlineNotification.setStyle("-fx-text-fill: #ba1a1a; -fx-font-weight: 600; -fx-font-size: 12px;");
                inlineNotificationIcon.setIconLiteral("fth-alert-triangle");
                inlineNotificationIcon.setIconColor(javafx.scene.paint.Color.web("#ba1a1a"));
            } else {
                inlineNotificationBox.setStyle("-fx-background-color: #e6f7ec; -fx-background-radius: 8px; -fx-padding: 8 16;");
                lblInlineNotification.setStyle("-fx-text-fill: #00210e; -fx-font-weight: 600; -fx-font-size: 12px;");
                inlineNotificationIcon.setIconLiteral("fth-check-circle");
                inlineNotificationIcon.setIconColor(javafx.scene.paint.Color.web("#1b8744"));
            }
            inlineNotificationBox.setVisible(true);
            inlineNotificationBox.setManaged(true);
        }
    }

    @FXML
    public void hideInlineNotification() {
        if (inlineNotificationBox != null) {
            inlineNotificationBox.setVisible(false);
            inlineNotificationBox.setManaged(false);
        }
    }
}
