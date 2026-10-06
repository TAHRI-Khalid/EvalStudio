package tk.tahri.eval.desktop.document.reports;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import tk.tahri.eval.desktop.document.Document;
import tk.tahri.eval.desktop.document.Place;
import tk.tahri.eval.desktop.document.TextAlign;
import tk.tahri.eval.desktop.document.dto.StudentDetails;
import tk.tahri.eval.desktop.document.entities.Setting;
import tk.tahri.eval.desktop.exception.FirstPageHeaderException;
import tk.tahri.eval.desktop.model.Group;
import tk.tahri.eval.desktop.model.Evaluation;

import java.io.IOException;
import java.util.List;

public class CorrectionReport extends Document {

    private final Evaluation evaluation;
    private final Group group;
    private final List<StudentDetails> studentDetailsList;

    public CorrectionReport(Evaluation evaluation, Group group, List<StudentDetails> studentDetailsList, PDDocument document, PDFont font) {
        super(document, font);
        this.evaluation = evaluation;
        this.group = group;
        this.studentDetailsList = studentDetailsList;
    }

    private float drawFirstPageHeader(PDPageContentStream canvas) {
        try {
            var yOffset = pdRectangle.getHeight() - Setting.margin;
            var headerHeight = 0f;
            final var headerContentWidth = this.pdRectangle.getWidth() - (2 * Setting.margin);

            if (group.logoUrl() != null && !group.logoUrl().isBlank()) {
                var imagePlace = new Place(Setting.margin * 2, yOffset, headerContentWidth - (2 * Setting.margin));
                var logoImg = loadImage(logoFolder(), group.logoUrl());
                if (logoImg != null) {
                    headerHeight += this.drawImage(canvas, logoImg, imagePlace, 60);
                } else {
                    headerHeight = Setting.margin / 2f;
                }
            } else {
                headerHeight = Setting.margin / 2f;
            }

            var institutePlace = new Place(Setting.margin, yOffset - headerHeight, headerContentWidth);
            headerHeight += drawParagraph(canvas, group.etablissement() != null ? group.etablissement() : "", institutePlace, TextAlign.CENTER, Setting.fontSize);

            var informationHeight = 0f;
            var typePlace = new Place(Setting.margin, yOffset - headerHeight - informationHeight, headerContentWidth);
            informationHeight += drawParagraph(canvas, evaluation.type() != null ? evaluation.type() : "", typePlace, TextAlign.CENTER, Setting.fontSize);

            var sectionPlace = new Place(Setting.margin, yOffset - headerHeight - informationHeight, headerContentWidth);
            informationHeight += drawParagraph(canvas, group.section() != null ? group.section() : "", sectionPlace, TextAlign.CENTER, Setting.fontSize);

            var designationPlace = new Place(Setting.margin, yOffset - headerHeight - informationHeight, headerContentWidth);
            informationHeight += drawParagraph(canvas, evaluation.designation() != null ? evaluation.designation() : "", designationPlace, TextAlign.CENTER, Setting.fontSize);

            var yearPlace = new Place(Setting.margin, yOffset - headerHeight - informationHeight, headerContentWidth);
            informationHeight += drawParagraph(canvas, group.year() != null ? group.year() : "", yearPlace, TextAlign.CENTER, Setting.fontSize);

            drawRectangle(canvas, Setting.margin, yOffset - headerHeight - (0.2f * Setting.lineSpacing), headerContentWidth, informationHeight);
            headerHeight += informationHeight;

            var groupPlace = new Place(Setting.margin, yOffset - headerHeight, headerContentWidth / 2);
            drawParagraph(canvas, "Groupe : " + (group.name() != null ? group.name() : ""), groupPlace, TextAlign.CENTER, Setting.fontSize);

            var durationPlace = new Place(Setting.margin + headerContentWidth / 2, yOffset - headerHeight, headerContentWidth / 2);
            int durationMin = evaluation.duration() != null ? (int) evaluation.duration().toMinutes() : 60;
            headerHeight += drawParagraph(canvas, "Durée : " + durationToString(durationMin), durationPlace, TextAlign.CENTER, Setting.fontSize);

            var titlePlace = new Place(Setting.margin, yOffset - headerHeight, headerContentWidth);
            headerHeight += drawParagraph(canvas, "PV de Notes", titlePlace, TextAlign.CENTER, 16);

            return headerHeight;
        } catch (IOException e) {
            throw new FirstPageHeaderException(e);
        }
    }

    public void process() throws Exception {
        var canvas = addPageToTheDocument(false, false);
        float yOffset = pdRectangle.getHeight() - Setting.margin;
        final float width = pdRectangle.getWidth() - 2 * Setting.margin;
        yOffset -= drawFirstPageHeader(canvas) + 16;

        drawTableHeader(canvas, yOffset, width);
        yOffset -= 20;

        for (var student : studentDetailsList) {
            if (yOffset - 20 < Setting.margin) {
                canvas.close();
                canvas = addPageToTheDocument(false, false);
                yOffset = pdRectangle.getHeight() - Setting.margin;
                drawTableHeader(canvas, yOffset, width);
                yOffset -= 20;
            }
            drawTableRow(canvas, yOffset, width, student);
            yOffset -= 20;
        }
        canvas.close();
    }

    private void drawTableHeader(PDPageContentStream canvas, float yOffset, float width) throws IOException {
        drawRectangle(canvas, Setting.margin, yOffset, width / 5, 20);
        drawParagraph(canvas, "ID", new Place(Setting.margin, yOffset, width / 5), TextAlign.CENTER, Setting.fontSize);

        drawRectangle(canvas, Setting.margin + width / 5, yOffset, width / 5, 20);
        drawParagraph(canvas, "Nom", new Place(Setting.margin + width / 5, yOffset, width / 5), TextAlign.CENTER, Setting.fontSize);

        drawRectangle(canvas, Setting.margin + (width * 2) / 5, yOffset, width / 5, 20);
        drawParagraph(canvas, "Prénom", new Place(Setting.margin + (width * 2) / 5, yOffset, width / 5), TextAlign.CENTER, Setting.fontSize);

        drawRectangle(canvas, Setting.margin + (width * 3) / 5, yOffset, width / 5, 20);
        drawParagraph(canvas, "Note", new Place(Setting.margin + (width * 3) / 5, yOffset, width / 5), TextAlign.CENTER, Setting.fontSize);

        drawRectangle(canvas, Setting.margin + (width * 4) / 5, yOffset, width / 5, 20);
        drawParagraph(canvas, "Observation", new Place(Setting.margin + (width * 4) / 5, yOffset, width / 5), TextAlign.CENTER, Setting.fontSize);
    }

    private void drawTableRow(PDPageContentStream canvas, float yOffset, float width, StudentDetails student) throws IOException {
        final int space = 5;

        String matricule = student.matriculation() != null ? student.matriculation() : "";
        String nom = student.lastName() != null ? student.lastName() : "";
        String prenom = student.firstName() != null ? student.firstName() : "";

        drawRectangle(canvas, Setting.margin, yOffset, width / 5, 20);
        drawParagraph(canvas, matricule, new Place(space + Setting.margin, yOffset, width / 5), TextAlign.LEFT, Setting.fontSize);

        drawRectangle(canvas, Setting.margin + width / 5, yOffset, width / 5, 20);
        drawParagraph(canvas, nom, new Place(space + Setting.margin + width / 5, yOffset, width / 5), TextAlign.LEFT, Setting.fontSize);

        drawRectangle(canvas, Setting.margin + (width * 2) / 5, yOffset, width / 5, 20);
        drawParagraph(canvas, prenom, new Place(space + Setting.margin + (width * 2) / 5, yOffset, width / 5), TextAlign.LEFT, Setting.fontSize);

        drawRectangle(canvas, Setting.margin + (width * 3) / 5, yOffset, width / 5, 20);
        drawParagraph(canvas, String.format("%.2f", student.score()), new Place(Setting.margin + (width * 3) / 5, yOffset, width / 5), TextAlign.CENTER, Setting.fontSize);

        drawRectangle(canvas, Setting.margin + (width * 4) / 5, yOffset, width / 5, 20);
        drawParagraph(canvas, "", new Place(Setting.margin + (width * 4) / 5, yOffset, width / 5), TextAlign.CENTER, Setting.fontSize);
    }
}
