package tk.tahri.eval.desktop;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationContextInitializedEvent;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ConfigurableApplicationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import tk.tahri.eval.desktop.ui.SplashScreen;

@SpringBootApplication
public class EvalDesktopApplication extends Application {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    private ConfigurableApplicationContext springContext;

    @Override
    public void init() {
        tk.tahri.eval.desktop.config.AppPaths.init();
    }

    @Override
    public void start(Stage primaryStage) {
        // Applique le thème immédiatement
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        // Affiche immédiatement l'écran de démarrage pour rassurer l'utilisateur dès le clic
        SplashScreen splashScreen = new SplashScreen();
        splashScreen.show();

        // Démarre Spring Boot en tâche d'arrière-plan sans bloquer l'UI
        Task<ConfigurableApplicationContext> startupTask = new Task<>() {
            @Override
            protected ConfigurableApplicationContext call() {
                return new SpringApplicationBuilder(EvalDesktopApplication.class)
                        .headless(false)
                        .listeners(event -> {
                            if (event instanceof ApplicationStartingEvent) {
                                Platform.runLater(() -> splashScreen.setProgressText("Démarrage du moteur applicatif..."));
                            } else if (event instanceof ApplicationEnvironmentPreparedEvent) {
                                Platform.runLater(() -> splashScreen.setProgressText("Chargement de la configuration..."));
                            } else if (event instanceof ApplicationContextInitializedEvent) {
                                Platform.runLater(() -> splashScreen.setProgressText("Initialisation du contexte..."));
                            } else if (event instanceof ApplicationPreparedEvent) {
                                Platform.runLater(() -> splashScreen.setProgressText("Préparation de la base de données..."));
                            } else if (event instanceof ApplicationStartedEvent) {
                                Platform.runLater(() -> splashScreen.setProgressText("Lancement de l'interface..."));
                            }
                        })
                        .run(getParameters().getRaw().toArray(new String[0]));
            }
        };

        startupTask.setOnSucceeded(event -> {
            try {
                this.springContext = startupTask.getValue();

                FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/main-layout.fxml"));
                loader.setControllerFactory(springContext::getBean);
                Parent root = loader.load();

                Scene scene = new Scene(root, 1240, 800);
                try {
                    var iconStream = getClass().getResourceAsStream("/icons/app-icon.png");
                    if (iconStream != null) {
                        primaryStage.getIcons().add(new javafx.scene.image.Image(iconStream));
                    }
                } catch (Exception ignored) {}

                primaryStage.setTitle("EVAL - Studio d'Évaluation & Correction Automatique (Desktop)");
                primaryStage.setMinWidth(1050);
                primaryStage.setMinHeight(680);
                primaryStage.setScene(scene);

                // Affiche la fenêtre principale puis ferme le splash screen en douceur
                primaryStage.show();
                splashScreen.closeWithFade(null);
            } catch (Exception ex) {
                handleStartupFailure(splashScreen, ex);
            }
        });

        startupTask.setOnFailed(event -> {
            handleStartupFailure(splashScreen, startupTask.getException());
        });

        Thread startupThread = new Thread(startupTask, "EvalStartupThread");
        startupThread.setDaemon(true);
        startupThread.start();
    }

    private void handleStartupFailure(SplashScreen splashScreen, Throwable ex) {
        if (splashScreen != null) {
            splashScreen.getStage().close();
        }
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur de démarrage");
        alert.setHeaderText("Impossible de démarrer l'application");
        String message = (ex != null && ex.getMessage() != null) ? ex.getMessage() : "Une erreur inconnue est survenue au démarrage.";
        alert.setContentText(message);
        alert.showAndWait();
        Platform.exit();
    }

    @Override
    public void stop() {
        if (springContext != null) {
            springContext.close();
        }
        Platform.exit();
    }

    public static void main(String[] args) {
        Application.launch(EvalDesktopApplication.class, args);
    }
}
