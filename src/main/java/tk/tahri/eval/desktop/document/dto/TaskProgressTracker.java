package tk.tahri.eval.desktop.document.dto;

import java.util.function.Consumer;

public class TaskProgressTracker {

    private final Consumer<TaskProgress> consumer;
    private final long startTime;
    private String stageTitle;
    private int currentStep = 0;
    private int totalSteps = 1;
    private double baseRatio = 0.0;
    private double stageWeight = 1.0;
    private int successCount = 0;
    private int warningCount = 0;
    private String currentDetail = "";

    public TaskProgressTracker(Consumer<TaskProgress> consumer) {
        this.consumer = consumer;
        this.startTime = System.currentTimeMillis();
    }

    public synchronized void setStage(String stageTitle, int totalSteps, double baseRatio, double stageWeight) {
        this.stageTitle = stageTitle;
        this.totalSteps = Math.max(1, totalSteps);
        this.baseRatio = baseRatio;
        this.stageWeight = stageWeight;
        this.currentStep = 0;
        emitProgress();
    }

    public synchronized void updateStep(int step, String detail) {
        this.currentStep = step;
        this.currentDetail = detail != null ? detail : "";
        emitProgress();
    }

    public synchronized void recordSuccess() {
        this.successCount++;
        emitProgress();
    }

    public synchronized void recordWarning() {
        this.warningCount++;
        emitProgress();
    }

    public synchronized void updateStepWithResult(int step, String detail, boolean success) {
        this.currentStep = step;
        this.currentDetail = detail != null ? detail : "";
        if (success) {
            this.successCount++;
        } else {
            this.warningCount++;
        }
        emitProgress();
    }

    public synchronized void setComplete(String finalDetail) {
        this.currentDetail = finalDetail != null ? finalDetail : "Terminé";
        this.currentStep = this.totalSteps;
        long elapsed = System.currentTimeMillis() - startTime;
        if (consumer != null) {
            consumer.accept(new TaskProgress(
                    stageTitle != null ? stageTitle : "Terminé",
                    totalSteps,
                    totalSteps,
                    1.0,
                    this.currentDetail,
                    elapsed,
                    0L,
                    successCount,
                    warningCount
            ));
        }
    }

    private void emitProgress() {
        if (consumer == null) return;

        double stepFraction = Math.min(1.0, (double) currentStep / totalSteps);
        double overallRatio = Math.min(1.0, baseRatio + (stepFraction * stageWeight));
        long elapsed = Math.max(1, System.currentTimeMillis() - startTime);

        Long eta = null;
        if (overallRatio > 0.05 && overallRatio < 1.0) {
            long totalEstimated = (long) (elapsed / overallRatio);
            eta = Math.max(0, totalEstimated - elapsed);
        }

        consumer.accept(new TaskProgress(
                stageTitle != null ? stageTitle : "En cours...",
                currentStep,
                totalSteps,
                overallRatio,
                currentDetail,
                elapsed,
                eta,
                successCount,
                warningCount
        ));
    }
}
