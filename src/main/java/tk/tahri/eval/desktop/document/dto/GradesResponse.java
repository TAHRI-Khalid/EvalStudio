package tk.tahri.eval.desktop.document.dto;

import java.util.List;

public record GradesResponse(
        GradeStatistics statistics,
        List<StudentGrade> students
) {
}
