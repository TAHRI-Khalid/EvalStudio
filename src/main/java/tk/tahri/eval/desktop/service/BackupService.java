package tk.tahri.eval.desktop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tk.tahri.eval.desktop.config.AppPaths;
import tk.tahri.eval.desktop.document.dto.TaskProgressTracker;
import tk.tahri.eval.desktop.repository.GroupRepository;
import tk.tahri.eval.desktop.service.dto.BackupManifest;

import javax.sql.DataSource;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Service centralisé pour l'exportation et l'importation (restauration) des sauvegardes complètes.
 * Encapsule la base de données H2, les préférences utilisateur ainsi que tous les fichiers
 * multimédias (sujets PDF, scans étudiants, corrections et images).
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final String MANIFEST_ENTRY_NAME = "manifest.json";
    private static final int BUFFER_SIZE = 65536; // 64 Ko
    private static final long UI_THROTTLE_MS = 60; // Max 16 mises à jour UI / sec

    private final DataSource dataSource;
    private final GroupRepository groupRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.version:0.1.7}")
    private String appVersion;

    public BackupService(DataSource dataSource,
                         GroupRepository groupRepository,
                         ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.groupRepository = groupRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Valide l'intégrité d'une archive de sauvegarde et extrait son manifeste.
     */
    public BackupManifest validateBackup(File archiveFile) throws Exception {
        if (archiveFile == null || !archiveFile.exists() || archiveFile.length() == 0) {
            throw new IllegalArgumentException("Le fichier de sauvegarde est introuvable ou vide.");
        }

        try (ZipFile zipFile = new ZipFile(archiveFile)) {
            ZipEntry manifestEntry = zipFile.getEntry(MANIFEST_ENTRY_NAME);
            ZipEntry dbEntry = zipFile.getEntry("data/evaldb.mv.db");
            if (dbEntry == null) {
                dbEntry = zipFile.getEntry("evaldb.mv.db");
            }

            if (dbEntry == null) {
                throw new IllegalArgumentException("L'archive ne contient pas de base de données EvalStudio (evaldb.mv.db manquant).");
            }

            if (manifestEntry != null) {
                try (InputStream in = zipFile.getInputStream(manifestEntry)) {
                    return objectMapper.readValue(in, BackupManifest.class);
                }
            } else {
                // Rétrocompatibilité : manifeste synthétisé si l'archive est valide mais sans manifest.json
                int fileCount = 0;
                long totalSize = 0;
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (!entry.isDirectory()) {
                        fileCount++;
                        totalSize += entry.getSize();
                    }
                }
                return new BackupManifest(
                        BackupManifest.CURRENT_FORMAT_VERSION,
                        appVersion,
                        LocalDateTime.now(),
                        BackupManifest.CURRENT_DATABASE_ENGINE,
                        fileCount,
                        totalSize,
                        0L
                );
            }
        }
    }

    /**
     * Exporte l'intégralité des données (DB + Préférences + Uploads) dans une archive compressée.
     */
    public File exportBackup(File targetArchive,
                             TaskProgressTracker tracker,
                             BooleanSupplier cancellationCheck) throws Exception {

        Path dataDir = AppPaths.getDataDir();
        Path uploadDir = AppPaths.getUploadDir();

        // 1. Synchronisation de la base de données H2 sur disque
        if (tracker != null) {
            tracker.setStage("Synchronisation de la base H2", 2, 0.0, 0.05);
            tracker.updateStep(1, "Vidage des caches mémoire H2 vers le disque...");
        }

        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute("CHECKPOINT SYNC;");
        } catch (Exception e) {
            log.warn("Avertissement lors du CHECKPOINT SYNC H2 : {}", e.getMessage());
        }

        if (cancellationCheck != null && cancellationCheck.getAsBoolean()) {
            throw new InterruptedException("Exportation annulée par l'utilisateur.");
        }

        // 2. Inventaire des fichiers à archiver
        if (tracker != null) {
            tracker.updateStep(2, "Inventaire des fichiers et documents...");
        }

        List<Path> dataFiles = new ArrayList<>();
        if (Files.exists(dataDir)) {
            try (var stream = Files.list(dataDir)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> {
                            String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                            return !name.endsWith(".trace.db") && !name.endsWith(".lock.db");
                        })
                        .forEach(dataFiles::add);
            }
        }

        List<Path> uploadFiles = new ArrayList<>();
        if (Files.exists(uploadDir)) {
            try (var stream = Files.walk(uploadDir)) {
                stream.filter(Files::isRegularFile).forEach(uploadFiles::add);
            }
        }

        int totalFilesToZip = dataFiles.size() + uploadFiles.size();
        long uncompressedTotalBytes = 0;
        for (Path p : dataFiles) {
            uncompressedTotalBytes += Files.size(p);
        }
        for (Path p : uploadFiles) {
            uncompressedTotalBytes += Files.size(p);
        }

        long groupsCount = 0;
        try {
            groupsCount = groupRepository.count();
        } catch (Exception e) {
            log.debug("Impossible de compter les groupes : {}", e.getMessage());
        }

        BackupManifest manifest = new BackupManifest(
                BackupManifest.CURRENT_FORMAT_VERSION,
                appVersion,
                LocalDateTime.now(),
                BackupManifest.CURRENT_DATABASE_ENGINE,
                totalFilesToZip,
                uncompressedTotalBytes,
                groupsCount
        );

        // 3. Compression en streaming vers un fichier temporaire
        Path targetPath = targetArchive.toPath().toAbsolutePath();
        Path tmpZipPath = targetPath.resolveSibling(targetPath.getFileName().toString() + ".tmp");
        if (Files.exists(tmpZipPath)) {
            Files.delete(tmpZipPath);
        }

        if (tracker != null) {
            tracker.setStage("Archivage des données et documents", totalFilesToZip + 1, 0.05, 0.90);
        }

        long[] lastUpdateMsHolder = new long[]{System.currentTimeMillis()};
        long[] processedBytesHolder = new long[]{0};
        int fileIndex = 0;

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tmpZipPath.toFile()), BUFFER_SIZE))) {
            // Compression rapide optimale pour éviter de saturer le CPU sur les gros PDFs/images
            zos.setLevel(Deflater.BEST_SPEED);

            // Écriture du manifest.json
            ZipEntry manifestEntry = new ZipEntry(MANIFEST_ENTRY_NAME);
            zos.putNextEntry(manifestEntry);
            byte[] manifestBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest);
            zos.write(manifestBytes);
            zos.closeEntry();
            fileIndex++;

            if (tracker != null) {
                tracker.updateProgress(0.01, fileIndex, totalFilesToZip + 1, "Création du manifeste de sauvegarde...");
            }

            // Écriture des fichiers de data/ (base H2 et préférences)
            for (Path path : dataFiles) {
                if (cancellationCheck != null && cancellationCheck.getAsBoolean()) {
                    throw new InterruptedException("Exportation annulée par l'utilisateur.");
                }
                fileIndex++;
                String entryName = "data/" + path.getFileName().toString();
                streamFileToZip(zos, path, entryName, processedBytesHolder, uncompressedTotalBytes,
                        fileIndex, totalFilesToZip + 1, tracker, cancellationCheck, lastUpdateMsHolder);
            }

            // Écriture des fichiers de upload/ (documents, scans, images)
            for (Path path : uploadFiles) {
                if (cancellationCheck != null && cancellationCheck.getAsBoolean()) {
                    throw new InterruptedException("Exportation annulée par l'utilisateur.");
                }
                fileIndex++;
                Path rel = uploadDir.relativize(path);
                String entryName = "upload/" + rel.toString().replace('\\', '/');
                streamFileToZip(zos, path, entryName, processedBytesHolder, uncompressedTotalBytes,
                        fileIndex, totalFilesToZip + 1, tracker, cancellationCheck, lastUpdateMsHolder);
            }
        } catch (Exception e) {
            try {
                Files.deleteIfExists(tmpZipPath);
            } catch (Exception ignored) {}
            throw e;
        }

        // 4. Finalisation
        if (tracker != null) {
            tracker.setStage("Finalisation de l'archive", 1, 0.95, 0.05);
            tracker.updateStep(1, "Enregistrement du fichier de sauvegarde...");
        }

        Files.move(tmpZipPath, targetPath, StandardCopyOption.REPLACE_EXISTING);

        if (tracker != null) {
            tracker.setComplete("Sauvegarde terminée avec succès (" + formatSize(targetArchive.length()) + ").");
        }

        log.info("Sauvegarde EvalStudio exportée avec succès : {} ({} fichiers, {})",
                targetArchive.getAbsolutePath(), totalFilesToZip, formatSize(targetArchive.length()));

        return targetArchive;
    }

    /**
     * Importe et restaure l'intégralité d'une sauvegarde EvalStudio.
     */
    public void importBackup(File archiveFile,
                             TaskProgressTracker tracker,
                             BooleanSupplier cancellationCheck) throws Exception {

        BackupManifest manifest = validateBackup(archiveFile);
        Path appDir = AppPaths.getAppDir();
        Path dataDir = AppPaths.getDataDir();
        Path uploadDir = AppPaths.getUploadDir();

        if (tracker != null) {
            tracker.setStage("Préparation de la restauration", 3, 0.0, 0.05);
            tracker.updateStep(1, "Fermeture des connexions à la base de données...");
        }

        // Fermeture propre de la base de données H2 et libération des verrous
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute("SHUTDOWN;");
        } catch (Exception e) {
            log.warn("Shutdown H2 : {}", e.getMessage());
        }

        if (dataSource instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.warn("Fermeture DataSource : {}", e.getMessage());
            }
        }

        // Création d'une copie de sécurité temporaire pour le rollback en cas de panne
        if (tracker != null) {
            tracker.updateStep(2, "Création d'un point de restauration de sécurité...");
        }
        Path rollbackDir = appDir.resolve(".backup_rollback_" + System.currentTimeMillis());
        Files.createDirectories(rollbackDir);

        Path rollbackData = rollbackDir.resolve("data");
        Path rollbackUpload = rollbackDir.resolve("upload");
        if (Files.exists(dataDir)) {
            copyDirectoryRecursively(dataDir, rollbackData);
        }
        if (Files.exists(uploadDir)) {
            copyDirectoryRecursively(uploadDir, rollbackUpload);
        }

        if (tracker != null) {
            tracker.updateStep(3, "Extraction des données...");
        }

        try (ZipFile zipFile = new ZipFile(archiveFile)) {
            int totalEntries = 0;
            long totalUncompressedBytes = 0;
            Enumeration<? extends ZipEntry> en = zipFile.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (!e.isDirectory() && !MANIFEST_ENTRY_NAME.equals(e.getName())) {
                    totalEntries++;
                    if (e.getSize() > 0) {
                        totalUncompressedBytes += e.getSize();
                    }
                }
            }

            if (tracker != null) {
                tracker.setStage("Restauration des données et documents", totalEntries, 0.05, 0.90);
            }

            // Nettoyage préalable du dossier upload actuel pour ne pas conserver d'orphelins
            if (Files.exists(uploadDir)) {
                deleteDirectoryRecursively(uploadDir);
            }
            Files.createDirectories(uploadDir);
            Files.createDirectories(dataDir);

            int currentEntryIndex = 0;
            long[] lastUpdateMsHolder = new long[]{System.currentTimeMillis()};
            long[] extractedBytesHolder = new long[]{0};

            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                if (MANIFEST_ENTRY_NAME.equals(name)) {
                    continue;
                }

                Path destFile;
                if (name.startsWith("data/")) {
                    String sub = name.substring("data/".length());
                    destFile = dataDir.resolve(sub).normalize();
                    if (!destFile.startsWith(dataDir)) {
                        throw new SecurityException("Tentative de Zip Slip détectée pour " + name);
                    }
                } else if (name.startsWith("upload/")) {
                    String sub = name.substring("upload/".length());
                    destFile = uploadDir.resolve(sub).normalize();
                    if (!destFile.startsWith(uploadDir)) {
                        throw new SecurityException("Tentative de Zip Slip détectée pour " + name);
                    }
                } else if ("evaldb.mv.db".equals(name) || "preferences.properties".equals(name)) {
                    // Rétrocompatibilité racine
                    destFile = dataDir.resolve(name).normalize();
                } else {
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(destFile);
                } else {
                    Files.createDirectories(destFile.getParent());
                    currentEntryIndex++;
                    try (InputStream in = zipFile.getInputStream(entry)) {
                        streamEntryToFile(in, destFile, entry.getName(), extractedBytesHolder, totalUncompressedBytes,
                                currentEntryIndex, totalEntries, tracker, cancellationCheck, lastUpdateMsHolder);
                    }
                }
            }

            // Succès : suppression du dossier de rollback
            if (tracker != null) {
                tracker.setStage("Finalisation", 1, 0.95, 0.05);
                tracker.updateStep(1, "Nettoyage des fichiers temporaires...");
            }
            deleteDirectoryRecursively(rollbackDir);

            if (tracker != null) {
                tracker.setComplete("Restauration terminée avec succès. Redémarrage requis.");
            }
            log.info("Restauration de la sauvegarde EvalStudio terminée avec succès.");

        } catch (Exception ex) {
            log.error("Échec de la restauration de la sauvegarde, déclenchement du rollback...", ex);
            // Restauration de l'état initial
            try {
                if (Files.exists(rollbackData)) {
                    deleteDirectoryRecursively(dataDir);
                    copyDirectoryRecursively(rollbackData, dataDir);
                }
                if (Files.exists(rollbackUpload)) {
                    deleteDirectoryRecursively(uploadDir);
                    copyDirectoryRecursively(rollbackUpload, uploadDir);
                }
                deleteDirectoryRecursively(rollbackDir);
            } catch (Exception rollbackEx) {
                log.error("Échec critique lors du rollback", rollbackEx);
            }
            throw new IllegalStateException("Échec de la restauration : " + ex.getMessage(), ex);
        }
    }

    private void streamFileToZip(ZipOutputStream zos,
                                 Path file,
                                 String entryName,
                                 long[] processedBytesHolder,
                                 long totalBytes,
                                 int fileIndex,
                                 int totalFiles,
                                 TaskProgressTracker tracker,
                                 BooleanSupplier cancellationCheck,
                                 long[] lastUpdateMsHolder) throws Exception {

        ZipEntry entry = new ZipEntry(entryName);
        entry.setTime(Files.getLastModifiedTime(file).toMillis());
        zos.putNextEntry(entry);

        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (cancellationCheck != null && cancellationCheck.getAsBoolean()) {
                    throw new InterruptedException("Exportation annulée par l'utilisateur.");
                }
                zos.write(buffer, 0, read);
                processedBytesHolder[0] += read;

                long now = System.currentTimeMillis();
                if (tracker != null && (now - lastUpdateMsHolder[0] >= UI_THROTTLE_MS || processedBytesHolder[0] >= totalBytes)) {
                    lastUpdateMsHolder[0] = now;
                    double fraction = (double) processedBytesHolder[0] / Math.max(1, totalBytes);
                    String fileName = file.getFileName().toString();
                    String detail = String.format(Locale.FRENCH, "%s (%s / %s)",
                            fileName, formatSize(processedBytesHolder[0]), formatSize(totalBytes));
                    tracker.updateProgress(fraction, fileIndex, totalFiles, detail);
                }
            }
        }
        zos.closeEntry();
    }

    private void streamEntryToFile(InputStream in,
                                   Path destFile,
                                   String entryName,
                                   long[] extractedBytesHolder,
                                   long totalBytes,
                                   int entryIndex,
                                   int totalEntries,
                                   TaskProgressTracker tracker,
                                   BooleanSupplier cancellationCheck,
                                   long[] lastUpdateMsHolder) throws Exception {

        byte[] buffer = new byte[BUFFER_SIZE];
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(destFile), BUFFER_SIZE)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (cancellationCheck != null && cancellationCheck.getAsBoolean()) {
                    throw new InterruptedException("Restauration annulée par l'utilisateur.");
                }
                out.write(buffer, 0, read);
                extractedBytesHolder[0] += read;

                long now = System.currentTimeMillis();
                if (tracker != null && (now - lastUpdateMsHolder[0] >= UI_THROTTLE_MS || extractedBytesHolder[0] >= totalBytes)) {
                    lastUpdateMsHolder[0] = now;
                    double fraction = (double) extractedBytesHolder[0] / Math.max(1, totalBytes);
                    String fileName = destFile.getFileName().toString();
                    String detail = String.format(Locale.FRENCH, "%s (%s / %s)",
                            fileName, formatSize(extractedBytesHolder[0]), formatSize(totalBytes));
                    tracker.updateProgress(fraction, entryIndex, totalEntries, detail);
                }
            }
        }
    }

    private void copyDirectoryRecursively(Path source, Path target) throws Exception {
        if (!Files.exists(source)) return;
        try (var stream = Files.walk(source)) {
            stream.forEach(src -> {
                try {
                    Path dest = target.resolve(source.relativize(src));
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.createDirectories(dest.getParent());
                        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private void deleteDirectoryRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {}
                    });
        } catch (Exception e) {
            log.warn("Impossible de supprimer complètement le dossier {}", dir, e);
        }
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " octets";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format(Locale.FRENCH, "%.2f %so", bytes / Math.pow(1024, exp), pre);
    }
}
