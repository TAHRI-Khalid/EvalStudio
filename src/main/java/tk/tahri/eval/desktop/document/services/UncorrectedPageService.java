package tk.tahri.eval.desktop.document.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tk.tahri.eval.desktop.document.entities.UncorrectedPage;
import tk.tahri.eval.desktop.document.repositories.UncorrectedPageRepository;

import java.util.List;

@Service
@Transactional
public class UncorrectedPageService {

    private final UncorrectedPageRepository uncorrectedPageRepository;

    public UncorrectedPageService(UncorrectedPageRepository uncorrectedPageRepository) {
        this.uncorrectedPageRepository = uncorrectedPageRepository;
    }

    public UncorrectedPage save(UncorrectedPage uncorrectedPage) {
        return uncorrectedPageRepository.save(uncorrectedPage);
    }

    public List<UncorrectedPage> findByEvaluationId(String evaluationId) {
        return uncorrectedPageRepository.findByEvaluationId(evaluationId);
    }

    public boolean hasUncorrectedPages(String evaluationId) {
        return !uncorrectedPageRepository.findByEvaluationId(evaluationId).isEmpty();
    }

    public void deleteByEvaluationId(String evaluationId) {
        uncorrectedPageRepository.deleteByEvaluationId(evaluationId);
    }
}
