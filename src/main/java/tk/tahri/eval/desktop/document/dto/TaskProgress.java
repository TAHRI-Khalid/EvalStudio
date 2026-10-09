package tk.tahri.eval.desktop.document.dto;

public record TaskProgress(
        String stageTitle,
        int currentStep,
        int totalSteps,
        double progressRatio,
        String currentDetail,
        long elapsedMillis,
        Long estimatedRemainingMillis,
        int successCount,
        int warningCount
) {
    public int percentage() {
        return (int) Math.min(100, Math.max(0, Math.round(progressRatio * 100.0)));
    }

    public String formattedElapsed() {
        long seconds = (elapsedMillis / 1000) % 60;
        long minutes = (elapsedMillis / (1000 * 60));
        return String.format("%02d:%02d", minutes, seconds);
    }

    public String formattedEta() {
        if (estimatedRemainingMillis == null || estimatedRemainingMillis <= 0) {
            return "--:--";
        }
        long seconds = (estimatedRemainingMillis / 1000) % 60;
        long minutes = (estimatedRemainingMillis / (1000 * 60));
        return String.format("%02d:%02d", minutes, seconds);
    }
}
