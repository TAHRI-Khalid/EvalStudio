package tk.tahri.eval.desktop.ui.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import tk.tahri.eval.desktop.model.Group;
import tk.tahri.eval.desktop.model.Evaluation;
import tk.tahri.eval.desktop.service.GroupService;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

@Component
public class MainLayoutController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(MainLayoutController.class);

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

    private final ApplicationContext applicationContext;
    private final GroupService groupService;

    private Group activeGroup;
    private Evaluation activeEvaluation;
    private Node workspaceNode;
    private GroupWorkspaceViewController workspaceController;

    public MainLayoutController(ApplicationContext applicationContext, GroupService groupService) {
        this.applicationContext = applicationContext;
        this.groupService = groupService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupIcons();
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
        Button btn = getGroupsButton();
        if (btn != null) btn.setGraphic(new FontIcon(Feather.GRID));
        if (btnSettings != null) btnSettings.setGraphic(new FontIcon(Feather.SETTINGS));
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
        setActiveButton(btnSettings);
        clearActiveEvaluation();
        setBannerVisible(false);
        loadView("/views/settings-view.fxml");
    }

    public void openEvaluationBuilder(String evaluationId) {
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
        setBannerVisible(true);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/pdf-viewer-view.fxml"));
            loader.setControllerFactory(applicationContext::getBean);
            Node view = loader.load();
            PdfViewerController controller = loader.getController();
            controller.loadFile(pdfFile, title, backAction);
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
}
