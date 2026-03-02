package ltldev.SeverUpFile.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ltldev.SeverUpFile.memory.SmartMemoryManager;
import ltldev.SeverUpFile.queue.UploadQueueManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchUploadService {

    private final UploadQueueManager queueManager;
    private final SmartMemoryManager memoryManager;

    @Value("${app.upload-dir}")
    private String uploadDir;

    @Value("${upload.batch.max-files:100}")
    private int maxFilesPerBatch;

    @Value("${upload.batch.max-total-size:10737418240}")
    private long maxBatchSize;

    private static final int BUFFER_SIZE = 1024 * 1024;

    // ========== BATCH UPLOAD ==========

    public BatchUploadResponse submitBatchUpload(
            MultipartFile[] files,
            String clientIp,
            String sessionId) {

        BatchValidation validation = validateBatch(files);
        if (!validation.isValid()) {
            return BatchUploadResponse.error(validation.getError());
        }

        String batchId = UUID.randomUUID().toString();
        BatchUploadState batchState = new BatchUploadState(
                batchId,
                files.length,
                validation.totalSize,
                clientIp,
                sessionId
        );

        log.info("BATCH_UPLOAD_SUBMITTED batchId={} files={} totalSize={} MB",
                batchId, files.length, validation.getTotalSize() / (1024.0 * 1024.0));

        // Copy files to temp storage immediately
        List<FileData> fileDataList = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                FileData fileData = copyFileToTemp(file);
                fileDataList.add(fileData);
            }
        } catch (IOException e) {
            log.error("Failed to copy files to temp", e);
            return BatchUploadResponse.error("Lỗi sao chép file tạm: " + e.getMessage());
        }

        submitBatchAsync(batchState, fileDataList);

        return BatchUploadResponse.success(batchId, files.length);
    }

    /**
     * Copy MultipartFile to temp storage immediately
     */
    private FileData copyFileToTemp(MultipartFile file) throws IOException {
        String tempDir = System.getProperty("java.io.tmpdir");
        Path tempPath = Paths.get(tempDir, "serverupload-batch-" + UUID.randomUUID());

        Files.createDirectories(tempPath);

        Path filePath = tempPath.resolve(sanitizeFilename(file.getOriginalFilename()));

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, filePath, StandardCopyOption.REPLACE_EXISTING);
        }

        return new FileData(
                file.getOriginalFilename(),
                file.getSize(),
                filePath
        );
    }

    /**
     * ✅ FIX: Process batch asynchronously
     * KHÔNG cleanup temp files ở đây - để cho streamUploadFile cleanup
     */
    private void submitBatchAsync(BatchUploadState batch, List<FileData> fileDataList) {
        CompletableFuture.runAsync(() -> {
            batch.setState(BatchUploadState.State.IN_PROGRESS);
            batch.setStartTime(System.currentTimeMillis());

            try {
                for (int i = 0; i < fileDataList.size(); i++) {
                    FileData fileData = fileDataList.get(i);
                    String fileId = batch.getBatchId() + "-" + i;

                    try {
                        processSingleFile(batch, fileData, fileId, i + 1, fileDataList.size());
                        batch.recordSuccess(fileData.size);

                    } catch (Exception e) {
                        log.error("File processing failed in batch: {} - {}",
                                batch.getBatchId(),
                                fileData.filename,
                                e);
                        batch.recordFailure(fileData.filename, e.getMessage());
                    }

                    if (i < fileDataList.size() - 1) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }

                batch.setState(BatchUploadState.State.SUCCESS);
                batch.setEndTime(System.currentTimeMillis());

                log.info("BATCH_COMPLETED batchId={} successful={} failed={} totalTime={}ms",
                        batch.getBatchId(),
                        batch.getSuccessCount(),
                        batch.getFailureCount(),
                        batch.getDurationMs());

            } catch (Exception e) {
                batch.setState(BatchUploadState.State.FAILED);
                batch.setError(e.getMessage());
                log.error("Batch upload failed: {}", batch.getBatchId(), e);
            }
            // ✅ FIX: KHÔNG cleanup temp files ở đây!
        });
    }

    /**
     * ✅ FIX: Process single file - cleanup temp file AFTER streaming
     */
    private void processSingleFile(
            BatchUploadState batch,
            FileData fileData,
            String fileId,
            int fileIndex,
            int totalFiles) throws Exception {

        // Check memory
        if (!memoryManager.canAcceptUpload(Math.min(fileData.size, 100 * 1024 * 1024))) {
            throw new RuntimeException("Insufficient memory for upload");
        }

        // Submit to queue
        queueManager.submitUpload(
                fileId,
                fileData.filename,
                fileData.size,
                () -> {
                    try {
                        memoryManager.registerStream(fileData.size);
                        streamUploadFile(batch, fileData);
                        log.info("FILE_UPLOADED fileId={} filename={}", fileId, fileData.filename);

                    } catch (IOException e) {
                        log.error("FILE_UPLOAD_FAILED fileId={} filename={} error={}",
                                fileId, fileData.filename, e.getMessage(), e);
                        batch.recordFailure(fileData.filename, "IO Error: " + e.getMessage());

                    } catch (Exception e) {
                        log.error("FILE_UPLOAD_ERROR fileId={} filename={} error={}",
                                fileId, fileData.filename, e.getMessage(), e);
                        batch.recordFailure(fileData.filename, "Error: " + e.getMessage());

                    } finally {
                        memoryManager.unregisterStream(fileData.size);

                        // ✅ FIX: Cleanup temp file AFTER streaming
                        cleanupTempFile(fileData);
                    }
                }
        );

        log.debug("File queued: batch={} file={}/{} size={}",
                batch.getBatchId(), fileIndex, totalFiles, fileData.size / (1024.0 * 1024.0));
    }

    /**
     * Stream từ temp file
     */
    private void streamUploadFile(
            BatchUploadState batch,
            FileData fileData) throws IOException {

        // ✅ Check temp file exists trước khi read
        if (!Files.exists(fileData.tempPath)) {
            throw new FileNotFoundException("Temp file not found: " + fileData.tempPath);
        }

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path filePath = uploadPath.resolve(fileData.filename);

        Files.createDirectories(uploadPath);

        byte[] buffer = new byte[BUFFER_SIZE];
        long uploadedBytes = 0;
        long startTime = System.currentTimeMillis();

        try (InputStream in = Files.newInputStream(fileData.tempPath);
             OutputStream out = Files.newOutputStream(filePath)) {

            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                uploadedBytes += bytesRead;

                // Log progress mỗi 100MB
                if (uploadedBytes % (100 * 1024 * 1024) == 0) {
                    double progressPercent = (uploadedBytes * 100.0) / fileData.size;
                    log.debug("UPLOAD_PROGRESS filename={} progress={:.1f}%",
                            fileData.filename, progressPercent);
                }
            }
            out.flush();
        }

        long durationMs = System.currentTimeMillis() - startTime;
        double speedMBps = calculateSpeed(uploadedBytes, durationMs);

        log.info("FILE_STREAM_COMPLETE filename={} size={} duration={}ms speed={:.2f}MB/s",
                fileData.filename,
                formatBytes(uploadedBytes),
                durationMs,
                speedMBps);
    }

    /**
     * ✅ FIX: Cleanup temp file after streaming
     */
    private void cleanupTempFile(FileData fileData) {
        try {
            if (Files.exists(fileData.tempPath)) {
                Files.delete(fileData.tempPath);
                log.debug("Cleaned up temp file: {}", fileData.tempPath);
            }

            // Cleanup parent directory if empty
            Path parentDir = fileData.tempPath.getParent();
            if (parentDir != null && Files.exists(parentDir)) {
                try {
                    Files.delete(parentDir);
                    log.debug("Cleaned up temp directory: {}", parentDir);
                } catch (DirectoryNotEmptyException ignored) {
                    // Other files still in directory
                }
            }
        } catch (IOException e) {
            log.warn("Failed to cleanup temp file: {}", fileData.tempPath, e);
        }
    }

    // ========== HELPERS ==========

    private BatchValidation validateBatch(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return BatchValidation.error("Không có file nào");
        }

        if (files.length > maxFilesPerBatch) {
            return BatchValidation.error(
                    String.format("Quá nhiều file (%d > %d)", files.length, maxFilesPerBatch));
        }

        long totalSize = 0;
        for (MultipartFile file : files) {
            totalSize += file.getSize();
        }

        if (totalSize > maxBatchSize) {
            return BatchValidation.error(
                    String.format("Batch quá lớn (%.1f GB > %.1f GB)",
                            totalSize / (1024.0 * 1024.0 * 1024.0),
                            maxBatchSize / (1024.0 * 1024.0 * 1024.0)));
        }

        return BatchValidation.success(totalSize);
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return "unnamed_" + System.currentTimeMillis();
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private double calculateSpeed(long bytes, long durationMs) {
        if (durationMs == 0) return 0;
        return (bytes / (1024.0 * 1024.0)) / (durationMs / 1000.0);
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.2f %s",
                bytes / Math.pow(1024, digitGroups),
                units[Math.min(digitGroups, units.length - 1)]);
    }

    // ========== INNER CLASSES ==========

    @Data
    public static class FileData {
        String filename;
        long size;
        Path tempPath;

        public FileData(String filename, long size, Path tempPath) {
            this.filename = filename;
            this.size = size;
            this.tempPath = tempPath;
        }
    }

    @Data
    public static class BatchValidation {
        private boolean valid;
        private String error;
        private long totalSize;

        public static BatchValidation error(String error) {
            BatchValidation validation = new BatchValidation();
            validation.valid = false;
            validation.error = error;
            return validation;
        }

        public static BatchValidation success(long totalSize) {
            BatchValidation validation = new BatchValidation();
            validation.valid = true;
            validation.totalSize = totalSize;
            return validation;
        }

        public long getTotalSize() {
            return totalSize;
        }
    }

    @Data
    public static class BatchUploadResponse {
        private boolean success;
        private String batchId;
        private int fileCount;
        private String error;
        private long submittedAt;

        public static BatchUploadResponse success(String batchId, int fileCount) {
            BatchUploadResponse response = new BatchUploadResponse();
            response.success = true;
            response.batchId = batchId;
            response.fileCount = fileCount;
            response.submittedAt = System.currentTimeMillis();
            return response;
        }

        public static BatchUploadResponse error(String error) {
            BatchUploadResponse response = new BatchUploadResponse();
            response.success = false;
            response.error = error;
            return response;
        }
    }
}