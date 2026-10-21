package tk.tahri.eval.desktop.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;

/**
 * Résolution dynamique de la version de l'application depuis Maven pom.properties,
 * le manifest JAR ou application.properties.
 */
public final class AppVersion {

    private static final Logger log = LoggerFactory.getLogger(AppVersion.class);
    private static final String DEFAULT_FALLBACK_VERSION = "0.1.6";
    private static volatile String cachedVersion;

    private AppVersion() {}

    /**
     * Retourne la version de l'application préfixée par 'v' (ex: "v0.1.5").
     */
    public static String getVersion() {
        if (cachedVersion != null) {
            return cachedVersion;
        }

        String version = resolveVersion();
        if (version == null || version.isBlank() || version.contains("@")) {
            version = DEFAULT_FALLBACK_VERSION;
        }
        if (!version.startsWith("v") && !version.startsWith("V")) {
            version = "v" + version;
        }
        cachedVersion = version;
        return cachedVersion;
    }

    private static String resolveVersion() {
        // 1. Essai via Package Implementation-Version (disponible en JAR packagé)
        Package pkg = AppVersion.class.getPackage();
        if (pkg != null && pkg.getImplementationVersion() != null && !pkg.getImplementationVersion().isBlank()) {
            return pkg.getImplementationVersion().trim();
        }

        // 2. Essai via pom.properties généré par Maven
        try (InputStream is = AppVersion.class.getResourceAsStream("/META-INF/maven/tk.tahri/eval_desktop/pom.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                String v = props.getProperty("version");
                if (v != null && !v.isBlank()) {
                    return v.trim();
                }
            }
        } catch (Exception ex) {
            log.debug("Impossible de lire pom.properties : {}", ex.getMessage());
        }

        // 3. Essai via application.properties (filtré par Maven)
        try (InputStream is = AppVersion.class.getResourceAsStream("/application.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                String v = props.getProperty("app.version");
                if (v != null && !v.isBlank() && !v.contains("@")) {
                    return v.trim();
                }
            }
        } catch (Exception ex) {
            log.debug("Impossible de lire app.version depuis application.properties : {}", ex.getMessage());
        }

        return DEFAULT_FALLBACK_VERSION;
    }
}
