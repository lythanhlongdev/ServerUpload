package ltldev.SeverUpFile.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadStatus {

    public enum State {
        PENDING, IN_PROGRESS, SUCCESS, FAILED
    }

    private String uploadId;
    private String filename;
    private long totalBytes;
    private long uploadedBytes = 0;
    private double progress = 0.0;
    private State state = State.PENDING;
    private long startTime;
    private long endTime;
    private String error;

    public long getDurationMs() {
        return endTime - startTime;
    }

    public double getSpeedMBps() {
        long durationMs = getDurationMs();
        if (durationMs == 0) return 0;
        return (uploadedBytes / (1024.0 * 1024.0)) / (durationMs / 1000.0);
    }
}