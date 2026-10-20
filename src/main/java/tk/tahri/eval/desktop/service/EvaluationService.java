package tk.tahri.eval.desktop.service;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tk.tahri.eval.desktop.document.repositories.BlueprintPaperAssessmentRepository;
import tk.tahri.eval.desktop.document.repositories.UncorrectedPageRepository;
import tk.tahri.eval.desktop.exception.InvalidEvaluationStateException;
import tk.tahri.eval.desktop.exception.NotFoundException;
import tk.tahri.eval.desktop.model.*;
import tk.tahri.eval.desktop.repository.EvaluationRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    private final EvaluationRepository evaluationRepository;
    private final BlueprintPaperAssessmentRepository blueprintPaperAssessmentRepository;
    private final UncorrectedPageRepository uncorrectedPageRepository;

    @Value("${eval.document.upload-dir:upload}")
    private String uploadDirPath;

    public EvaluationService(EvaluationRepository evaluationRepository,
                             BlueprintPaperAssessmentRepository blueprintPaperAssessmentRepository,
                             UncorrectedPageRepository uncorrectedPageRepository) {
        this.evaluationRepository = evaluationRepository;
        this.blueprintPaperAssessmentRepository = blueprintPaperAssessmentRepository;
        this.uncorrectedPageRepository = uncorrectedPageRepository;
    }

    public List<Evaluation> getEvaluationsByGroup(String groupId) {
        return evaluationRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
    }

    public List<Evaluation> getEvaluationsByCourse(String courseId) {
        return getEvaluationsByGroup(courseId);
    }

    public Evaluation getEvaluation(String evaluationId) {
        return evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new NotFoundException("Évaluation introuvable : " + evaluationId));
    }

    public Evaluation createEvaluation(String groupId, String designation, String type) {
        Evaluation evaluation = new Evaluation();
        evaluation.setGroupId(groupId);
        evaluation.setDesignation(designation);
        evaluation.setType(type != null && !type.isBlank() ? type : Evaluation.TYPE_CONTROLE_CONTINU);
        evaluation.setCreatedAt(LocalDate.now());
        evaluation.setState(EvaluationState.CONCEPTION);
        evaluation.setQuestions(new ArrayList<>());
        return evaluationRepository.save(evaluation);
    }

    public Evaluation createEvaluation(String groupId, String designation) {
        return createEvaluation(groupId, designation, Evaluation.TYPE_CONTROLE_CONTINU);
    }

    public static void validateBareme(double scoreCorrect, double scoreEmpty, double scoreIncorrect) {
        if (Double.isNaN(scoreCorrect) || Double.isInfinite(scoreCorrect) || scoreCorrect <= 0) {
            throw new IllegalArgumentException("La note pour une réponse correcte doit être strictement positive (> 0). Valeur reçue : " + scoreCorrect);
        }
        if (Double.isNaN(scoreIncorrect) || Double.isInfinite(scoreIncorrect) || scoreIncorrect > 0) {
            throw new IllegalArgumentException("La note pour une réponse fausse doit être 0 ou négative (≤ 0). Valeur reçue : " + scoreIncorrect);
        }
        if (Double.isNaN(scoreEmpty) || Double.isInfinite(scoreEmpty)) {
            throw new IllegalArgumentException("La note pour « sans réponse » doit être un nombre valide.");
        }
    }

    public Evaluation saveEvaluation(Evaluation evaluation) {
        if (evaluation != null && evaluation.getId() != null) {
            evaluationRepository.findById(evaluation.getId()).ifPresent(existing -> {
                if (existing.getState() != EvaluationState.CONCEPTION && evaluation.getState() == existing.getState()) {
                    throw new InvalidEvaluationStateException("Une évaluation dont le statut est « " + existing.getState() + " » ne peut plus être modifiée.");
                }
            });
        }
        if (evaluation != null) {
            validateBareme(evaluation.getDefaultScoreIfCorrect(), evaluation.getDefaultScoreIfEmpty(), evaluation.getDefaultScoreIfIncorrect());
            if (evaluation.isSameScore() && evaluation.getQuestions() != null) {
                List<Question> syncedQuestions = new ArrayList<>();
                for (Question q : evaluation.getQuestions()) {
                    syncedQuestions.add(new Question(
                            q.id(),
                            q.content(),
                            q.imagePath(),
                            q.multipleChoice(),
                            evaluation.getDefaultScoreIfCorrect(),
                            evaluation.getDefaultScoreIfEmpty(),
                            evaluation.getDefaultScoreIfIncorrect(),
                            q.answers()
                    ));
                }
                evaluation.setQuestions(syncedQuestions);
            }
            if (evaluation.getQuestions() != null) {
                for (Question q : evaluation.getQuestions()) {
                    validateBareme(q.scoreIfCorrect(), q.scoreIfEmpty(), q.scoreIfIncorrect());
                }
            }
        }
        return evaluationRepository.save(evaluation);
    }

    @Transactional
    public void deleteEvaluation(String evaluationId) {
        log.info("Suppression en cascade de l'évaluation {}", evaluationId);

        // 1. Suppression des copies / blueprints générés en base
        try {
            blueprintPaperAssessmentRepository.deleteByEvaluationId(evaluationId);
        } catch (Exception e) {
            log.warn("Erreur lors de la suppression des blueprints pour l'évaluation {}", evaluationId, e);
        }

        // 2. Suppression des pages non corrigées en base
        try {
            uncorrectedPageRepository.deleteByEvaluationId(evaluationId);
        } catch (Exception e) {
            log.warn("Erreur lors de la suppression des pages non corrigées pour l'évaluation {}", evaluationId, e);
        }

        // 3. Suppression de l'évaluation elle-même
        evaluationRepository.deleteById(evaluationId);

        // 4. Suppression des fichiers et dossiers d'artefacts sur le disque
        try {
            Path evalDir = Path.of(uploadDirPath, "evaluations", evaluationId);
            if (Files.exists(evalDir)) {
                FileUtils.deleteDirectory(evalDir.toFile());
                log.info("Répertoire des artefacts supprimé : {}", evalDir.toAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("Impossible de supprimer le répertoire des artefacts pour l'évaluation {}", evaluationId, e);
        }
    }

    public Evaluation addQuestion(String evaluationId, Question question) {
        Evaluation evaluation = getEvaluation(evaluationId);
        if (evaluation.getState() != EvaluationState.CONCEPTION) {
            throw new InvalidEvaluationStateException("Une évaluation dont le statut est « " + evaluation.getState() + " » ne peut plus être modifiée.");
        }
        double sc = (evaluation.isSameScore() && question != null) ? evaluation.getDefaultScoreIfCorrect() : (question != null ? question.scoreIfCorrect() : 1.0);
        double se = (evaluation.isSameScore() && question != null) ? evaluation.getDefaultScoreIfEmpty() : (question != null ? question.scoreIfEmpty() : 0.0);
        double si = (evaluation.isSameScore() && question != null) ? evaluation.getDefaultScoreIfIncorrect() : (question != null ? question.scoreIfIncorrect() : 0.0);
        validateBareme(sc, se, si);

        List<Question> questions = new ArrayList<>(evaluation.getQuestions());
        question = new Question(
                (question == null || question.id() == null || question.id().isBlank()) ? UUID.randomUUID().toString() : question.id(),
                question != null ? question.content() : "",
                question != null ? question.imagePath() : null,
                question != null && question.multipleChoice(),
                sc,
                se,
                si,
                question != null ? question.answers() : List.of()
        );
        questions.add(question);
        evaluation.setQuestions(questions);
        return evaluationRepository.save(evaluation);
    }

    public Evaluation removeQuestion(String evaluationId, String questionId) {
        Evaluation evaluation = getEvaluation(evaluationId);
        if (evaluation.getState() != EvaluationState.CONCEPTION) {
            throw new InvalidEvaluationStateException("Une évaluation dont le statut est « " + evaluation.getState() + " » ne peut plus être modifiée.");
        }
        List<Question> questions = new ArrayList<>(evaluation.getQuestions());
        questions.removeIf(q -> q.id().equals(questionId));
        evaluation.setQuestions(questions);
        return evaluationRepository.save(evaluation);
    }

    public Evaluation updateQuestion(String evaluationId, Question updated) {
        Evaluation evaluation = getEvaluation(evaluationId);
        if (evaluation.getState() != EvaluationState.CONCEPTION) {
            throw new InvalidEvaluationStateException("Une évaluation dont le statut est « " + evaluation.getState() + " » ne peut plus être modifiée.");
        }
        List<Question> questions = new ArrayList<>(evaluation.getQuestions());
        for (int i = 0; i < questions.size(); i++) {
            if (questions.get(i).id().equals(updated.id())) {
                questions.set(i, updated);
                break;
            }
        }
        evaluation.setQuestions(questions);
        return evaluationRepository.save(evaluation);
    }

    public Evaluation setEvaluationState(String evaluationId, EvaluationState state) {
        Evaluation evaluation = getEvaluation(evaluationId);
        evaluation.setState(state);
        return evaluationRepository.save(evaluation);
    }

    @Transactional
    public Evaluation resetToConception(String evaluationId) {
        log.info("Réinitialisation en mode conception de l'évaluation {}", evaluationId);
        Evaluation evaluation = getEvaluation(evaluationId);
        if (evaluation.getState() == EvaluationState.CONCEPTION) {
            return evaluation;
        }

        // 1. Suppression des copies / blueprints générés en base
        try {
            blueprintPaperAssessmentRepository.deleteByEvaluationId(evaluationId);
        } catch (Exception e) {
            log.warn("Erreur lors de la suppression des blueprints pour l'évaluation {}", evaluationId, e);
        }

        // 2. Suppression des pages non corrigées en base
        try {
            uncorrectedPageRepository.deleteByEvaluationId(evaluationId);
        } catch (Exception e) {
            log.warn("Erreur lors de la suppression des pages non corrigées pour l'évaluation {}", evaluationId, e);
        }

        // 3. Suppression des fichiers et dossiers de production sur le disque
        try {
            Path evalDir = Path.of(uploadDirPath, "evaluations", evaluationId);
            if (Files.exists(evalDir)) {
                FileUtils.deleteDirectory(evalDir.toFile());
                log.info("Répertoire des artefacts de production supprimé : {}", evalDir.toAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("Impossible de supprimer le répertoire des artefacts pour l'évaluation {}", evaluationId, e);
        }

        // 4. Réinitialisation de l'état
        evaluation.setState(EvaluationState.CONCEPTION);
        return evaluationRepository.save(evaluation);
    }

    /**
     * Clone une question en générant de nouveaux identifiants uniques (UUID) pour la question
     * et l'ensemble de ses réponses, tout en permettant éventuellement de surcharger le barème.
     */
    public Question cloneQuestion(Question source, Double overrideScoreCorrect, Double overrideScoreEmpty, Double overrideScoreIncorrect) {
        if (source == null) return null;
        List<Answer> clonedAnswers = new ArrayList<>();
        if (source.answers() != null) {
            for (Answer a : source.answers()) {
                clonedAnswers.add(new Answer(
                        UUID.randomUUID().toString(),
                        a.content(),
                        a.correct()
                ));
            }
        }

        double scoreCorrect = overrideScoreCorrect != null ? overrideScoreCorrect : source.scoreIfCorrect();
        double scoreEmpty = overrideScoreEmpty != null ? overrideScoreEmpty : source.scoreIfEmpty();
        double scoreIncorrect = overrideScoreIncorrect != null ? overrideScoreIncorrect : source.scoreIfIncorrect();

        return new Question(
                UUID.randomUUID().toString(),
                source.content(),
                source.imagePath(),
                source.multipleChoice(),
                scoreCorrect,
                scoreEmpty,
                scoreIncorrect,
                clonedAnswers
        );
    }

    /**
     * Clone une liste de questions en régénérant les identifiants uniques.
     */
    public List<Question> cloneQuestions(List<Question> sources, Double overrideScoreCorrect, Double overrideScoreEmpty, Double overrideScoreIncorrect) {
        if (sources == null || sources.isEmpty()) return new ArrayList<>();
        List<Question> clonedList = new ArrayList<>(sources.size());
        for (Question q : sources) {
            clonedList.add(cloneQuestion(q, overrideScoreCorrect, overrideScoreEmpty, overrideScoreIncorrect));
        }
        return clonedList;
    }
}
