package tk.tahri.eval.desktop.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OpenCvConfig {

    private static final Logger log = LoggerFactory.getLogger(OpenCvConfig.class);

    @PostConstruct
    public void loadOpenCvNativeLibrary() {
        try {
            nu.pattern.OpenCV.loadLocally();
            log.info("Bibliothèque native OpenCV chargée avec succès");
        } catch (Exception e) {
            log.error("Échec du chargement de la bibliothèque OpenCV native", e);
        }
    }
}
