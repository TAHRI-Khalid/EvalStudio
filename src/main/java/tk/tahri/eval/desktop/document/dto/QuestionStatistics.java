package tk.tahri.eval.desktop.document.dto;

public record QuestionStatistics(
        String questionId,
        int questionNumber,
        String content,
        long correctCount,
        long emptyCount,
        long incorrectCount,
        long totalEvaluated,
        double successRate
) {
}
