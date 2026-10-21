package tk.tahri.eval.desktop.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@Component
public class AppPreferences {

    private static final Logger log = LoggerFactory.getLogger(AppPreferences.class);
    private static final String PREF_FILE = "preferences.properties";

    public static final String KEY_EVAL_VIEW_MODE = "view.evaluations.mode";
    public static final String KEY_CORRECTION_VIEW_MODE = "view.correction.mode";
    public static final String KEY_EXPORT_DIR = "export.directory";

    public enum ViewMode {
        LIST, GRID
    }

    private final Properties properties = new Properties();
    private final Path prefPath;

    public AppPreferences() {
        this.prefPath = AppPaths.getDataDir().resolve(PREF_FILE);
        load();
    }

    private synchronized void load() {
        if (Files.exists(prefPath)) {
            try (InputStream in = Files.newInputStream(prefPath)) {
                properties.load(in);
            } catch (Exception e) {
                log.warn("Impossible de charger les préférences utilisateur depuis {} : {}", prefPath, e.getMessage());
            }
        }
    }

    private synchronized void save() {
        try {
            if (prefPath.getParent() != null) {
                Files.createDirectories(prefPath.getParent());
            }
            try (OutputStream out = Files.newOutputStream(prefPath)) {
                properties.store(out, "EvalStudio User Preferences");
            }
        } catch (Exception e) {
            log.error("Erreur lors de la sauvegarde des préférences utilisateur dans {} : {}", prefPath, e.getMessage());
        }
    }

    public synchronized String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public synchronized void set(String key, String value) {
        if (value == null) {
            properties.remove(key);
        } else {
            properties.setProperty(key, value);
        }
        save();
    }

    public ViewMode getViewMode(String key, ViewMode defaultMode) {
        String val = get(key, defaultMode != null ? defaultMode.name() : ViewMode.LIST.name());
        try {
            return ViewMode.valueOf(val.toUpperCase());
        } catch (Exception e) {
            return defaultMode;
        }
    }

    public void setViewMode(String key, ViewMode mode) {
        set(key, mode != null ? mode.name() : ViewMode.LIST.name());
    }

    public ViewMode getEvaluationsViewMode() {
        return getViewMode(KEY_EVAL_VIEW_MODE, ViewMode.LIST);
    }

    public void setEvaluationsViewMode(ViewMode mode) {
        setViewMode(KEY_EVAL_VIEW_MODE, mode);
    }

    public ViewMode getCorrectionViewMode() {
        return getViewMode(KEY_CORRECTION_VIEW_MODE, ViewMode.LIST);
    }

    public void setCorrectionViewMode(ViewMode mode) {
        setViewMode(KEY_CORRECTION_VIEW_MODE, mode);
    }

    public String getExportDirectory() {
        return get(KEY_EXPORT_DIR, System.getProperty("user.home"));
    }

    public void setExportDirectory(String dir) {
        set(KEY_EXPORT_DIR, dir);
    }
}
