package ltldev.SeverUpFile.service;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

/**
 * DTO chứa metrics upload
 */
@Data
@Builder
public class UploadMetrics {
    private long totalUploads;
    private long successfulUploads;
    private long failedUploads;
    private long totalBytesUploaded;
    private long avgUploadTimeMs;
    private double avgUploadSpeedMBps;
    private double successRate;
    private Map<String, UploadMetricsService.UploadStats> errorBreakdown;

    public String getTotalUploadedGB() {
        return String.format("%.2f GB", totalBytesUploaded / (1024.0 * 1024.0 * 1024.0));
    }

    public String getFormattedSuccessRate() {
        return String.format("%.1f%%", successRate);
    }
}