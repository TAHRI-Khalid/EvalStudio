package tk.tahri.eval.desktop.document.dto;

public record GradeStatistics(
        Double max,
        Double min,
        Double average,
        Double median,
        Double variance,
        Double stdDeviation
) {
}
