package tk.tahri.eval.desktop.ui.controllers;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
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
import java.util.ResourceBundle;

@Component
public class StudentsViewController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(StudentsViewController.class);

    @FXML private TextField searchField;
    @FXML private Button btnImportExcel;
    @FXML private Button btnNewStudent;

    // Formulaire Inline
    @FXML private VBox studentFormCard;
    @FXML private FontIcon formIcon;
    @FXML private Label lblFormTitle;
    @FXML private Button btnCloseForm;
    @FXML private TextField txtMatricule;
    @FXML private TextField txtLastName;
    @FXML private TextField txtFirstName;
    @FXML private Label lblFormError;
    @FXML private Button btnCancelForm;
    @FXML private Button btnSaveStudent;

    // Barre Confirmation Suppression Inline
    @FXML private HBox deleteConfirmBox;
    @FXML private Label lblConfirmDelete;
    @FXML private Button btnConfirmDelete;

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
    private String editingStudentId = null;
    private Enrollment studentToDelete = null;

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

        // Colonne Actions intégrée dans chaque ligne
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnEdit = new Button();
            private final Button btnDelete = new Button();
            private final HBox pane = new HBox(8, btnEdit, btnDelete);

            {
                pane.setAlignment(Pos.CENTER);

                btnEdit.getStyleClass().add("card-action-btn");
                FontIcon editIcon = new FontIcon(Feather.EDIT_2);
                editIcon.setIconSize(13);
                btnEdit.setGraphic(editIcon);
                btnEdit.setTooltip(new Tooltip("Modifier cet étudiant"));
                btnEdit.setOnAction(e -> {
                    Enrollment item = getTableView().getItems().get(getIndex());
                    if (item != null) {
                        startEditStudent(item);
                    }
                });

                btnDelete.getStyleClass().add("card-action-btn");
                FontIcon trashIcon = new FontIcon(Feather.TRASH_2);
                trashIcon.setIconColor(javafx.scene.paint.Color.web("#ba1a1a"));
                trashIcon.setIconSize(13);
                btnDelete.setGraphic(trashIcon);
                btnDelete.setTooltip(new Tooltip("Retirer cet étudiant"));
                btnDelete.setOnAction(e -> {
                    Enrollment item = getTableView().getItems().get(getIndex());
                    if (item != null) {
                        askDeleteStudent(item);
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
        if (btnImportExcel != null) btnImportExcel.setGraphic(new FontIcon(Feather.UPLOAD));
        if (btnExportExcel != null) btnExportExcel.setGraphic(new FontIcon(Feather.DOWNLOAD));
        if (btnSaveStudent != null) btnSaveStudent.setGraphic(new FontIcon(Feather.CHECK));
        if (btnCloseForm != null) btnCloseForm.setGraphic(new FontIcon(Feather.X));
    }

    private void setupListeners() {
        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> filterStudents(newVal));
        }
    }

    public void setGroup(Group group) {
        this.currentGroup = group;
        onCancelForm();
        onCancelDelete();
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
    }

    @FXML
    public void onNewStudent() {
        if (currentGroup == null) {
            showInlineNotification("Veuillez sélectionner un groupe d'abord.", true);
            return;
        }
        editingStudentId = null;
        lblFormTitle.setText("Ajouter un nouvel étudiant");
        formIcon.setIconLiteral("fth-user-plus");
        txtMatricule.clear();
        txtLastName.clear();
        txtFirstName.clear();
        lblFormError.setVisible(false);

        studentFormCard.setVisible(true);
        studentFormCard.setManaged(true);
        onCancelDelete();
        Platform.runLater(() -> txtMatricule.requestFocus());
    }

    public void startEditStudent(Enrollment selected) {
        if (selected == null) return;

        editingStudentId = selected.getId();
        lblFormTitle.setText("Modifier l'étudiant : " + selected.fullName());
        formIcon.setIconLiteral("fth-edit-2");
        txtMatricule.setText(selected.getMatriculation() != null ? selected.getMatriculation() : "");
        txtLastName.setText(selected.getLastName() != null ? selected.getLastName() : "");
        txtFirstName.setText(selected.getFirstName() != null ? selected.getFirstName() : "");
        lblFormError.setVisible(false);

        studentFormCard.setVisible(true);
        studentFormCard.setManaged(true);
        onCancelDelete();
        Platform.runLater(() -> txtLastName.requestFocus());
    }

    @FXML
    public void onSaveStudent() {
        if (currentGroup == null) return;

        String mat = txtMatricule.getText() != null ? txtMatricule.getText().trim() : "";
        String nom = txtLastName.getText() != null ? txtLastName.getText().trim() : "";
        String prenom = txtFirstName.getText() != null ? txtFirstName.getText().trim() : "";

        if (mat.isEmpty() || nom.isEmpty()) {
            lblFormError.setText("Le matricule et le nom de famille sont obligatoires.");
            lblFormError.setVisible(true);
            return;
        }

        try {
            if (editingStudentId == null) {
                groupService.addStudent(currentGroup.getId(), mat, prenom, nom);
                showInlineNotification("Étudiant « " + nom + " " + prenom + " » ajouté avec succès.", false);
            } else {
                groupService.updateStudent(editingStudentId, mat, prenom, nom);
                showInlineNotification("Coordonnées de l'étudiant mises à jour avec succès.", false);
            }
            onCancelForm();
            loadStudents(currentGroup.getId());
        } catch (Exception ex) {
            log.error("Erreur lors de l'enregistrement de l'étudiant", ex);
            lblFormError.setText(ex.getMessage());
            lblFormError.setVisible(true);
        }
    }

    @FXML
    public void onCancelForm() {
        editingStudentId = null;
        if (studentFormCard != null) {
            studentFormCard.setVisible(false);
            studentFormCard.setManaged(false);
        }
        if (lblFormError != null) {
            lblFormError.setVisible(false);
        }
    }

    public void askDeleteStudent(Enrollment selected) {
        if (selected == null) return;
        studentToDelete = selected;
        lblConfirmDelete.setText("Voulez-vous vraiment retirer « " + selected.fullName() + " » de ce groupe ?");
        deleteConfirmBox.setVisible(true);
        deleteConfirmBox.setManaged(true);
        onCancelForm();
    }

    @FXML
    public void onConfirmDelete() {
        if (studentToDelete != null && currentGroup != null) {
            String name = studentToDelete.fullName();
            groupService.removeStudent(studentToDelete.getId());
            showInlineNotification("L'étudiant « " + name + " » a été retiré du groupe.", false);
            studentToDelete = null;
            onCancelDelete();
            loadStudents(currentGroup.getId());
        }
    }

    @FXML
    public void onCancelDelete() {
        studentToDelete = null;
        if (deleteConfirmBox != null) {
            deleteConfirmBox.setVisible(false);
            deleteConfirmBox.setManaged(false);
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
