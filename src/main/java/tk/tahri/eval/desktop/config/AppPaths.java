package tk.tahri.eval.desktop.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Gestionnaire centralisé des répertoires applicatifs d'EvalStudio.
 * Détecte l'environnement (développement vs application desktop installée)
 * et garantit que la base de données, les uploads et le modèle d'IA sont
 * toujours situés dans un espace accessible en écriture par l'utilisateur.
 */
public final class AppPaths {

    private static final Logger log = LoggerFactory.getLogger(AppPaths.class);
    private static volatile Path appDir;

    private AppPaths() {}

    public static synchronized Path init() {
        if (appDir != null) {
            return appDir;
        }

        String customDir = System.getProperty("app.dir");
        if (customDir != null && !customDir.isBlank()) {
            appDir = Path.of(customDir).toAbsolutePath().normalize();
        } else if (new File("pom.xml").exists()) {
            // Mode développement : exécuté depuis la racine du dépôt source
            appDir = Path.of(".").toAbsolutePath().normalize();
        } else {
            // Mode application installée (MSI, DMG, DEB, RPM) : dossier utilisateur OS
            appDir = resolveDefaultAppDataDir();
        }

        System.setProperty("app.dir", appDir.toString());
        log.info("Répertoire des données applicatives : {}", appDir);

        try {
            Files.createDirectories(getDataDir());
            Files.createDirectories(getUploadDir());
            Files.createDirectories(getModelDir());

            ensureModelExtracted();
        } catch (Exception e) {
            log.error("Erreur lors de l'initialisation des répertoires applicatifs", e);
        }

        return appDir;
    }

    private static Path resolveDefaultAppDataDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String userHome = System.getProperty("user.home", ".");

        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return Path.of(localAppData, "EvalStudio");
            }
            return Path.of(userHome, "AppData", "Local", "EvalStudio");
        } else if (os.contains("mac")) {
            return Path.of(userHome, "Library", "Application Support", "EvalStudio");
        } else {
            String xdgData = System.getenv("XDG_DATA_HOME");
            if (xdgData != null && !xdgData.isBlank()) {
                return Path.of(xdgData, "evalstudio");
            }
            return Path.of(userHome, ".local", "share", "evalstudio");
        }
    }

    private static void ensureModelExtracted() {
        Path targetModel = getModelDir().resolve("eval_model.onnx");
        if (Files.exists(targetModel) && targetModel.toFile().length() > 0) {
            return;
        }

        try (InputStream in = AppPaths.class.getResourceAsStream("/model/eval_model.onnx")) {
            if (in != null) {
                Files.copy(in, targetModel, StandardCopyOption.REPLACE_EXISTING);
                log.info("Modèle ONNX extrait avec succès vers : {}", targetModel);
            } else {
                log.warn("Modèle ONNX introuvable dans les ressources /model/eval_model.onnx");
            }
        } catch (Exception e) {
            log.error("Échec de l'extraction du modèle ONNX vers {}", targetModel, e);
        }
    }

    public static Path getAppDir() {
        if (appDir == null) {
            return init();
        }
        return appDir;
    }

    public static Path getDataDir() {
        return getAppDir().resolve("data");
    }

    public static Path getUploadDir() {
        return getAppDir().resolve("upload");
    }

    public static Path getModelDir() {
        return getAppDir().resolve("model");
    }

    public static Path getModelPath() {
        return getModelDir().resolve("eval_model.onnx");
    }
}
