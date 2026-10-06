package tk.tahri.eval.desktop.document.reports;

import org.apache.pdfbox.pdmodel.PDPageContentStream;
import tk.tahri.eval.desktop.document.Place;
import tk.tahri.eval.desktop.document.SharedAssessmentData;
import tk.tahri.eval.desktop.document.entities.Setting;
import tk.tahri.eval.desktop.document.entities.blueprint.AssessmentStudent;
import tk.tahri.eval.desktop.document.entities.blueprint.BlueprintPaperAssessment;
import tk.tahri.eval.desktop.model.Answer;
import tk.tahri.eval.desktop.model.Question;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class TwoColumnAssessment {
    private final SharedAssessmentData sharedData;
    private final AssessmentStudent student;
    private final BlueprintPaperAssessment blueprintAssessment;

    public TwoColumnAssessment(SharedAssessmentData sharedAssessmentData, AssessmentStudent student) {
        this.sharedData = sharedAssessmentData;
        this.student = student;
        this.blueprintAssessment = new BlueprintPaperAssessment(sharedData.evaluation, student);
    }

    public void addPagesToDocument() throws Exception {
        float xOffset = Setting.margin;
        float yOffset = sharedData.pdRectangle.getHeight() - Setting.margin;
        final float width = (sharedData.pdRectangle.getWidth() / 2 - Setting.margin) * 0.95f;
        int pageNumber = 1;

        List<Question> questions = new ArrayList<>(sharedData.evaluation.questions());
        if (sharedData.evaluation.questionShuffle()) Collections.shuffle(questions);
        if (sharedData.evaluation.answersShuffle()) questions = questions.stream().map(TwoColumnAssessment::shuffled).toList();

        var canvas = sharedData.addPageToTheDocument(true, sharedData.draft);
        this.blueprintAssessment.addBluePage();

        yOffset -= sharedData.drawFirstPageHeader(canvas, student);
        yOffset -= Setting.lineSpacing * 0.5f;
        var headerHeight = yOffset;

        for (int questionNumber = 0; questionNumber < questions.size(); questionNumber++) {
            var question = questions.get(questionNumber);

            var questionPlace = new Place(xOffset, yOffset, width);
            var questionHeight = sharedData.getQuestionHeight(canvas, questionNumber + 1, question, questionPlace);

            if (yOffset - questionHeight - 16 < Setting.margin) {
                if (xOffset > sharedData.pdRectangle.getWidth() / 2) {
                    canvas.close();
                    canvas = sharedData.addPageToTheDocument(true, sharedData.draft);
                    this.blueprintAssessment.addBluePage();
                    pageNumber++;
                    xOffset = Setting.margin;
                    yOffset = sharedData.pdRectangle.getHeight() - Setting.margin;
                    yOffset -= sharedData.drawOtherPagesHeader(canvas, student);
                    drawLineBetweenColumns(canvas, yOffset);
                } else {
                    xOffset = (sharedData.pdRectangle.getWidth() / 2) + (sharedData.pdRectangle.getWidth() / 2 - Setting.margin) * 0.05f;
                    if (pageNumber == 1) {
                        yOffset = headerHeight;
                        drawLineBetweenColumns(canvas, yOffset);
                    } else {
                        yOffset = sharedData.pdRectangle.getHeight() - Setting.margin;
                        yOffset -= Setting.lineSpacing;
                    }
                }
                questionPlace = new Place(xOffset, yOffset, width);
            }
            var blueprintQuestion = sharedData.drawQuestion(canvas, questionNumber + 1, question, questionPlace);
            this.blueprintAssessment.addBlueQuestion(blueprintQuestion);
            yOffset -= questionHeight;
            yOffset -= Setting.lineSpacing * 0.5f;
        }

        canvas.close();
        if (sharedData.draft) {
            sharedData.addPageNumbersAndBarcode(pageNumber, UUID.randomUUID().toString());
        } else {
            sharedData.addPageNumbersAndBarcode(pageNumber, this.blueprintAssessment.getId());
        }

        if (this.sharedData.evaluation.doubleSided() && pageNumber != 1 && pageNumber % 2 != 0) {
            var c = sharedData.addPageToTheDocument(false, false);
            c.close();
        }
    }

    private static Question shuffled(Question question) {
        List<Answer> answers = new ArrayList<>(question.answers());
        Collections.shuffle(answers);
        return new Question(question.id(), question.content(), question.imagePath(), question.multipleChoice(),
                question.scoreIfCorrect(), question.scoreIfEmpty(), question.scoreIfIncorrect(), answers);
    }

    private void drawLineBetweenColumns(PDPageContentStream canvas, float yOffset) throws IOException {
        canvas.setLineWidth(1);
        canvas.moveTo(sharedData.pdRectangle.getWidth() / 2, yOffset);
        canvas.lineTo(sharedData.pdRectangle.getWidth() / 2, Setting.margin);
        canvas.stroke();
    }

    public BlueprintPaperAssessment getBlueprintAssessment() {
        return this.blueprintAssessment;
    }
}
