package tk.tahri.eval.desktop.ui.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;

public class DesktopFileViewer {

    private static final Logger log = LoggerFactory.getLogger(DesktopFileViewer.class);

    public static void openFile(File file) {
        if (file == null || !file.exists()) {
            log.warn("Fichier introuvable pour ouverture : {}", file);
            return;
        }

        // Toujours exécuter l'ouverture dans un thread d'arrière-plan pour ne jamais bloquer l'UI JavaFX
        Thread.startVirtualThread(() -> {
            try {
                String os = System.getProperty("os.name", "").toLowerCase();
                log.info("Tentative d'ouverture du fichier {} sur OS: {}", file.getAbsolutePath(), os);

                if (os.contains("linux")) {
                    // Sur Linux, xdg-open via ProcessBuilder est asynchrone et évite le blocage AWT/GIO
                    try {
                        Process process = new ProcessBuilder("xdg-open", file.getAbsolutePath())
                                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                                .redirectError(ProcessBuilder.Redirect.DISCARD)
                                .start();
                        log.info("Fichier ouvert avec succès via xdg-open (PID: {})", process.pid());
                        return;
                    } catch (Exception e) {
                        log.warn("xdg-open a échoué ({}), bascule sur Desktop.getDesktop()", e.getMessage());
                    }
                } else if (os.contains("mac")) {
                    try {
                        new ProcessBuilder("open", file.getAbsolutePath())
                                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                                .redirectError(ProcessBuilder.Redirect.DISCARD)
                                .start();
                        return;
                    } catch (Exception ignored) {}
                } else if (os.contains("windows")) {
                    try {
                        new ProcessBuilder("cmd", "/c", "start", "", file.getAbsolutePath())
                                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                                .redirectError(ProcessBuilder.Redirect.DISCARD)
                                .start();
                        return;
                    } catch (Exception ignored) {}
                }

                // Fallback générique
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(file);
                    log.info("Fichier ouvert avec succès via Desktop.getDesktop()");
                } else {
                    log.error("Desktop API non supportée sur cet environnement pour ouvrir {}", file.getAbsolutePath());
                }
            } catch (Exception e) {
                log.error("Impossible d'ouvrir le fichier : {}", file.getAbsolutePath(), e);
            }
        });
    }
}
