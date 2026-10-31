package tk.tahri.eval.desktop.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * Fiche d'identité et métadonnées d'une archive de sauvegarde EvalStudio.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BackupManifest(
        int formatVersion,
        String appVersion,
        LocalDateTime createdAt,
        String databaseEngine,
        int totalFiles,
        long uncompressedSizeBytes,
        long activeGroupsCount
) {
    public static final int CURRENT_FORMAT_VERSION = 1;
    public static final String CURRENT_DATABASE_ENGINE = "H2-MVStore";
}
