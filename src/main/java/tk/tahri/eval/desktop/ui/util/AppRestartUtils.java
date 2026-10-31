package tk.tahri.eval.desktop.ui.util;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilitaire permettant de relancer l'application de façon propre et multiplateforme
 * après des opérations système comme la restauration intégrale d'une sauvegarde.
 */
public final class AppRestartUtils {

    private static final Logger log = LoggerFactory.getLogger(AppRestartUtils.class);

    private AppRestartUtils() {}

    public static void restart() {
        try {
            String command = ProcessHandle.current().info().command().orElse(null);
            List<String> cmd = new ArrayList<>();

            if (command != null && !command.isBlank() && !command.endsWith("java") && !command.endsWith("java.exe")) {
                // Application packagée nativement (binaire jpackage, .exe, binaire Linux, etc.)
                cmd.add(command);
            } else {
                // Exécution standard via machine virtuelle Java (FAT jar ou IDE/Maven)
                String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
                cmd.add(javaBin);

                String sunCmd = System.getProperty("sun.java.command");
                if (sunCmd != null && !sunCmd.isBlank()) {
                    String[] parts = sunCmd.split("\\s+");
                    if (parts.length > 0 && parts[0].endsWith(".jar")) {
                        cmd.add("-jar");
                        cmd.add(parts[0]);
                    } else {
                        cmd.add("-cp");
                        cmd.add(System.getProperty("java.class.path"));
                        cmd.add(parts[0]);
                    }
                    for (int i = 1; i < parts.length; i++) {
                        cmd.add(parts[i]);
                    }
                }
            }

            log.info("Relance du processus applicatif avec la commande : {}", cmd);
            new ProcessBuilder(cmd).start();
        } catch (Exception e) {
            log.error("Échec lors de la tentative de relance automatique de l'application", e);
        } finally {
            Platform.exit();
            System.exit(0);
        }
    }
}
