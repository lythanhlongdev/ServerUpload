package ltldev.SeverUpFile.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

/**
 * Optimized upload service - bypass temp directory
 * Direct streaming to final location
 */
@Slf4j
@Service
public class OptimizedUploadService {

    @Value("${app.upload-dir}")
    private String uploadDir;

    // ========== BUFFER SIZE ==========
    private static final int BUFFER_SIZE = 1024 * 1024; // 1MB

    /**
     * ✅ FIX: Stream directly to file (no intermediate temp)
     * Sử dụng byte array buffer thay vì CopyOptions (không tồn tại)
     */
    public long streamUpload(InputStream inputStream, String filename, long fileSize) throws IOException {
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path filePath = uploadPath.resolve(sanitizeFilename(filename));

        // Create parent directories if not exist
        Files.createDirectories(uploadPath);

        // ✅ Stream directly với custom buffer size
        long startTime = System.currentTimeMillis();
        long writtenBytes = 0;

        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream in = inputStream;
             var out = Files.newOutputStream(filePath,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.WRITE,
                     StandardOpenOption.TRUNCATE_EXISTING)) {

            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                writtenBytes += bytesRead;

                // Log progress mỗi 100MB
                if (writtenBytes % (100 * 1024 * 1024) == 0) {
                    double progressPercent = (writtenBytes * 100.0) / fileSize;
                    log.debug("UPLOAD_PROGRESS filename={} progress={:.1f}%",
                            filename, progressPercent);
                }
            }
            out.flush();
        }

        long durationMs = System.currentTimeMillis() - startTime;
        double speedMBps = calculateSpeed(writtenBytes, durationMs);

        log.info("UPLOAD_COMPLETE filename={} size={} duration={}ms speed={:.2f}MB/s",
                filename,
                formatBytes(writtenBytes),
                durationMs,
                speedMBps);

        // ✅ Set file metadata
        setFileMetadata(filePath, filename);

        return writtenBytes;
    }

    /**
     * ✅ Alternative: Stream với progress callback
     * Dùng nếu cần update progress real-time cho client
     */
    public long streamUploadWithCallback(
            InputStream inputStream,
            String filename,
            long fileSize,
            ProgressCallback progressCallback) throws IOException {

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path filePath = uploadPath.resolve(sanitizeFilename(filename));

        Files.createDirectories(uploadPath);

        long startTime = System.currentTimeMillis();
        long uploadedBytes = 0;

        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream in = inputStream;
             var out = Files.newOutputStream(filePath,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.WRITE,
                     StandardOpenOption.TRUNCATE_EXISTING)) {

            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                uploadedBytes += bytesRead;

                // Call progress callback
                if (progressCallback != null) {
                    double progressPercent = (uploadedBytes * 100.0) / fileSize;
                    long elapsedMs = System.currentTimeMillis() - startTime;
                    double speedMBps = calculateSpeed(uploadedBytes, elapsedMs);

                    progressCallback.onProgress(
                            uploadedBytes,
                            fileSize,
                            progressPercent,
                            speedMBps
                    );
                }
            }
            out.flush();
        }

        long durationMs = System.currentTimeMillis() - startTime;
        double speedMBps = calculateSpeed(uploadedBytes, durationMs);

        log.info("UPLOAD_COMPLETE filename={} size={} duration={}ms speed={:.2f}MB/s",
                filename,
                formatBytes(uploadedBytes),
                durationMs,
                speedMBps);

        setFileMetadata(filePath, filename);

        return uploadedBytes;
    }

    /**
     * ✅ Stream to temp file first, then move (atomic)
     * Safer approach - nếu fail giữa chừng, file chưa hoàn chỉnh
     */
    public long streamUploadAtomic(
            InputStream inputStream,
            String filename,
            long fileSize) throws IOException {

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path filePath = uploadPath.resolve(sanitizeFilename(filename));
        Path tempPath = uploadPath.resolve(filePath.getFileName() + ".tmp");

        Files.createDirectories(uploadPath);

        long startTime = System.currentTimeMillis();
        long uploadedBytes = 0;

        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream in = inputStream;
             var out = Files.newOutputStream(tempPath,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.WRITE,
                     StandardOpenOption.TRUNCATE_EXISTING)) {

            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                uploadedBytes += bytesRead;
            }
            out.flush();
        }

        // ✅ Atomic move (replace if exists)
        Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING);

        long durationMs = System.currentTimeMillis() - startTime;
        double speedMBps = calculateSpeed(uploadedBytes, durationMs);

        log.info("UPLOAD_ATOMIC_COMPLETE filename={} size={} duration={}ms",
                filename,
                formatBytes(uploadedBytes),
                durationMs);

        setFileMetadata(filePath, filename);

        return uploadedBytes;
    }

    // ========== HELPER METHODS ==========

    /**
     * Set file metadata (time, permissions)
     */
    private void setFileMetadata(Path filePath, String filename) {
        try {
            FileTime fileTime = FileTime.from(Instant.now());
            Files.setLastModifiedTime(filePath, fileTime);
            log.debug("File metadata set: {}", filename);
        } catch (IOException e) {
            log.warn("Failed to set metadata: {}", filename, e);
        }
    }

    /**
     * Sanitize filename
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return "file_" + System.currentTimeMillis();
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Calculate upload speed
     */
    private double calculateSpeed(long bytes, long durationMs) {
        if (durationMs == 0) return 0;
        return (bytes / (1024.0 * 1024.0)) / (durationMs / 1000.0);
    }

    /**
     * Format bytes to readable string
     */
    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.2f %s",
                bytes / Math.pow(1024, digitGroups),
                units[Math.min(digitGroups, units.length - 1)]);
    }

    // ========== CALLBACK INTERFACE ==========

    /**
     * Progress callback cho upload real-time
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(long uploadedBytes, long totalBytes,
                        double progressPercent, double speedMBps);
    }
}