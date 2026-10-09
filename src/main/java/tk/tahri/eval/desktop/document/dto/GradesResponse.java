package tk.tahri.eval.desktop.document.dto;

import java.util.Collections;
import java.util.List;

public record GradesResponse(
        GradeStatistics statistics,
        List<StudentGrade> students,
        List<QuestionStatistics> questions
) {
    public GradesResponse(GradeStatistics statistics, List<StudentGrade> students) {
        this(statistics, students, Collections.emptyList());
    }
}
