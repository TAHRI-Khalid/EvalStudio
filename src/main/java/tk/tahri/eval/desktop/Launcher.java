package tk.tahri.eval.desktop;

import tk.tahri.eval.desktop.config.AppPaths;

/**
 * Lanceur principal pour JavaFX et Spring Boot.
 * Permet l'exécution en fat JAR autonome et le packaging natif avec jpackage
 * sans conflit avec le modular launcher de JavaFX.
 */
public class Launcher {

    public static void main(String[] args) {
        AppPaths.init();
        EvalDesktopApplication.main(args);
    }
}
