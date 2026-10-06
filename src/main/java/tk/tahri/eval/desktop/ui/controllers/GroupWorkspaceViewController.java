package tk.tahri.eval.desktop.ui.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import tk.tahri.eval.desktop.model.Group;
import tk.tahri.eval.desktop.model.Evaluation;

import java.net.URL;
import java.util.ResourceBundle;

@Component
public class GroupWorkspaceViewController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(GroupWorkspaceViewController.class);

    @FXML private TabPane workspaceTabPane;
    @FXML private Tab tabStudents;
    @FXML private Tab tabEvaluations;
    @FXML private Tab tabCorrection;

    private final ApplicationContext applicationContext;
    private final MainLayoutController mainLayoutController;

    private StudentsViewController studentsController;
    private EvaluationsViewController evaluationsController;
    private CorrectionViewController correctionController;
    private Group currentGroup;

    public GroupWorkspaceViewController(ApplicationContext applicationContext,
                                        @org.springframework.context.annotation.Lazy MainLayoutController mainLayoutController) {
        this.applicationContext = applicationContext;
        this.mainLayoutController = mainLayoutController;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTabs();
    }

    private void setupTabs() {
        tabStudents.setGraphic(new FontIcon(Feather.USERS));
        tabEvaluations.setGraphic(new FontIcon(Feather.FILE_TEXT));
        tabCorrection.setGraphic(new FontIcon(Feather.CHECK_CIRCLE));

        try {
            FXMLLoader sLoader = new FXMLLoader(getClass().getResource("/views/students-view.fxml"));
            sLoader.setControllerFactory(applicationContext::getBean);
            Node sNode = sLoader.load();
            this.studentsController = sLoader.getController();
            tabStudents.setContent(sNode);

            FXMLLoader eLoader = new FXMLLoader(getClass().getResource("/views/evaluations-view.fxml"));
            eLoader.setControllerFactory(applicationContext::getBean);
            Node eNode = eLoader.load();
            this.evaluationsController = eLoader.getController();
            tabEvaluations.setContent(eNode);

            FXMLLoader cLoader = new FXMLLoader(getClass().getResource("/views/correction-view.fxml"));
            cLoader.setControllerFactory(applicationContext::getBean);
            Node cNode = cLoader.load();
            this.correctionController = cLoader.getController();
            tabCorrection.setContent(cNode);

            workspaceTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                if (currentGroup == null) return;
                if (newTab == tabStudents && studentsController != null) {
                    studentsController.loadStudents(currentGroup.getId());
                    if (mainLayoutController != null) {
                        mainLayoutController.clearActiveEvaluation();
                    }
                } else if (newTab == tabEvaluations && evaluationsController != null) {
                    evaluationsController.loadEvaluations(currentGroup.getId());
                    if (mainLayoutController != null) {
                        mainLayoutController.clearActiveEvaluation();
                    }
                } else if (newTab == tabCorrection && correctionController != null) {
                    correctionController.loadEvaluationsForGroup(currentGroup.getId());
                    if (mainLayoutController != null) {
                        Evaluation sel = correctionController.getSelectedEvaluation();
                        if (sel != null) {
                            mainLayoutController.setActiveEvaluation(sel);
                        } else {
                            mainLayoutController.clearActiveEvaluation();
                        }
                    }
                }
            });
        } catch (Exception e) {
            log.error("Erreur lors du chargement des onglets de l'espace de travail du groupe", e);
        }
    }

    public void setGroup(Group group) {
        this.currentGroup = group;
        if (studentsController != null) {
            studentsController.setGroup(group);
        }
        if (evaluationsController != null) {
            evaluationsController.setGroup(group);
        }
        if (correctionController != null) {
            correctionController.setGroup(group);
        }
    }

    public Group getCurrentGroup() {
        return currentGroup;
    }

    // Compatibility methods
    public void setCourse(Group course) { setGroup(course); }
    public Group getCurrentCourse() { return getCurrentGroup(); }

    public void selectTab(int tabIndex) {
        if (tabIndex >= 0 && tabIndex < workspaceTabPane.getTabs().size()) {
            workspaceTabPane.getSelectionModel().select(tabIndex);
            if (tabIndex == 0 || tabIndex == 1) {
                if (mainLayoutController != null) {
                    mainLayoutController.clearActiveEvaluation();
                }
            }
        }
    }

    public int getCurrentTabIndex() {
        if (workspaceTabPane != null && workspaceTabPane.getSelectionModel() != null) {
            return workspaceTabPane.getSelectionModel().getSelectedIndex();
        }
        return 0;
    }

    public void selectGroupAndTab(Group group, int tabIndex) {
        setGroup(group);
        selectTab(tabIndex);
        if (tabIndex == 0 || tabIndex == 1) {
            if (mainLayoutController != null) {
                mainLayoutController.clearActiveEvaluation();
            }
        }
    }

    public void selectGroupAndEvaluation(Group group, String evalId) {
        setGroup(group);
        selectTab(2); // Onglet Correction & Scanner
        if (correctionController != null) {
            correctionController.selectGroupAndEvaluation(group != null ? group.getId() : null, evalId);
        }
    }

    public void selectCourseAndTab(Group course, int tabIndex) { selectGroupAndTab(course, tabIndex); }
    public void selectCourseAndEvaluation(Group course, String evalId) { selectGroupAndEvaluation(course, evalId); }

    public StudentsViewController getStudentsController() { return studentsController; }
    public EvaluationsViewController getEvaluationsController() { return evaluationsController; }
    public CorrectionViewController getCorrectionController() { return correctionController; }
}
