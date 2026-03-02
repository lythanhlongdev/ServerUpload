package ltldev.SeverUpFile.service;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Trạng thái của một batch upload
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchUploadState {

    public enum State {
        QUEUED, IN_PROGRESS, SUCCESS, FAILED, CANCELLED
    }

    // ========== CONSTRUCTOR ==========
    public BatchUploadState(String batchId, int totalFiles,
                            long totalSize, String clientIp, String sessionId) {
        this.batchId = batchId;
        this.totalFiles = totalFiles;
        this.totalSize = totalSize;
        this.clientIp = clientIp;
        this.sessionId = sessionId;
        this.state = State.QUEUED;
        this.successCount = 0;
        this.failureCount = 0;
        this.failedFiles = new CopyOnWriteArrayList<>();
        this.errors = new ConcurrentHashMap<>();
    }

    // ========== FIELDS ==========
    private String batchId;
    private int totalFiles;
    private long totalSize;
    private String clientIp;
    private String sessionId;
    private State state = State.QUEUED;

    private long startTime;
    private long endTime;

    private int successCount = 0;
    private int failureCount = 0;
    private List<String> failedFiles = new CopyOnWriteArrayList<>();
    private Map<String, String> errors = new ConcurrentHashMap<>();
    private String error;

    // ========== METHODS ==========

    public void recordSuccess(long fileSize) {
        successCount++;
    }

    public void recordFailure(String filename, String errorMsg) {
        failureCount++;
        failedFiles.add(filename);
        errors.put(filename, errorMsg);
    }

    public long getDurationMs() {
        return endTime - startTime;
    }

    public double getAverageSpeedMBps() {
        long duration = getDurationMs();
        if (duration == 0) return 0;
        return (totalSize / (1024.0 * 1024.0)) / (duration / 1000.0);
    }

    public String getFormattedTotalSize() {
        return String.format("%.2f GB", totalSize / (1024.0 * 1024.0 * 1024.0));
    }

    public String getStatusSummary() {
        return String.format("%d/%d uploaded (%d failed)",
                successCount, totalFiles, failureCount);
    }
}