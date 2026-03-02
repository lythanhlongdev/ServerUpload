package ltldev.SeverUpFile.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Track upload metrics for monitoring dashboard
 */
@Slf4j
@Service
public class UploadMetricsService {

    private final AtomicLong totalUploads = new AtomicLong(0);
    private final AtomicLong successfulUploads = new AtomicLong(0);
    private final AtomicLong failedUploads = new AtomicLong(0);
    private final AtomicLong totalBytesUploaded = new AtomicLong(0);
    private final AtomicLong totalUploadTimeMs = new AtomicLong(0);
    private final ConcurrentHashMap<String, UploadStats> errorStats = new ConcurrentHashMap<>();

    public synchronized void recordSuccess(long fileSizeBytes, long durationMs) {
        successfulUploads.incrementAndGet();
        totalUploads.incrementAndGet();
        totalBytesUploaded.addAndGet(fileSizeBytes);
        totalUploadTimeMs.addAndGet(durationMs);
    }

    public synchronized void recordFailure(String errorCode) {
        failedUploads.incrementAndGet();
        totalUploads.incrementAndGet();
        errorStats.computeIfAbsent(errorCode, k -> new UploadStats())
                .increment();
    }

    public UploadMetrics getMetrics() {
        long totalUploadsVal = totalUploads.get();
        long avgUploadTimeMs = totalUploadsVal > 0 ?
                totalUploadTimeMs.get() / totalUploadsVal : 0;
        double avgUploadSpeedMBps = totalUploadsVal > 0 ?
                (totalBytesUploaded.get() / (1024.0 * 1024.0)) / (totalUploadTimeMs.get() / 1000.0) : 0;

        return UploadMetrics.builder()
                .totalUploads(totalUploadsVal)
                .successfulUploads(successfulUploads.get())
                .failedUploads(failedUploads.get())
                .totalBytesUploaded(totalBytesUploaded.get())
                .avgUploadTimeMs(avgUploadTimeMs)
                .avgUploadSpeedMBps(avgUploadSpeedMBps)
                .successRate(totalUploadsVal > 0 ?
                        (successfulUploads.get() * 100.0 / totalUploadsVal) : 0)
                .errorBreakdown(errorStats)
                .build();
    }

    @lombok.Data
    public static class UploadStats {
        private long count = 0;
        public void increment() { this.count++; }
    }
}