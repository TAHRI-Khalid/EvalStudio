package tk.tahri.eval.desktop.ui.controllers;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import tk.tahri.eval.desktop.model.Group;
import tk.tahri.eval.desktop.service.GroupService;
import tk.tahri.eval.desktop.service.ImageStorageService;
import tk.tahri.eval.desktop.ui.NotificationService;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class GroupsViewController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(GroupsViewController.class);

    public static final String[] THEME_COLORS = {
            "#216cd3", "#1f8e3f", "#e52592", "#e8710a", "#38adbc", "#9334e6", "#4285f4", "#5f6368"
    };
    public static final String DEFAULT_THEME = "#216cd3";

    @FXML private TextField searchField;
    @FXML private Button btnNewGroup;
    @FXML private FlowPane groupsFlowPane;
    @FXML private VBox emptyStateBox;

    // FXML compatibility aliases
    @FXML private Button btnNewCourse;
    @FXML private FlowPane coursesFlowPane;

    private final GroupService groupService;
    private final NotificationService notificationService;
    private final MainLayoutController mainLayoutController;
    private final ImageStorageService imageStorageService;

    private final ObservableList<Group> allGroups = FXCollections.observableArrayList();

    public GroupsViewController(GroupService groupService,
                                NotificationService notificationService,
                                @Lazy MainLayoutController mainLayoutController,
                                ImageStorageService imageStorageService) {
        this.groupService = groupService;
        this.notificationService = notificationService;
        this.mainLayoutController = mainLayoutController;
        this.imageStorageService = imageStorageService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Button newBtn = btnNewGroup != null ? btnNewGroup : btnNewCourse;
        if (newBtn != null) newBtn.setGraphic(new FontIcon(Feather.PLUS));

        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> renderCards());
        }
        loadGroups();
    }

    private FlowPane getTargetFlowPane() {
        return groupsFlowPane != null ? groupsFlowPane : coursesFlowPane;
    }

    public void loadGroups() {
        allGroups.setAll(groupService.getAllGroups());
        renderCards();
    }

    public void loadCourses() {
        loadGroups();
    }

    private void renderCards() {
        FlowPane targetPane = getTargetFlowPane();
        if (targetPane == null) return;
        targetPane.getChildren().clear();

        String query = (searchField != null && searchField.getText() != null) ? searchField.getText().trim().toLowerCase() : "";
        List<Group> filtered = allGroups.stream()
                .filter(g -> {
                    if (query.isEmpty()) return true;
                    return (g.getName() != null && g.getName().toLowerCase().contains(query)) ||
                           (g.getSection() != null && g.getSection().toLowerCase().contains(query)) ||
                           (g.getEtablissement() != null && g.getEtablissement().toLowerCase().contains(query));
                })
                .toList();

        if (filtered.isEmpty()) {
            if (emptyStateBox != null) {
                emptyStateBox.setVisible(true);
                emptyStateBox.setManaged(true);
            }
        } else {
            if (emptyStateBox != null) {
                emptyStateBox.setVisible(false);
                emptyStateBox.setManaged(false);
            }

            int colorIndex = 0;
            for (Group group : filtered) {
                String color = (group.getTheme() != null && !group.getTheme().isBlank())
                        ? group.getTheme()
                        : THEME_COLORS[Math.abs((group.getName() != null ? group.getName().hashCode() : colorIndex)) % THEME_COLORS.length];
                colorIndex++;
                targetPane.getChildren().add(createGroupCard(group, color));
            }
        }
    }

    private VBox createGroupCard(Group group, String themeColor) {
        VBox card = new VBox();
        card.getStyleClass().addAll("group-card", "course-card");

        // 1. Bannière supérieure colorée
        VBox banner = new VBox(6);
        banner.getStyleClass().addAll("group-banner", "course-banner");
        banner.setStyle("-fx-background-color: " + themeColor + "; -fx-background-radius: 12px 12px 0 0;");

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label lblTitle = new Label(group.getName() != null ? group.getName() : "Sans nom");
        lblTitle.getStyleClass().addAll("group-banner-title", "course-banner-title");
        lblTitle.setMaxWidth(200);
        lblTitle.setWrapText(true);
        HBox.setHgrow(lblTitle, Priority.ALWAYS);

        Label lblYear = new Label(group.getYear() != null && !group.getYear().isBlank() ? group.getYear() : "2025-2026");
        lblYear.getStyleClass().addAll("group-banner-year", "course-banner-year");

        topRow.getChildren().addAll(lblTitle, lblYear);
        banner.getChildren().add(topRow);

        // 2. Zone de transition avec macaron flottant (Avatar avec Logo ou initiale)
        StackPane avatarContainer = new StackPane();
        avatarContainer.setAlignment(Pos.TOP_RIGHT);
        avatarContainer.setPadding(new Insets(-22, 16, 0, 0));

        StackPane avatar = new StackPane();
        avatar.getStyleClass().addAll("group-avatar", "course-avatar");

        File logoFile = getLogoFile(group.getLogoUrl());
        if (logoFile != null) {
            try {
                Image img = new Image(logoFile.toURI().toString(), 44, 44, true, true);
                ImageView iv = new ImageView(img);
                iv.setFitWidth(44);
                iv.setFitHeight(44);
                Circle clip = new Circle(22, 22, 22);
                iv.setClip(clip);
                avatar.getChildren().add(iv);
            } catch (Exception ex) {
                log.warn("Impossible de charger le logo du groupe {}: {}", group.getName(), ex.getMessage());
                renderInitialLetter(avatar, group, themeColor);
            }
        } else {
            renderInitialLetter(avatar, group, themeColor);
        }

        avatarContainer.getChildren().add(avatar);

        // 3. Corps de la carte
        VBox body = new VBox(8);
        body.getStyleClass().addAll("group-body", "course-body");
        VBox.setVgrow(body, Priority.ALWAYS);

        HBox badgesRow = new HBox(6);
        badgesRow.setAlignment(Pos.CENTER_LEFT);
        if (group.getSection() != null && !group.getSection().isBlank()) {
            Label badgeSection = new Label(group.getSection());
            badgeSection.getStyleClass().add("tag-badge");
            badgesRow.getChildren().add(badgeSection);
        }
        if (group.getYear() != null && !group.getYear().isBlank()) {
            Label badgeYear = new Label(group.getYear());
            badgeYear.getStyleClass().add("tag-badge");
            badgesRow.getChildren().add(badgeYear);
        }

        HBox instRow = new HBox(6);
        instRow.setAlignment(Pos.CENTER_LEFT);
        FontIcon instIcon = new FontIcon(Feather.MAP_PIN);
        instIcon.setIconSize(12);
        instIcon.setIconColor(javafx.scene.paint.Color.web("#424753"));
        Label lblInst = new Label(group.getEtablissement() != null && !group.getEtablissement().isBlank() ? group.getEtablissement() : "Aucun établissement");
        lblInst.getStyleClass().addAll("group-etablissement", "course-institute");
        instRow.getChildren().addAll(instIcon, lblInst);

        body.getChildren().addAll(badgesRow, instRow);

        // 4. Pied de carte avec boutons d'actions
        HBox footer = new HBox(4);
        footer.getStyleClass().addAll("group-footer", "course-footer");
        footer.setAlignment(Pos.CENTER_LEFT);

        Button btnStudents = new Button("Étudiants");
        btnStudents.getStyleClass().add("card-action-btn");
        btnStudents.setGraphic(new FontIcon(Feather.USERS));
        btnStudents.setOnAction(e -> {
            e.consume();
            mainLayoutController.showStudentsForGroup(group.getId());
        });

        Button btnEvals = new Button("Évaluations");
        btnEvals.getStyleClass().add("card-action-btn");
        btnEvals.setStyle("-fx-text-fill: #004faa; -fx-font-weight: bold;");
        btnEvals.setGraphic(new FontIcon(Feather.FILE_TEXT));
        btnEvals.setOnAction(e -> {
            e.consume();
            mainLayoutController.showEvaluationsForGroup(group.getId());
        });

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnEdit = new Button();
        btnEdit.getStyleClass().add("card-action-btn");
        btnEdit.setGraphic(new FontIcon(Feather.EDIT_2));
        btnEdit.setTooltip(new Tooltip("Modifier le groupe"));
        btnEdit.setOnAction(e -> {
            e.consume();
            showGroupDialog(group);
        });

        Button btnDelete = new Button();
        btnDelete.getStyleClass().add("card-action-btn");
        FontIcon trashIcon = new FontIcon(Feather.TRASH_2);
        trashIcon.setIconColor(javafx.scene.paint.Color.web("#ba1a1a"));
        btnDelete.setGraphic(trashIcon);
        btnDelete.setTooltip(new Tooltip("Supprimer le groupe"));
        btnDelete.setOnAction(e -> {
            e.consume();
            if (notificationService.confirm("Suppression", "Supprimer le groupe",
                    "Êtes-vous sûr de vouloir supprimer « " + group.getName() + " » et toutes ses inscriptions ?")) {
                groupService.deleteGroup(group.getId());
                loadGroups();
            }
        });

        footer.getChildren().addAll(btnStudents, btnEvals, spacer, btnEdit, btnDelete);

        card.getChildren().addAll(banner, avatarContainer, body, footer);

        // Double clic sur la carte pour ouvrir directement les évaluations
        card.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                mainLayoutController.showEvaluationsForGroup(group.getId());
            }
        });

        return card;
    }

    private void renderInitialLetter(StackPane avatar, Group group, String themeColor) {
        String initial = (group.getName() != null && !group.getName().isBlank())
                ? group.getName().substring(0, 1).toUpperCase()
                : "G";
        Label lblInitial = new Label(initial);
        lblInitial.getStyleClass().addAll("group-avatar-text", "course-avatar-text");
        lblInitial.setStyle("-fx-text-fill: " + themeColor + ";");
        avatar.getChildren().add(lblInitial);
    }

    public File getLogoFile(String logoUrl) {
        if (logoUrl == null || logoUrl.isBlank()) return null;
        try {
            Path sm = imageStorageService.getImagePath(logoUrl, ImageStorageService.ImageFolder.GROUP_LOGOS, false);
            if (sm != null && Files.exists(sm)) return sm.toFile();
            Path xm = imageStorageService.getImagePath(logoUrl, ImageStorageService.ImageFolder.GROUP_LOGOS, true);
            if (xm != null && Files.exists(xm)) return xm.toFile();
            File direct = new File(logoUrl);
            if (direct.exists()) return direct;
        } catch (Exception ex) {
            log.warn("Impossible de résoudre le fichier logo : {}", logoUrl, ex);
        }
        return null;
    }

    @FXML
    public void onNewGroup() {
        showGroupDialog(null);
    }

    @FXML
    public void onNewCourse() {
        onNewGroup();
    }

    private void showGroupDialog(Group existing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Nouveau groupe" : "Modifier le groupe");
        dialog.setHeaderText(existing == null ? "Créez un groupe pour vos étudiants" : "Mettez à jour les informations du groupe");

        ButtonType btnSaveType = new ButtonType(existing == null ? "Créer le groupe" : "Enregistrer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnSaveType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        grid.setStyle("-fx-padding: 20;");
        grid.setPrefWidth(540);

        TextField txtName = new TextField(existing != null ? existing.getName() : "");
        txtName.setPromptText("Ex : Informatique Groupe A");
        txtName.setStyle("-fx-background-radius: 6px; -fx-padding: 8;");

        TextField txtSection = new TextField(existing != null ? existing.getSection() : "");
        txtSection.setPromptText("Ex : II-BDCC");
        txtSection.setStyle("-fx-background-radius: 6px; -fx-padding: 8;");

        TextField txtYear = new TextField(existing != null && existing.getYear() != null ? existing.getYear() : "2025-2026");
        txtYear.setPromptText("Ex : 2025-2026");
        txtYear.setStyle("-fx-background-radius: 6px; -fx-padding: 8;");

        TextField txtEtablissement = new TextField(existing != null ? existing.getEtablissement() : "");
        txtEtablissement.setPromptText("Ex : ENSET Mohammedia");
        txtEtablissement.setStyle("-fx-background-radius: 6px; -fx-padding: 8;");

        // 1. Sélecteur de couleur de thème
        String initialTheme = (existing != null && existing.getTheme() != null && !existing.getTheme().isBlank())
                ? existing.getTheme()
                : DEFAULT_THEME;
        SimpleStringProperty selectedTheme = new SimpleStringProperty(initialTheme);

        HBox themeSwatches = new HBox(8);
        themeSwatches.setAlignment(Pos.CENTER_LEFT);
        List<Button> swatchButtons = new ArrayList<>();

        for (String themeHex : THEME_COLORS) {
            Button swatch = new Button();
            swatch.getStyleClass().add("color-swatch");
            swatch.setStyle("-fx-background-color: " + themeHex + ";");

            Runnable updateSwatchVisual = () -> {
                if (themeHex.equalsIgnoreCase(selectedTheme.get())) {
                    if (!swatch.getStyleClass().contains("color-swatch-selected")) {
                        swatch.getStyleClass().add("color-swatch-selected");
                    }
                    FontIcon check = new FontIcon(Feather.CHECK);
                    check.setIconColor(javafx.scene.paint.Color.WHITE);
                    check.setIconSize(14);
                    swatch.setGraphic(check);
                } else {
                    swatch.getStyleClass().remove("color-swatch-selected");
                    swatch.setGraphic(null);
                }
            };

            swatch.setOnAction(e -> {
                selectedTheme.set(themeHex);
                swatchButtons.forEach(b -> ((Runnable) b.getUserData()).run());
            });

            swatch.setUserData(updateSwatchVisual);
            swatchButtons.add(swatch);
            updateSwatchVisual.run();
            themeSwatches.getChildren().add(swatch);
        }

        // 2. Logo du groupe
        AtomicReference<File> chosenLogoFile = new AtomicReference<>(null);
        AtomicBoolean logoRemoved = new AtomicBoolean(false);

        StackPane logoPreview = new StackPane();
        logoPreview.getStyleClass().add("dialog-logo-preview");

        Button btnClearLogo = new Button("Retirer");
        btnClearLogo.setStyle("-fx-text-fill: #ba1a1a;");

        Runnable refreshLogoPreview = () -> {
            logoPreview.getChildren().clear();
            if (logoRemoved.get()) {
                FontIcon ph = new FontIcon(Feather.IMAGE);
                ph.setIconColor(javafx.scene.paint.Color.web("#727782"));
                ph.setIconSize(24);
                logoPreview.getChildren().add(ph);
                btnClearLogo.setDisable(true);
            } else if (chosenLogoFile.get() != null) {
                try {
                    Image img = new Image(chosenLogoFile.get().toURI().toString(), 60, 60, true, true);
                    ImageView iv = new ImageView(img);
                    iv.setFitWidth(60);
                    iv.setFitHeight(60);
                    Rectangle clip = new Rectangle(60, 60);
                    clip.setArcWidth(20);
                    clip.setArcHeight(20);
                    iv.setClip(clip);
                    logoPreview.getChildren().add(iv);
                    btnClearLogo.setDisable(false);
                } catch (Exception ex) {
                    FontIcon ph = new FontIcon(Feather.IMAGE);
                    ph.setIconColor(javafx.scene.paint.Color.web("#727782"));
                    ph.setIconSize(24);
                    logoPreview.getChildren().add(ph);
                    btnClearLogo.setDisable(true);
                }
            } else if (existing != null && existing.getLogoUrl() != null && !existing.getLogoUrl().isBlank()) {
                File currentFile = getLogoFile(existing.getLogoUrl());
                if (currentFile != null) {
                    try {
                        Image img = new Image(currentFile.toURI().toString(), 60, 60, true, true);
                        ImageView iv = new ImageView(img);
                        iv.setFitWidth(60);
                        iv.setFitHeight(60);
                        Rectangle clip = new Rectangle(60, 60);
                        clip.setArcWidth(20);
                        clip.setArcHeight(20);
                        iv.setClip(clip);
                        logoPreview.getChildren().add(iv);
                        btnClearLogo.setDisable(false);
                    } catch (Exception ex) {
                        FontIcon ph = new FontIcon(Feather.IMAGE);
                        ph.setIconColor(javafx.scene.paint.Color.web("#727782"));
                        ph.setIconSize(24);
                        logoPreview.getChildren().add(ph);
                        btnClearLogo.setDisable(true);
                    }
                } else {
                    FontIcon ph = new FontIcon(Feather.IMAGE);
                    ph.setIconColor(javafx.scene.paint.Color.web("#727782"));
                    ph.setIconSize(24);
                    logoPreview.getChildren().add(ph);
                    btnClearLogo.setDisable(true);
                }
            } else {
                FontIcon ph = new FontIcon(Feather.IMAGE);
                ph.setIconColor(javafx.scene.paint.Color.web("#727782"));
                ph.setIconSize(24);
                logoPreview.getChildren().add(ph);
                btnClearLogo.setDisable(true);
            }
        };

        refreshLogoPreview.run();

        Button btnChooseLogo = new Button("Choisir un fichier...");
        btnChooseLogo.setGraphic(new FontIcon(Feather.UPLOAD));
        btnChooseLogo.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Sélectionner le logo du groupe");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Images (*.png, *.jpg, *.jpeg, *.webp)", "*.png", "*.jpg", "*.jpeg", "*.webp")
            );
            File f = fileChooser.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (f != null) {
                chosenLogoFile.set(f);
                logoRemoved.set(false);
                refreshLogoPreview.run();
            }
        });

        btnClearLogo.setOnAction(e -> {
            chosenLogoFile.set(null);
            logoRemoved.set(true);
            refreshLogoPreview.run();
        });

        HBox logoRow = new HBox(12, logoPreview, btnChooseLogo, btnClearLogo);
        logoRow.setAlignment(Pos.CENTER_LEFT);

        grid.add(new Label("Nom du groupe * :"), 0, 0); grid.add(txtName, 1, 0);
        grid.add(new Label("Filière * :"), 0, 1); grid.add(txtSection, 1, 1);
        grid.add(new Label("Année universitaire :"), 0, 2); grid.add(txtYear, 1, 2);
        grid.add(new Label("Établissement :"), 0, 3); grid.add(txtEtablissement, 1, 3);
        grid.add(new Label("Couleur du thème * :"), 0, 4); grid.add(themeSwatches, 1, 4);
        grid.add(new Label("Logo du groupe :"), 0, 5); grid.add(logoRow, 1, 5);

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> clickResult = dialog.showAndWait();
        if (clickResult.isPresent() && clickResult.get() == btnSaveType) {
            if (txtName.getText().isBlank()) {
                notificationService.showWarning("Champ requis", "Le nom du groupe est obligatoire.");
                return;
            }
            Group g = existing != null ? existing : new Group();
            g.setName(txtName.getText().trim());
            g.setSection(txtSection.getText().trim());
            g.setYear(txtYear.getText().trim());
            g.setEtablissement(txtEtablissement.getText().trim());
            g.setTheme(selectedTheme.get());

            if (logoRemoved.get()) {
                g.setLogoUrl(null);
                groupService.saveGroup(g, null);
            } else if (chosenLogoFile.get() != null) {
                groupService.saveGroup(g, chosenLogoFile.get());
            } else {
                groupService.saveGroup(g, null);
            }

            loadGroups();
        }
    }
}
