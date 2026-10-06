package tk.tahri.eval.desktop.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tk.tahri.eval.desktop.model.Enrollment;
import tk.tahri.eval.desktop.model.GroupRole;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class StudentExcelService {

    private static final Logger log = LoggerFactory.getLogger(StudentExcelService.class);

    public record ImportedStudent(String matriculation, String lastName, String firstName, String email) {
    }

    public List<ImportedStudent> parseStudentsFromExcel(InputStream inputStream) throws IOException {
        List<ImportedStudent> students = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            int startRow = 1; // 0 is header
            for (int r = startRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String matricule = formatter.formatCellValue(row.getCell(0)).trim();
                String nom = formatter.formatCellValue(row.getCell(1)).trim();
                String prenom = formatter.formatCellValue(row.getCell(2)).trim();
                String email = row.getCell(3) != null ? formatter.formatCellValue(row.getCell(3)).trim() : "";

                if (!matricule.isEmpty() && (!nom.isEmpty() || !prenom.isEmpty())) {
                    students.add(new ImportedStudent(matricule, nom, prenom, email));
                }
            }
        }
        return students;
    }

    public void exportStudentsToExcel(List<Enrollment> students, String courseTitle, File targetFile) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Étudiants");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(24);

            String[] headers = {"Matricule", "Nom", "Prénom", "Rôle"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Enrollment s : students) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(s.getMatriculation() != null ? s.getMatriculation() : "");
                row.createCell(1).setCellValue(s.getLastName() != null ? s.getLastName() : "");
                row.createCell(2).setCellValue(s.getFirstName() != null ? s.getFirstName() : "");
                row.createCell(3).setCellValue(s.getRole() != null ? s.getRole().name() : GroupRole.STUDENT.name());
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                workbook.write(fos);
            }
        }
    }
}
