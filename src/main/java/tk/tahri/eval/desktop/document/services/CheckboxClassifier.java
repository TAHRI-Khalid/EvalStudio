package tk.tahri.eval.desktop.document.services;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@Service
public class CheckboxClassifier implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CheckboxClassifier.class);

    private static final int CROP_SIZE = 28;
    private static final int PIXELS_PER_CROP = CROP_SIZE * CROP_SIZE;

    private final String modelPath;
    private OrtEnvironment env;
    private OrtSession session;

    public CheckboxClassifier(@Value("${eval.document.model-path:model/eval_model.onnx}") String modelPath) {
        this.modelPath = modelPath;
    }

    @PostConstruct
    public synchronized void init() {
        try {
            Path path = Path.of(modelPath);
            if (!Files.exists(path) || Files.size(path) == 0) {
                try (var in = getClass().getResourceAsStream("/model/eval_model.onnx")) {
                    if (in != null) {
                        if (path.getParent() != null) {
                            Files.createDirectories(path.getParent());
                        }
                        Files.copy(in, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        log.info("Modèle ONNX extrait depuis le classpath vers : {}", path.toAbsolutePath());
                    }
                } catch (Exception ex) {
                    log.warn("Impossible d'extraire le modèle ONNX depuis le classpath : {}", ex.getMessage());
                }
            }

            if (!Files.exists(path)) {
                log.warn("Fichier modèle ONNX introuvable à : {}", path.toAbsolutePath());
                return;
            }

            this.env = OrtEnvironment.getEnvironment();
            try (var options = new OrtSession.SessionOptions()) {
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
                int threads = Math.max(1, Runtime.getRuntime().availableProcessors());
                options.setIntraOpNumThreads(threads);
                this.session = env.createSession(modelPath, options);
            }

            log.info("ONNX Runtime initialisé avec le modèle : {} (inputs: {}, outputs: {})",
                    modelPath, session.getInputNames(), session.getOutputNames());
        } catch (Exception e) {
            log.error("Échec de l'initialisation de la session ONNX Runtime pour le modèle : {}", modelPath, e);
        }
    }

    public int[] predictClasses(List<Mat> crops) {
        if (crops == null || crops.isEmpty()) {
            return new int[0];
        }

        if (session == null || env == null) {
            throw new IllegalStateException("La session ONNX Runtime n'est pas initialisée");
        }

        int batchSize = crops.size();
        FloatBuffer buffer = FloatBuffer.allocate(batchSize * PIXELS_PER_CROP);
        byte[] rowBytes = new byte[CROP_SIZE];

        for (Mat crop : crops) {
            if (crop.rows() != CROP_SIZE || crop.cols() != CROP_SIZE) {
                throw new IllegalArgumentException("Taille attendue du crop : " + CROP_SIZE + "x" + CROP_SIZE + ", obtenu : " + crop.cols() + "x" + crop.rows());
            }
            for (int r = 0; r < CROP_SIZE; r++) {
                crop.get(r, 0, rowBytes);
                for (int c = 0; c < CROP_SIZE; c++) {
                    float normalized = ((rowBytes[c] & 0xFF) - 127.5f) / 127.5f;
                    buffer.put(normalized);
                }
            }
        }
        buffer.flip();

        long[] shape = new long[]{batchSize, 1, CROP_SIZE, CROP_SIZE};
        try (var tensor = OnnxTensor.createTensor(env, buffer, shape);
             var result = session.run(Collections.singletonMap("input", tensor))) {

            float[][] outputs = (float[][]) result.get(0).getValue();
            int[] predictions = new int[batchSize];

            for (int i = 0; i < batchSize; i++) {
                float[] logits = outputs[i];
                int maxIdx = 0;
                float maxVal = logits[0];
                for (int j = 1; j < logits.length; j++) {
                    if (logits[j] > maxVal) {
                        maxVal = logits[j];
                        maxIdx = j;
                    }
                }
                predictions[i] = maxIdx;
            }

            return predictions;
        } catch (OrtException e) {
            log.error("Erreur d'inférence ONNX Runtime", e);
            throw new RuntimeException("Inférence échouée", e);
        }
    }

    public boolean isChecked(int classId) {
        return classId == 1 || classId == 3;
    }

    public boolean isReady() {
        return session != null;
    }

    public String getModelPath() {
        return modelPath;
    }

    @PreDestroy
    @Override
    public synchronized void close() {
        if (session != null) {
            try {
                session.close();
            } catch (OrtException e) {
                log.warn("Erreur à la fermeture d'OrtSession", e);
            }
            session = null;
        }
        if (env != null) {
            env.close();
            env = null;
        }
        log.info("Ressources ONNX Runtime libérées");
    }
}
