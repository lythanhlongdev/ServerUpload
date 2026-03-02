package ltldev.SeverUpFile.logging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Structured logging wrapper using SLF4J
 * Replaces custom AppLogger with proper framework integration
 */
@Slf4j
public class AppLoggerUpgraded {

    // Upload lifecycle events
    public static void uploadStarted(String filename, long fileSizeBytes, String clientIp) {
        log.info("UPLOAD_STARTED file={} size_bytes={} size_mb={:.2f} client_ip={}",
                filename,
                fileSizeBytes,
                fileSizeBytes / (1024.0 * 1024.0),
                clientIp);
    }

    public static void uploadInProgress(String filename, long uploadedBytes, long totalBytes) {
        double percent = (uploadedBytes * 100.0) / totalBytes;
        log.debug("UPLOAD_PROGRESS file={} uploaded_bytes={} total_bytes={} percent={:.1f}%",
                filename, uploadedBytes, totalBytes, percent);
    }

    public static void uploadSuccess(String filename, long fileSizeBytes, long durationMs, double speedMBps) {
        log.info("UPLOAD_SUCCESS file={} size_mb={:.2f} duration_ms={} speed_mbps={:.2f}",
                filename,
                fileSizeBytes / (1024.0 * 1024.0),
                durationMs,
                speedMBps);
    }

    public static void uploadFailed(String filename, String reason, Exception e) {
        log.error("UPLOAD_FAILED file={} reason={} error={}", filename, reason, e.getMessage(), e);
    }

    // Validation events
    public static void validationFailed(String filename, String reason) {
        log.warn("VALIDATION_FAILED file={} reason={}", filename, reason);
    }

    // Disk events
    public static void diskCheckWarning(String filename, long requiredBytes, long availableBytes) {
        log.warn("DISK_CHECK_WARNING file={} required_mb={:.2f} available_mb={:.2f}",
                filename,
                requiredBytes / (1024.0 * 1024.0),
                availableBytes / (1024.0 * 1024.0));
    }

    public static void diskFull(String filename) {
        log.error("DISK_FULL file={}", filename);
    }

    // System events
    public static void systemWarning(String message) {
        log.warn("SYSTEM_WARNING message={}", message);
    }
}