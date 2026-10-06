package tk.tahri.eval.desktop.ui.controllers;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import tk.tahri.eval.desktop.model.Group;
import tk.tahri.eval.desktop.model.Evaluation;
import tk.tahri.eval.desktop.service.GroupService;
import tk.tahri.eval.desktop.ui.NotificationService;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

@Component
public class MainLayoutController implements Initializable, NotificationService.ToastPresenter {

    private static final Logger log = LoggerFactory.getLogger(MainLayoutController.class);

    @FXML private Button btnNavBack;
    @FXML private Button btnAllGroups;
    @FXML private Button btnAllCourses; // FXML compatibility
    @FXML private Separator headerSeparator;
    @FXML private HBox groupBannerBox;
    @FXML private HBox courseBannerBox; // FXML compatibility
    @FXML private HBox bannerAvatarBox;
    @FXML private Label lblGroupTitle;
    @FXML private Label lblCourseTitle; // FXML compatibility
    @FXML private Label lblGroupSubtitle;
    @FXML private Label lblCourseSubtitle; // FXML compatibility

    @FXML private Separator evalSeparator;
    @FXML private HBox evaluationBannerBox;
    @FXML private HBox evalAvatarBox;
    @FXML private Label lblEvaluationTitle;
    @FXML private Label lblEvaluationSubtitle;

    @FXML private Button btnSettings;
    @FXML private StackPane contentArea;
    @FXML private Label statusLabel;

    private final java.util.Deque<Runnable> navigationHistory = new java.util.ArrayDeque<>();
    private boolean isNavigatingBack = false;

    private final ApplicationContext applicationContext;
    private final GroupService groupService;
    private final NotificationService notificationService;

    private Group activeGroup;
    private Evaluation activeEvaluation;
    private Node workspaceNode;
    private GroupWorkspaceViewController workspaceController;

    public MainLayoutController(ApplicationContext applicationContext,
                                GroupService groupService,
                                NotificationService notificationService) {
        this.applicationContext = applicationContext;
        this.groupService = groupService;
        this.notificationService = notificationService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (notificationService != null) {
            notificationService.setToastPresenter(this);
        }
        setupIcons();
        setupShortcutsListener();
        showGroupsView();
    }

    private Button getGroupsButton() {
        return btnAllGroups != null ? btnAllGroups : btnAllCourses;
    }

    private HBox getBannerBox() {
        return groupBannerBox != null ? groupBannerBox : courseBannerBox;
    }

    private Label getTitleLabel() {
        return lblGroupTitle != null ? lblGroupTitle : lblCourseTitle;
    }

    private Label getSubtitleLabel() {
        return lblGroupSubtitle != null ? lblGroupSubtitle : lblCourseSubtitle;
    }

    private void setupIcons() {
        if (btnNavBack != null) btnNavBack.setGraphic(new FontIcon(Feather.ARROW_LEFT));
        Button btn = getGroupsButton();
        if (btn != null) btn.setGraphic(new FontIcon(Feather.GRID));
        if (btnSettings != null) btnSettings.setGraphic(new FontIcon(Feather.SETTINGS));
    }

    public void pushBackAction(Runnable backAction) {
        if (isNavigatingBack || backAction == null) return;
        navigationHistory.push(backAction);
        updateNavBackButtonState();
    }

    public void clearNavigationHistory() {
        navigationHistory.clear();
        updateNavBackButtonState();
    }

    private void updateNavBackButtonState() {
        boolean canGoBack = !navigationHistory.isEmpty();
        if (btnNavBack != null) {
            btnNavBack.setVisible(canGoBack);
            btnNavBack.setManaged(canGoBack);
        }
    }

    @FXML
    public void navigateBack() {
        if (navigationHistory.isEmpty()) {
            handleEscapeFallback();
            return;
        }
        isNavigatingBack = true;
        try {
            Runnable action = navigationHistory.pop();
            updateNavBackButtonState();
            if (action != null) {
                action.run();
            }
        } finally {
            isNavigatingBack = false;
        }
    }

    public void setBannerVisible(boolean visible) {
        HBox banner = getBannerBox();
        if (banner != null) {
            banner.setVisible(visible);
            banner.setManaged(visible);
        }
        if (headerSeparator != null) {
            headerSeparator.setVisible(visible);
            headerSeparator.setManaged(visible);
        }
        boolean showEval = visible && activeEvaluation != null;
        if (evalSeparator != null) {
            evalSeparator.setVisible(showEval);
            evalSeparator.setManaged(showEval);
        }
        if (evaluationBannerBox != null) {
            evaluationBannerBox.setVisible(showEval);
            evaluationBannerBox.setManaged(showEval);
        }
    }

    public void setActiveGroup(Group group, boolean navigateToWorkspace) {
        this.activeGroup = group;
        clearActiveEvaluation();
        Label lblTitle = getTitleLabel();
        if (group != null) {
            setBannerVisible(true);
            if (lblTitle != null) {
                lblTitle.setText(group.getName() != null ? group.getName() : "Sans nom");
            }
            updateGroupSubtitle();
            if (bannerAvatarBox != null) {
                String color = (group.getTheme() != null && !group.getTheme().isBlank()) ? group.getTheme() : "#004faa";
                bannerAvatarBox.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 8px; -fx-min-width: 34px; -fx-min-height: 34px; -fx-max-width: 34px; -fx-max-height: 34px;");
            }
            if (navigateToWorkspace) {
                showWorkspace(group, 0);
            }
        } else {
            setBannerVisible(false);
            if (lblTitle != null) lblTitle.setText("Sélectionnez un groupe");
            updateGroupSubtitle();
            if (bannerAvatarBox != null) {
                bannerAvatarBox.setStyle("-fx-background-color: #004faa; -fx-background-radius: 8px; -fx-min-width: 34px; -fx-min-height: 34px; -fx-max-width: 34px; -fx-max-height: 34px;");
            }
        }
    }

    public void setActiveCourse(Group course, boolean navigateToWorkspace) {
        setActiveGroup(course, navigateToWorkspace);
    }

    public void setActiveEvaluation(Evaluation evaluation) {
        this.activeEvaluation = evaluation;
        if (evaluation != null) {
            setBannerVisible(true);
            if (evalSeparator != null) {
                evalSeparator.setVisible(true);
                evalSeparator.setManaged(true);
            }
            if (evaluationBannerBox != null) {
                evaluationBannerBox.setVisible(true);
                evaluationBannerBox.setManaged(true);
            }
            if (lblEvaluationTitle != null) {
                lblEvaluationTitle.setText(evaluation.getDesignation() != null ? evaluation.getDesignation() : "Évaluation");
            }
            updateEvaluationSubtitle(evaluation);
        } else {
            clearActiveEvaluation();
        }
    }

    public void clearActiveEvaluation() {
        this.activeEvaluation = null;
        if (evalSeparator != null) {
            evalSeparator.setVisible(false);
            evalSeparator.setManaged(false);
        }
        if (evaluationBannerBox != null) {
            evaluationBannerBox.setVisible(false);
            evaluationBannerBox.setManaged(false);
        }
        if (lblEvaluationTitle != null) {
            lblEvaluationTitle.setText("");
        }
        if (lblEvaluationSubtitle != null) {
            lblEvaluationSubtitle.setText("");
        }
    }

    public Evaluation getActiveEvaluation() {
        return activeEvaluation;
    }

    private void updateGroupSubtitle() {
        Label lblSubtitle = getSubtitleLabel();
        if (lblSubtitle == null) return;

        if (activeGroup != null) {
            StringBuilder sb = new StringBuilder();
            if (activeGroup.getSection() != null && !activeGroup.getSection().isBlank()) {
                sb.append(activeGroup.getSection());
            }
            if (activeGroup.getYear() != null && !activeGroup.getYear().isBlank()) {
                if (sb.length() > 0) sb.append(" • ");
                sb.append(activeGroup.getYear());
            }
            lblSubtitle.setText(sb.length() > 0 ? sb.toString() : "Groupe actif");
        } else {
            lblSubtitle.setText("Filière • Année");
        }
    }

    private void updateEvaluationSubtitle(Evaluation evaluation) {
        if (lblEvaluationSubtitle == null || evaluation == null) return;

        StringBuilder sb = new StringBuilder();
        if (evaluation.getType() != null && !evaluation.getType().isBlank()) {
            sb.append(evaluation.getType());
        }
        String stateLabel = "";
        if (evaluation.getState() != null) {
            stateLabel = switch (evaluation.getState()) {
                case CONCEPTION -> "Brouillon";
                case PRINTED -> "Prêt pour impression";
                case ARCHIVED -> "Copies scannées / Noté";
            };
        }
        if (!stateLabel.isEmpty()) {
            if (sb.length() > 0) sb.append(" • ");
            sb.append(stateLabel);
        }
        int qCount = evaluation.getQuestions() != null ? evaluation.getQuestions().size() : 0;
        if (qCount > 0) {
            if (sb.length() > 0) sb.append(" • ");
            sb.append(qCount).append(" question").append(qCount > 1 ? "s" : "");
        }
        if (evaluation.getDuration() != null) {
            if (sb.length() > 0) sb.append(" • ");
            sb.append(evaluation.getDuration().toMinutes()).append(" min");
        }
        lblEvaluationSubtitle.setText(sb.length() > 0 ? sb.toString() : "Épreuve");
    }

    public Group getActiveGroup() {
        return activeGroup;
    }

    public Group getActiveCourse() {
        return getActiveGroup();
    }

    private Group resolveGroup(String groupId) {
        if (groupId != null && !groupId.isBlank()) {
            try {
                return groupService.getGroup(groupId);
            } catch (Exception e) {
                log.warn("Groupe introuvable pour l'identifiant : {}", groupId);
            }
        }
        if (activeGroup != null) {
            return activeGroup;
        }
        List<Group> groups = groupService.getAllGroups();
        if (!groups.isEmpty()) {
            return groups.get(0);
        }
        return null;
    }

    @FXML
    public void showGroupsView() {
        clearNavigationHistory();
        setActiveButton(getGroupsButton());
        clearActiveEvaluation();
        setBannerVisible(false);
        loadView("/views/groups-view.fxml");
    }

    @FXML
    public void showCoursesView() {
        showGroupsView();
    }

    private GroupWorkspaceViewController getOrCreateWorkspace() {
        if (workspaceNode == null || workspaceController == null) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/group-workspace-view.fxml"));
                loader.setControllerFactory(applicationContext::getBean);
                this.workspaceNode = loader.load();
                this.workspaceController = loader.getController();
            } catch (IOException e) {
                log.error("Échec du chargement de l'espace de travail du groupe", e);
            }
        }
        return workspaceController;
    }

    public void showWorkspace(Group group, int tabIndex) {
        pushBackAction(this::showGroupsView);
        setActiveButton(null);
        setBannerVisible(true);
        GroupWorkspaceViewController c = getOrCreateWorkspace();
        if (c != null) {
            c.selectGroupAndTab(group, tabIndex);
            contentArea.getChildren().setAll(workspaceNode);
        }
    }

    public void showWorkspace(String groupId, int tabIndex) {
        Group group = resolveGroup(groupId);
        setActiveGroup(group, false);
        showWorkspace(group, tabIndex);
    }

    @FXML
    public void showStudentsView() {
        showStudentsForGroup(activeGroup != null ? activeGroup.getId() : null);
    }

    public void showStudentsForGroup(String groupId) {
        Group group = resolveGroup(groupId);
        setActiveGroup(group, false);
        clearActiveEvaluation();
        showWorkspace(group, 0);
    }

    public void showStudentsForCourse(String courseId) {
        showStudentsForGroup(courseId);
    }

    @FXML
    public void showEvaluationsView() {
        showEvaluationsForGroup(activeGroup != null ? activeGroup.getId() : null);
    }

    public void showEvaluationsForGroup(String groupId) {
        Group group = resolveGroup(groupId);
        setActiveGroup(group, false);
        clearActiveEvaluation();
        showWorkspace(group, 1);
    }

    public void showEvaluationsForCourse(String courseId) {
        showEvaluationsForGroup(courseId);
    }

    @FXML
    public void showCorrectionView() {
        showCorrectionForEvaluation(activeGroup != null ? activeGroup.getId() : null, null);
    }

    public void showCorrectionForEvaluation(String groupId, String evaluationId) {
        Group group = resolveGroup(groupId);
        setActiveGroup(group, false);
        setActiveButton(null);
        setBannerVisible(true);
        GroupWorkspaceViewController c = getOrCreateWorkspace();
        if (c != null) {
            c.selectGroupAndEvaluation(group, evaluationId);
            contentArea.getChildren().setAll(workspaceNode);
        }
    }

    public void syncTopNavForTab(int tabIndex) {
        // Le header affiche la bannière du groupe actif au lieu des boutons de navigation
    }

    @FXML
    public void showSettingsView() {
        Group previousGroup = activeGroup;
        Evaluation previousEval = activeEvaluation;
        pushBackAction(() -> {
            if (previousGroup != null) {
                setActiveGroup(previousGroup, false);
                if (previousEval != null) {
                    setActiveEvaluation(previousEval);
                }
                showWorkspace(previousGroup, 0);
            } else {
                showGroupsView();
            }
        });
        setActiveButton(btnSettings);
        clearActiveEvaluation();
        setBannerVisible(false);
        loadView("/views/settings-view.fxml");
    }

    public void openEvaluationBuilder(String evaluationId) {
        Group currentGrp = activeGroup;
        pushBackAction(() -> {
            if (currentGrp != null) {
                showEvaluationsForGroup(currentGrp.getId());
            } else {
                showGroupsView();
            }
        });
        setActiveButton(null);
        setBannerVisible(true);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/evaluation-builder-view.fxml"));
            loader.setControllerFactory(applicationContext::getBean);
            Node view = loader.load();
            EvaluationBuilderController controller = loader.getController();
            controller.loadEvaluation(evaluationId);
            contentArea.getChildren().setAll(view);
        } catch (IOException e) {
            log.error("Échec de l'ouverture du concepteur d'évaluation", e);
        }
    }

    public void openPdfViewer(File pdfFile, String title, Runnable backAction) {
        Runnable safeBack = () -> {
            try {
                if (applicationContext != null) {
                    PdfViewerController pvc = applicationContext.getBean(PdfViewerController.class);
                    pvc.closeCurrentDocument();
                }
            } catch (Exception ignored) {}
            if (backAction != null) {
                backAction.run();
            }
        };
        pushBackAction(safeBack);
        setBannerVisible(true);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/pdf-viewer-view.fxml"));
            loader.setControllerFactory(applicationContext::getBean);
            Node view = loader.load();
            PdfViewerController controller = loader.getController();
            controller.loadFile(pdfFile, title, safeBack);
            contentArea.getChildren().setAll(view);
        } catch (IOException e) {
            log.error("Échec de l'ouverture du visualiseur PDF", e);
        }
    }

    public void setStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
    }

    private void setActiveButton(Button active) {
        Button groupsBtn = getGroupsButton();
        for (Button btn : new Button[]{groupsBtn, btnSettings}) {
            if (btn != null) {
                if (btn == active) {
                    btn.setStyle("-fx-background-color: #e5e8ee; -fx-text-fill: #004faa; -fx-font-weight: 700;");
                } else {
                    btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #424753; -fx-font-weight: 500;");
                }
            }
        }
    }

    private void loadView(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            loader.setControllerFactory(applicationContext::getBean);
            Node view = loader.load();
            contentArea.getChildren().setAll(view);
        } catch (IOException e) {
            log.error("Échec du chargement de la vue FXML : {}", fxmlPath, e);
        }
    }

    @FXML
    public void onCourseBannerClicked() {
        if (activeGroup != null) {
            clearActiveEvaluation();
            showWorkspace(activeGroup, 0);
        }
    }

    private void setupShortcutsListener() {
        if (contentArea != null) {
            contentArea.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    setupKeyboardShortcuts(newScene);
                }
            });
        }
    }

    private void setupKeyboardShortcuts(Scene scene) {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN), this::focusSearchField);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN), this::triggerSaveIfBuilder);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN), this::triggerNewAction);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.ESCAPE), this::navigateBack);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.LEFT, KeyCombination.ALT_DOWN), this::navigateBack);
    }

    private void triggerNewAction() {
        if (contentArea == null) return;
        // Recherche dans l'ordre de priorité : "Nouveau groupe", "Nouvel étudiant", "Nouvelle évaluation", "Ajouter une question"
        String[] actions = {"Nouveau groupe", "Nouvel étudiant", "Nouvelle évaluation", "Ajouter une question"};
        for (String actionText : actions) {
            Button btn = findButtonByText(contentArea, actionText);
            if (btn != null && !btn.isDisable() && btn.isVisible()) {
                btn.fire();
                return;
            }
        }
    }

    private void focusSearchField() {
        if (contentArea == null) return;
        TextField search = findSearchField(contentArea);
        if (search != null) {
            search.requestFocus();
            search.selectAll();
        }
    }

    private TextField findSearchField(Node root) {
        if (root instanceof TextField tf) {
            if (tf.getStyleClass().contains("search-field") || (tf.getPromptText() != null && tf.getPromptText().toLowerCase().contains("recherch")) || (tf.getPromptText() != null && tf.getPromptText().toLowerCase().contains("filtrer"))) {
                return tf;
            }
        }
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                TextField found = findSearchField(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void triggerSaveIfBuilder() {
        if (activeEvaluation != null && contentArea != null) {
            Button btnSave = findButtonByText(contentArea, "Enregistrer");
            if (btnSave != null && !btnSave.isDisable()) {
                btnSave.fire();
            }
        }
    }

    private void handleEscapeFallback() {
        if (activeEvaluation != null && activeGroup != null) {
            showWorkspace(activeGroup, 1);
        } else if (activeGroup != null) {
            showGroupsView();
        }
    }

    private Button findButtonByText(Node root, String text) {
        if (root instanceof Button btn && text.equalsIgnoreCase(btn.getText())) {
            return btn;
        }
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Button found = findButtonByText(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Override
    public void showToast(String message, NotificationService.ToastType type) {
        if (contentArea == null) return;

        HBox toast = new HBox(10);
        toast.getStyleClass().add("toast-container");
        toast.setMaxWidth(420);
        toast.setMaxHeight(HBox.USE_PREF_SIZE);
        StackPane.setAlignment(toast, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(toast, new Insets(0, 24, 24, 0));

        FontIcon icon;
        switch (type) {
            case SUCCESS -> {
                toast.getStyleClass().add("toast-success");
                icon = new FontIcon(Feather.CHECK_CIRCLE);
                icon.setIconColor(javafx.scene.paint.Color.web("#1b8744"));
            }
            case ERROR -> {
                toast.getStyleClass().add("toast-error");
                icon = new FontIcon(Feather.ALERT_TRIANGLE);
                icon.setIconColor(javafx.scene.paint.Color.web("#ba1a1a"));
            }
            case WARNING -> {
                toast.setStyle("-fx-border-color: #f59e0b;");
                icon = new FontIcon(Feather.ALERT_CIRCLE);
                icon.setIconColor(javafx.scene.paint.Color.web("#b45309"));
            }
            default -> {
                toast.setStyle("-fx-border-color: -color-accent-emphasis;");
                icon = new FontIcon(Feather.INFO);
                icon.setIconColor(javafx.scene.paint.Color.web("#004faa"));
            }
        }
        icon.setIconSize(18);

        Label lbl = new Label(message);
        lbl.setWrapText(true);
        lbl.setStyle("-fx-font-size: 13px; -fx-font-weight: 500; -fx-text-fill: -color-fg-default;");
        HBox.setHgrow(lbl, Priority.ALWAYS);

        Button btnClose = new Button();
        btnClose.getStyleClass().add("card-action-btn");
        btnClose.setGraphic(new FontIcon(Feather.X));
        btnClose.setOnAction(e -> contentArea.getChildren().remove(toast));

        toast.getChildren().addAll(icon, lbl, btnClose);
        toast.setOpacity(0.0);

        contentArea.getChildren().add(toast);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(220), toast);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        PauseTransition pause = new PauseTransition(Duration.millis(3500));
        FadeTransition fadeOut = new FadeTransition(Duration.millis(250), toast);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(e -> contentArea.getChildren().remove(toast));

        SequentialTransition seq = new SequentialTransition(fadeIn, pause, fadeOut);
        seq.play();
    }
}
