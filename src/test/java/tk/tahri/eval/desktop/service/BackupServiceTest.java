package tk.tahri.eval.desktop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tk.tahri.eval.desktop.config.AppPaths;
import tk.tahri.eval.desktop.document.dto.TaskProgressTracker;
import tk.tahri.eval.desktop.repository.GroupRepository;
import tk.tahri.eval.desktop.service.dto.BackupManifest;

import javax.sql.DataSource;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackupServiceTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private Statement statement;

    @Mock
    private GroupRepository groupRepository;

    @TempDir
    Path tempAppDir;

    private BackupService backupService;
    private String originalAppDir;

    @BeforeEach
    void setUp() throws Exception {
        originalAppDir = System.getProperty("app.dir");
        System.setProperty("app.dir", tempAppDir.toAbsolutePath().toString());

        // Reinitialiser AppPaths
        AppPaths.init();

        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.createStatement()).thenReturn(statement);
        lenient().when(groupRepository.count()).thenReturn(3L);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        backupService = new BackupService(dataSource, groupRepository, mapper);
    }

    @AfterEach
    void tearDown() {
        if (originalAppDir != null) {
            System.setProperty("app.dir", originalAppDir);
        } else {
            System.clearProperty("app.dir");
        }
        AppPaths.resetForTests();
        AppPaths.init();
    }

    @Test
    void testExportAndValidateBackup(@TempDir Path targetDir) throws Exception {
        // Preparer des donnees factices dans data/ et upload/
        Path dataDir = AppPaths.getDataDir();
        Path uploadDir = AppPaths.getUploadDir();

        Files.createDirectories(dataDir);
        Files.createDirectories(uploadDir.resolve("evaluations/eval-123"));

        Files.writeString(dataDir.resolve("evaldb.mv.db"), "FAKE-H2-DATABASE-DATA");
        Files.writeString(dataDir.resolve("preferences.properties"), "theme=dark");
        Files.writeString(uploadDir.resolve("evaluations/eval-123/scan.png"), "FAKE-SCAN-IMAGE-BYTES");

        File backupFile = targetDir.resolve("test_backup.evalbackup").toFile();
        AtomicBoolean trackerCalled = new AtomicBoolean(false);
        TaskProgressTracker tracker = new TaskProgressTracker(tp -> trackerCalled.set(true));

        File exported = backupService.exportBackup(backupFile, tracker, () -> false);

        assertNotNull(exported);
        assertTrue(exported.exists());
        assertTrue(exported.length() > 0);
        assertTrue(trackerCalled.get(), "Le tracker de progression visuelle doit avoir ete notifie");

        // Verifier le contenu interne du ZIP
        try (ZipFile zip = new ZipFile(exported)) {
            assertNotNull(zip.getEntry("manifest.json"));
            assertNotNull(zip.getEntry("data/evaldb.mv.db"));
            assertNotNull(zip.getEntry("data/preferences.properties"));
            assertNotNull(zip.getEntry("upload/evaluations/eval-123/scan.png"));
        }

        // Valider l'archive via le service
        BackupManifest manifest = backupService.validateBackup(exported);
        assertNotNull(manifest);
        assertEquals(3, manifest.totalFiles());
        assertEquals(3L, manifest.activeGroupsCount());
        assertEquals(BackupManifest.CURRENT_DATABASE_ENGINE, manifest.databaseEngine());
    }

    @Test
    void testImportBackupRestoresFiles(@TempDir Path targetDir) throws Exception {
        Path dataDir = AppPaths.getDataDir();
        Path uploadDir = AppPaths.getUploadDir();

        // 1. Creation de donnees a sauvegarder
        Files.createDirectories(dataDir);
        Files.createDirectories(uploadDir.resolve("question_images"));

        Files.writeString(dataDir.resolve("evaldb.mv.db"), "H2-DB-V1");
        Files.writeString(uploadDir.resolve("question_images/q1.jpg"), "Q1-IMAGE");

        File backupFile = targetDir.resolve("to_import.evalbackup").toFile();
        backupService.exportBackup(backupFile, null, () -> false);

        // 2. Simulation d'une fresh install ou changement de donnees
        Files.writeString(dataDir.resolve("evaldb.mv.db"), "FRESH-EMPTY-DB");
        Files.deleteIfExists(uploadDir.resolve("question_images/q1.jpg"));
        Files.writeString(uploadDir.resolve("orphan_file.txt"), "SHOULD_BE_REMOVED");

        // 3. Execution de l'import
        AtomicBoolean trackerCalled = new AtomicBoolean(false);
        TaskProgressTracker tracker = new TaskProgressTracker(tp -> trackerCalled.set(true));

        backupService.importBackup(backupFile, tracker, () -> false);

        // 4. Verifications
        assertTrue(trackerCalled.get());
        assertEquals("H2-DB-V1", Files.readString(dataDir.resolve("evaldb.mv.db")));
        assertEquals("Q1-IMAGE", Files.readString(uploadDir.resolve("question_images/q1.jpg")));
        assertFalse(Files.exists(uploadDir.resolve("orphan_file.txt")), "L'ancien dossier upload doit etre nettoye");
    }

    @Test
    void testValidateBackupRejectsInvalidFile(@TempDir Path targetDir) throws Exception {
        File emptyZip = targetDir.resolve("empty.zip").toFile();
        try (var zos = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(emptyZip))) {
            zos.putNextEntry(new java.util.zip.ZipEntry("something_else.txt"));
            zos.write("hello".getBytes());
            zos.closeEntry();
        }

        assertThrows(IllegalArgumentException.class, () -> backupService.validateBackup(emptyZip));
    }

    @Test
    void testProgressEmitsSmoothlyOnLargeFile(@TempDir Path targetDir) throws Exception {
        Path dataDir = AppPaths.getDataDir();
        Path uploadDir = AppPaths.getUploadDir();

        Files.createDirectories(dataDir);
        Files.createDirectories(uploadDir);

        // Fichier DB de 1 Mo et gros fichier scan de 3 Mo
        byte[] chunk1MB = new byte[1024 * 1024];
        java.util.Arrays.fill(chunk1MB, (byte) 'A');
        Files.write(dataDir.resolve("evaldb.mv.db"), chunk1MB);

        byte[] chunk3MB = new byte[3 * 1024 * 1024];
        java.util.Arrays.fill(chunk3MB, (byte) 'B');
        Files.write(uploadDir.resolve("big_scan.pdf"), chunk3MB);

        java.util.List<Double> emittedRatios = new java.util.concurrent.CopyOnWriteArrayList<>();
        TaskProgressTracker tracker = new TaskProgressTracker(tp -> emittedRatios.add(tp.progressRatio()));

        File backupFile = targetDir.resolve("smooth_test.evalbackup").toFile();
        backupService.exportBackup(backupFile, tracker, () -> false);

        assertTrue(emittedRatios.size() >= 3, "Plusieurs mises à jour de progression doivent être émises");
        assertTrue(emittedRatios.get(emittedRatios.size() - 1) >= 0.99, "La progression finale doit atteindre 100%");
    }
}
