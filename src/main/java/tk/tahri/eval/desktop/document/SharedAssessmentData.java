package tk.tahri.eval.desktop.document;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tk.tahri.eval.desktop.document.entities.Setting;
import tk.tahri.eval.desktop.document.entities.blueprint.AssessmentStudent;
import tk.tahri.eval.desktop.document.entities.blueprint.BlueprintAnswer;
import tk.tahri.eval.desktop.document.entities.blueprint.BlueprintQuestion;
import tk.tahri.eval.desktop.exception.FirstPageHeaderException;
import tk.tahri.eval.desktop.exception.MeasureOrDrawQuestionException;
import tk.tahri.eval.desktop.exception.OthersPagesHeaderException;
import tk.tahri.eval.desktop.model.Group;
import tk.tahri.eval.desktop.model.Evaluation;
import tk.tahri.eval.desktop.model.Question;

import java.awt.Color;
import java.io.IOException;
import java.util.Objects;

public class SharedAssessmentData extends Document {

    private static final Logger log = LoggerFactory.getLogger(SharedAssessmentData.class);

    public final Evaluation evaluation;
    public final Group group;
    public final Group course;
    public final boolean draft;

    public SharedAssessmentData(PDDocument document, boolean draft, Evaluation evaluation, Group group, PDFont font) {
        super(document, font);
        this.evaluation = evaluation;
        this.group = group;
        this.course = group;
        this.draft = draft;
    }

    private int durationMinutes() {
        return evaluation.duration() != null ? (int) evaluation.duration().toMinutes() : 60;
    }

    public float drawFirstPageHeader(PDPageContentStream canvas, AssessmentStudent student) {
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

            var groupPlace = new Place(Setting.margin, yOffset - headerHeight, headerContentWidth / 3);
            drawParagraph(canvas, "Groupe : " + (group.name() != null ? group.name() : ""), groupPlace, TextAlign.CENTER, Setting.fontSize);

            var durationPlace = new Place(Setting.margin + headerContentWidth / 3, yOffset - headerHeight, headerContentWidth / 3);
            drawParagraph(canvas, "Durée : " + durationToString(durationMinutes()), durationPlace, TextAlign.CENTER, Setting.fontSize);

            var namePlace = new Place(Setting.margin + (headerContentWidth * 2) / 3, yOffset - headerHeight, headerContentWidth / 3);
            headerHeight += drawParagraph(canvas, "Nom : " + student.fullName(), namePlace, TextAlign.CENTER, Setting.fontSize);

            if (evaluation.sameScore()) {
                var str = "Réponse correcte : %s   |   Pas de Réponse : %s   |   Réponse incorrecte : %s".formatted(
                        evaluation.defaultScoreIfCorrect(), evaluation.defaultScoreIfEmpty(), evaluation.defaultScoreIfIncorrect());
                drawRectangle(canvas, Setting.margin, yOffset - headerHeight - 0.2f * Setting.lineSpacing, headerContentWidth, Setting.lineSpacing);
                var marksPlace = new Place(Setting.margin, yOffset - headerHeight, headerContentWidth);
                headerHeight += drawParagraph(canvas, str, marksPlace, TextAlign.CENTER, Setting.fontSize);
            }
            return headerHeight;
        } catch (IOException e) {
            throw new FirstPageHeaderException(e);
        }
    }

    public float drawOtherPagesHeader(PDPageContentStream canvas, AssessmentStudent student) {
        try {
            var yOffset = pdRectangle.getHeight() - Setting.margin + (Setting.lineSpacing / 2);
            var headerHeight = 0f;
            final var headerContentWidth = this.pdRectangle.getWidth() - (2 * Setting.margin);

            var namePlace = new Place(Setting.margin, yOffset + (Setting.fontSize * 0.3f), headerContentWidth);
            var info = "";
            if (student.matriculation() != null && !student.matriculation().isBlank()) {
                info = "ID : " + student.matriculation() + " | ";
            }
            var informationHeight = drawParagraph(canvas, info + "Nom : " + student.fullName(), namePlace, TextAlign.CENTER, Setting.fontSize);

            drawRectangle(canvas, (2 * Setting.margin), yOffset, headerContentWidth - (2 * Setting.margin), informationHeight);
            headerHeight += informationHeight;

            return headerHeight;
        } catch (IOException e) {
            throw new OthersPagesHeaderException(e);
        }
    }

    public BlueprintQuestion drawQuestion(PDPageContentStream canvas, int questionNumber, Question question, Place place) {
        var blueprintQuestion = new BlueprintQuestion(question, questionNumber);
        question(canvas, questionNumber, question, place, blueprintQuestion);
        return blueprintQuestion;
    }

    public float getQuestionHeight(PDPageContentStream canvas, int questionNumber, Question question, Place place) {
        return question(canvas, questionNumber, question, place, null);
    }

    private float question(PDPageContentStream canvas, int questionNumber, Question question, Place place, BlueprintQuestion blueprintQuestion) {
        try {
            float questionHeight;
            if (Objects.nonNull(blueprintQuestion)) {
                questionHeight = drawParagraph(canvas, questionNumber + ") " + question.content(), place, TextAlign.LEFT, Setting.fontSize);
            } else {
                questionHeight = getParagraphHeight(canvas, questionNumber + ") " + question.content(), place, TextAlign.LEFT, Setting.fontSize);
            }

            questionHeight += Setting.paragraphSpacing;

            if (question.imagePath() != null && !question.imagePath().isBlank()) {
                var imagePlace = new Place(place.x(), place.y() - questionHeight, place.width());
                var image = loadImage(questionImagesFolder(), question.imagePath());
                if (Objects.nonNull(blueprintQuestion)) {
                    questionHeight += drawImage(canvas, image, imagePlace, Setting.maxImageHeight);
                } else {
                    questionHeight += getImageHeight(canvas, image, imagePlace, Setting.maxImageHeight);
                }
                questionHeight += Setting.paragraphSpacing;
            }

            final var r = Setting.checkBox / 2;
            var checkboxMargin = Setting.checkBox;
            if (question.answers() != null) {
                for (var answer : question.answers()) {
                    var x = place.x() + checkboxMargin;
                    var y = place.y() - questionHeight;
                    var paragraphPlace = new Place(x + checkboxMargin, y, place.width() - (checkboxMargin * 2));

                    if (Objects.nonNull(blueprintQuestion)) {
                        if (question.answers().stream().filter(a -> a.correct()).count() > 1) {
                            drawCheckBox(canvas, x, y - Setting.fontSize, r);
                        } else {
                            drawCircle(canvas, x, y - Setting.fontSize, r, 0.8f);
                        }
                        questionHeight += drawParagraph(canvas, answer.content(), paragraphPlace, TextAlign.LEFT, Setting.fontSize);

                        var blueprintAnswers = new BlueprintAnswer(answer, x, y - Setting.fontSize);
                        blueprintQuestion.blueprintAnswers.add(blueprintAnswers);
                    } else {
                        questionHeight += getParagraphHeight(canvas, answer.content(), paragraphPlace, TextAlign.LEFT, Setting.fontSize);
                    }
                    questionHeight += Setting.answerSpacing;
                }
            }
            return questionHeight;
        } catch (IOException e) {
            throw new MeasureOrDrawQuestionException(e);
        }
    }

    public void drawBlueprintQuestion(PDPageContentStream canvas, int questionNumber, BlueprintQuestion question, Place place) {
        blueprintQuestion(canvas, questionNumber, question, place, true);
    }

    public float getBlueprintQuestionHeight(PDPageContentStream canvas, int questionNumber, BlueprintQuestion question, Place place) {
        return blueprintQuestion(canvas, questionNumber, question, place, false);
    }

    private float blueprintQuestion(PDPageContentStream canvas, int questionNumber, BlueprintQuestion question, Place place, boolean sideEffect) {
        try {
            float questionHeight;
            if (sideEffect) {
                questionHeight = drawParagraph(canvas, questionNumber + ") " + question.content, place, TextAlign.LEFT, Setting.fontSize);
            } else {
                questionHeight = getParagraphHeight(canvas, questionNumber + ") " + question.content, place, TextAlign.LEFT, Setting.fontSize);
            }

            questionHeight += Setting.paragraphSpacing;

            if (question.imagePath != null && !question.imagePath.isBlank()) {
                var imagePlace = new Place(place.x(), place.y() - questionHeight, place.width());
                var image = loadImage(questionImagesFolder(), question.imagePath);
                if (sideEffect) {
                    questionHeight += drawImage(canvas, image, imagePlace, Setting.maxImageHeight);
                } else {
                    questionHeight += getImageHeight(canvas, image, imagePlace, Setting.maxImageHeight);
                }
                questionHeight += Setting.paragraphSpacing;
            }

            final var r = Setting.checkBox / 2;
            var checkboxMargin = Setting.checkBox;
            for (var answer : question.blueprintAnswers) {
                var x = place.x() + checkboxMargin;
                var y = place.y() - questionHeight;
                var paragraphPlace = new Place(x + checkboxMargin, y, place.width() - (checkboxMargin * 2));

                if (sideEffect) {
                    if (question.blueprintAnswers.stream().filter(a -> a.correct).count() > 1) {
                        drawCheckBox(canvas, x, y - Setting.fontSize, r);
                    } else {
                        drawCircle(canvas, x, y - Setting.fontSize, r, 0.8f);
                    }
                    if (answer.response) {
                        drawX(canvas, x, y - Setting.fontSize, 2 * r / 3);
                    }
                    answer.x = x;
                    answer.y = y - Setting.fontSize;
                    questionHeight += drawParagraph(canvas, answer.content, paragraphPlace, TextAlign.LEFT, Setting.fontSize);
                } else {
                    questionHeight += getParagraphHeight(canvas, answer.content, paragraphPlace, TextAlign.LEFT, Setting.fontSize);
                }
                questionHeight += Setting.answerSpacing;
            }
            if (sideEffect && !question.blueprintAnswers.isEmpty()) {
                drawCorrectionLine(canvas, question);
            }
            return questionHeight;
        } catch (IOException e) {
            throw new MeasureOrDrawQuestionException(e);
        }
    }

    private void drawCorrectionLine(PDPageContentStream canvas, BlueprintQuestion question) throws IOException {
        if (question.blueprintAnswers.isEmpty()) return;
        canvas.setLineWidth(2f);
        var color = switch (question.state != null ? question.state : tk.tahri.eval.desktop.document.entities.blueprint.QuestionState.EMPTY) {
            case EMPTY -> Color.orange;
            case CORRECT -> Color.green;
            case INCORRECT -> Color.red;
        };
        canvas.setStrokingColor(color);
        canvas.moveTo(question.blueprintAnswers.getFirst().x - 15, question.blueprintAnswers.getFirst().y + Setting.checkBox);
        canvas.lineTo(question.blueprintAnswers.getLast().x - 15, question.blueprintAnswers.getLast().y - Setting.checkBox);
        canvas.stroke();
        canvas.setStrokingColor(Color.BLACK);
    }

    private void drawX(PDPageContentStream canvas, float cx, float cy, float r) throws IOException {
        canvas.setLineWidth(0.8f);
        canvas.moveTo(cx - r, cy - r);
        canvas.lineTo(cx + r, cy + r);
        canvas.moveTo(cx + r, cy - r);
        canvas.lineTo(cx - r, cy + r);
        canvas.stroke();
    }
}
