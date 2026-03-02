package ltldev.SeverUpFile.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.concurrent.atomic.AtomicInteger;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
public class AsyncUploadService {

    @Value("${app.upload-dir}")
    private String uploadDir;

    private final Map<String, UploadStatus> uploadStatusMap = new ConcurrentHashMap<>();
    private static final int BUFFER_SIZE = 1024 * 1024; // 1MB

    // ========== ASYNC UPLOAD ==========

    /**
     * ✅ FIX: Dùng @Async HOẶC CompletableFuture.runAsync(), không dùng cả 2
     * Option 1: Dùng @Async (đơn giản hơn)
     */
    @Async("uploadExecutor")
    public void uploadAsync(String uploadId, MultipartFile file) {
        UploadStatus status = new UploadStatus(
                uploadId,
                file.getOriginalFilename(),
                file.getSize()
        );
        uploadStatusMap.put(uploadId, status);

        try {
            status.setStartTime(System.currentTimeMillis());
            status.setState(UploadStatus.State.IN_PROGRESS);

            streamUploadWithProgress(uploadId, file, status);

            status.setState(UploadStatus.State.SUCCESS);
            status.setEndTime(System.currentTimeMillis());

            log.info("UPLOAD_SUCCESS uploadId={} filename={} duration={}ms speed={:.2f}MB/s",
                    uploadId,
                    file.getOriginalFilename(),
                    status.getDurationMs(),
                    status.getSpeedMBps());

        } catch (Exception e) {
            log.error("Upload failed: {}", uploadId, e);
            status.setState(UploadStatus.State.FAILED);
            status.setError(e.getMessage());
        }
    }

    /**
     * ✅ ALTERNATIVE: Nếu muốn return CompletableFuture (không dùng @Async)
     * Option 2: Dùng CompletableFuture.supplyAsync()
     */
    public CompletableFuture<UploadStatus> uploadAsyncWithFuture(
            String uploadId,
            MultipartFile file) {

        return CompletableFuture.supplyAsync(() -> {
            UploadStatus status = new UploadStatus(
                    uploadId,
                    file.getOriginalFilename(),
                    file.getSize()
            );
            uploadStatusMap.put(uploadId, status);

            try {
                status.setStartTime(System.currentTimeMillis());
                status.setState(UploadStatus.State.IN_PROGRESS);

                streamUploadWithProgress(uploadId, file, status);

                status.setState(UploadStatus.State.SUCCESS);
                status.setEndTime(System.currentTimeMillis());

                log.info("UPLOAD_SUCCESS uploadId={} filename={}",
                        uploadId, file.getOriginalFilename());

                return status;

            } catch (Exception e) {
                log.error("Upload failed: {}", uploadId, e);
                status.setState(UploadStatus.State.FAILED);
                status.setError(e.getMessage());
                return status;
            }
        }, getUploadExecutor());
    }

    /**
     * Stream với progress tracking
     */
    private void streamUploadWithProgress(
            String uploadId,
            MultipartFile file,
            UploadStatus status) throws IOException {

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path filePath = uploadPath.resolve(sanitizeFilename(file.getOriginalFilename()));

        Files.createDirectories(uploadPath);

        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream in = file.getInputStream();
             OutputStream out = Files.newOutputStream(filePath,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.WRITE)) {

            int bytesRead;
            long uploadedBytes = 0;
            long totalBytes = file.getSize();

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                uploadedBytes += bytesRead;

                // Update progress
                status.setUploadedBytes(uploadedBytes);
                status.setProgress((uploadedBytes * 100.0) / totalBytes);

                // Log progress mỗi 100MB
                if (uploadedBytes % (100 * 1024 * 1024) == 0) {
                    log.debug("UPLOAD_PROGRESS uploadId={} progress={:.1f}%",
                            uploadId, status.getProgress());
                }
            }
            out.flush();
        }
    }

    /**
     * Lấy upload status
     */
    public UploadStatus getStatus(String uploadId) {
        return uploadStatusMap.getOrDefault(uploadId,
                new UploadStatus(uploadId, "unknown", 0));
    }

    /**
     * Sanitize filename
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) return "file_" + System.currentTimeMillis();
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * ✅ Executor bean configuration
     */
    public Executor getUploadExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                10,              // core threads
                20,              // max threads
                60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                createThreadFactory("async-upload")
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /**
     * ThreadFactory helper
     */
    private ThreadFactory createThreadFactory(String name) {
        return new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, name + "-" + count.incrementAndGet());
                t.setDaemon(false);
                return t;
            }
        };
    }

    /**
     * Cleanup old uploads (optional)
     */
    public void cleanupOldUploads(long maxAgeMs) {
        long now = System.currentTimeMillis();
        uploadStatusMap.entrySet().removeIf(entry ->
                entry.getValue().getEndTime() > 0 &&
                        (now - entry.getValue().getEndTime()) > maxAgeMs
        );
    }

    // ========== DTO CLASSES ==========

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UploadStatus {

        public enum State {
            PENDING, IN_PROGRESS, SUCCESS, FAILED, CANCELLED
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

        public UploadStatus(String uploadId, String filename, long totalBytes) {
            this.uploadId = uploadId;
            this.filename = filename;
            this.totalBytes = totalBytes;
            this.state = State.PENDING;
        }

        public long getDurationMs() {
            if (endTime == 0) return 0;
            return endTime - startTime;
        }

        public double getSpeedMBps() {
            long durationMs = getDurationMs();
            if (durationMs == 0) return 0;
            return (uploadedBytes / (1024.0 * 1024.0)) / (durationMs / 1000.0);
        }

        public boolean isComplete() {
            return state == State.SUCCESS || state == State.FAILED || state == State.CANCELLED;
        }

        public String getFormattedProgress() {
            return String.format("%.1f%% (%s / %s)",
                    progress,
                    formatBytes(uploadedBytes),
                    formatBytes(totalBytes));
        }

        private String formatBytes(long bytes) {
            if (bytes <= 0) return "0 B";
            final String[] units = new String[]{"B", "KB", "MB", "GB"};
            int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
            return String.format("%.2f %s",
                    bytes / Math.pow(1024, digitGroups),
                    units[Math.min(digitGroups, units.length - 1)]);
        }
    }
}