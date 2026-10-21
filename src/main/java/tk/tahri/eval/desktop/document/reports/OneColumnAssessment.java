package tk.tahri.eval.desktop.document.reports;

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

public class OneColumnAssessment {
    private final SharedAssessmentData sharedData;
    private final AssessmentStudent student;
    private final BlueprintPaperAssessment blueprintAssessment;

    public OneColumnAssessment(SharedAssessmentData sharedAssessmentData, AssessmentStudent student) {
        this.sharedData = sharedAssessmentData;
        this.student = student;
        this.blueprintAssessment = new BlueprintPaperAssessment(sharedData.evaluation, student);
    }

    public void addPagesToDocument() throws IOException {
        final float xOffset = Setting.margin;
        float yOffset = sharedData.pdRectangle.getHeight() - Setting.margin;
        final float width = sharedData.pdRectangle.getWidth() - 2 * Setting.margin;
        int pageNumber = 1;

        List<Question> questions = new ArrayList<>(sharedData.evaluation.questions());
        if (sharedData.evaluation.questionShuffle()) Collections.shuffle(questions);
        if (sharedData.evaluation.answersShuffle()) questions = questions.stream().map(OneColumnAssessment::shuffled).toList();

        var canvas = sharedData.addPageToTheDocument(true, sharedData.draft);
        this.blueprintAssessment.addBluePage();

        yOffset -= sharedData.drawFirstPageHeader(canvas, student);
        yOffset -= Setting.lineSpacing * 0.5f;

        for (int questionNumber = 0; questionNumber < questions.size(); questionNumber++) {
            var question = questions.get(questionNumber);

            var questionPlace = new Place(xOffset, yOffset, width);
            var questionHeight = sharedData.getQuestionHeight(canvas, questionNumber + 1, question, questionPlace);

            if (yOffset - questionHeight - 16 < Setting.margin) {
                canvas.close();
                canvas = sharedData.addPageToTheDocument(true, sharedData.draft);
                this.blueprintAssessment.addBluePage();
                pageNumber++;
                yOffset = sharedData.pdRectangle.getHeight() - Setting.margin;
                yOffset -= sharedData.drawOtherPagesHeader(canvas, student);
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

        if (this.sharedData.evaluation.doubleSided() && pageNumber % 2 != 0) {
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

    public BlueprintPaperAssessment getBlueprintAssessment() {
        return this.blueprintAssessment;
    }
}
