package ltldev.SeverUpFile.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ltldev.SeverUpFile.dto.UploadResult;
import ltldev.SeverUpFile.logging.AppLoggerUpgraded;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadServiceEnhanced {

    @Value("${app.upload-dir}")
    private String uploadDir;

    @Value("${app.max-parallel-upload:10}")
    private int maxParallelUpload;

    @Value("${app.allowed-extensions:jpg,jpeg,png,gif,mp4,mov,pdf,doc,docx,xls,xlsx}")
    private String allowedExtensions;

    @Value("${app.max-file-size:5368709120}") // 5GB in bytes
    private long maxFileSize;

    @Value("${app.chunk-size:8388608}") // 8MB chunks for efficient reading
    private int chunkSize;

    private static final String TEMP_SUFFIX = ".tmp";
    private static final String METADATA_SUFFIX = ".meta";
    private static final Set<String> FORBIDDEN_PATTERNS =
            Set.of("..", "~", "|", "<", ">", ":", "*", "?", "\"", "/", "\\");

    /**
     * Handle file upload with full validation and error handling
     */
    public UploadResult handleUpload(MultipartFile file, String clientIp, String device) {
        long startTimeMs = System.currentTimeMillis();
        String originalFilename = file.getOriginalFilename();
        long fileSize = file.getSize();

        try {
            // ========== PHASE 1: Validation ==========

            // 1.1 Null & empty check
            if (file.isEmpty()) {
                AppLoggerUpgraded.validationFailed(originalFilename, "File is empty");
                return UploadResult.error("File rỗng", "EMPTY_FILE", 400);
            }

            // 1.2 Filename validation
            UploadResult filenameValidation = validateFilename(originalFilename);
            if (!filenameValidation.isSuccess()) {
                return filenameValidation;
            }

            // 1.3 File size validation
            if (fileSize > maxFileSize) {
                AppLoggerUpgraded.validationFailed(originalFilename,
                        String.format("File size %d bytes exceeds max %d bytes", fileSize, maxFileSize));
                return UploadResult.error(
                        String.format("File quá lớn (max %dGB)", maxFileSize / (1024*1024*1024)),
                        "FILE_TOO_LARGE",
                        413);
            }

            // 1.4 Extension validation
            UploadResult extensionValidation = validateExtension(originalFilename);
            if (!extensionValidation.isSuccess()) {
                return extensionValidation;
            }

            // Log upload start
            AppLoggerUpgraded.uploadStarted(originalFilename, fileSize, clientIp);

            // ========== PHASE 2: Disk Checks ==========

            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();

            // 2.1 Ensure upload directory exists
            if (!Files.exists(uploadPath)) {
                try {
                    Files.createDirectories(uploadPath);
                    log.info("Created upload directory: {}", uploadPath);
                } catch (IOException e) {
                    log.error("Failed to create upload directory", e);
                    return UploadResult.error("Không thể tạo thư mục upload", "MKDIR_FAILED", 500);
                }
            }

            // 2.2 Check disk space
            try {
                FileStore fileStore = Files.getFileStore(uploadPath);
                long usableSpace = fileStore.getUsableSpace();

                if (usableSpace < fileSize) {
                    AppLoggerUpgraded.diskFull(originalFilename);
                    return UploadResult.error("Disk không đủ dung lượng", "DISK_FULL", 507);
                }

                // Warn if less than 10% free space
                long totalSpace = fileStore.getTotalSpace();
                double percentFree = (usableSpace * 100.0) / totalSpace;
                if (percentFree < 10) {
                    AppLoggerUpgraded.diskCheckWarning(originalFilename, fileSize, usableSpace);
                }
            } catch (IOException e) {
                log.error("Error checking disk space", e);
                return UploadResult.error("Lỗi kiểm tra disk", "DISK_CHECK_ERROR", 500);
            }

            // ========== PHASE 3: Upload ==========

            Path targetLocation = uploadPath.resolve(originalFilename);
            Path tempLocation = uploadPath.resolve(originalFilename + TEMP_SUFFIX);

            // 3.1 Check if file already exists
            if (Files.exists(targetLocation)) {
                // Option: allow overwrite or return conflict
                log.warn("File already exists: {}", originalFilename);
                return UploadResult.error("File đã tồn tại", "FILE_EXISTS", 409);
            }

            // 3.2 Perform actual upload (to temp file first)
            try {
                long uploadedBytes = uploadWithProgress(file.getInputStream(), tempLocation, fileSize);

                if (uploadedBytes != fileSize) {
                    Files.deleteIfExists(tempLocation);
                    return UploadResult.error("Upload không hoàn chỉnh", "INCOMPLETE_UPLOAD", 500);
                }

                // 3.3 Move temp to final location (atomic)
                Files.move(tempLocation, targetLocation, StandardCopyOption.REPLACE_EXISTING);

                // 3.4 Set file metadata
                setFileMetadata(targetLocation, clientIp, device, originalFilename);

            } catch (IOException e) {
                Files.deleteIfExists(tempLocation);
                log.error("Upload IO error", e);
                AppLoggerUpgraded.uploadFailed(originalFilename, "IO_ERROR", e);
                return UploadResult.error("Lỗi ghi file", "IO_ERROR", 500);
            }

            // ========== PHASE 4: Success ==========

            long durationMs = System.currentTimeMillis() - startTimeMs;
            double durationSec = durationMs / 1000.0;
            double speedMBps = (fileSize / (1024.0 * 1024.0)) / Math.max(durationSec, 0.001);

            AppLoggerUpgraded.uploadSuccess(originalFilename, fileSize, durationMs, speedMBps);

            return UploadResult.success(
                    "Upload thành công",
                    originalFilename,
                    fileSize,
                    durationMs,
                    speedMBps);

        } catch (Exception e) {
            log.error("Unexpected error during upload: {}", originalFilename, e);
            AppLoggerUpgraded.uploadFailed(originalFilename, "UNEXPECTED_ERROR", e);
            return UploadResult.error("Lỗi không xác định", "UNKNOWN_ERROR", 500);
        }
    }

    /**
     * Upload file with progress tracking (chunked reading)
     */
    private long uploadWithProgress(InputStream inputStream, Path targetPath, long totalSize) throws IOException {
        byte[] buffer = new byte[chunkSize];
        long uploadedBytes = 0;
        int bytesRead;

        try (InputStream in = inputStream;
             var out = Files.newOutputStream(targetPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                uploadedBytes += bytesRead;

                // Log progress for every 10% or every 100MB
                if (uploadedBytes % (10 * 1024 * 1024) == 0) {
                    double percentComplete = (uploadedBytes * 100.0) / totalSize;
                    log.debug("Upload progress: {:.1f}%", percentComplete);
                }
            }
            out.flush();
        }

        return uploadedBytes;
    }

    /**
     * Validate filename safety
     */
    private UploadResult validateFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return UploadResult.error("Tên file không hợp lệ", "INVALID_FILENAME", 400);
        }

        // Remove path traversal attempts
        filename = filename.replaceAll("[/\\\\]+", "");

        // Check for forbidden patterns
        for (String pattern : FORBIDDEN_PATTERNS) {
            if (filename.contains(pattern)) {
                AppLoggerUpgraded.validationFailed(filename, "Contains forbidden character: " + pattern);
                return UploadResult.error("Tên file chứa ký tự không hợp lệ", "INVALID_FILENAME", 400);
            }
        }

        // Max filename length
        if (filename.length() > 255) {
            return UploadResult.error("Tên file quá dài (max 255 ký tự)", "FILENAME_TOO_LONG", 400);
        }

        return UploadResult.success("Filename valid", filename, 0, 0, 0);
    }

    /**
     * Validate file extension
     */
    private UploadResult validateExtension(String filename) {
        String[] extensions = allowedExtensions.toLowerCase().split(",");
        String fileExtension = getFileExtension(filename).toLowerCase();

        boolean isAllowed = Arrays.stream(extensions)
                .anyMatch(ext -> ext.trim().equals(fileExtension));

        if (!isAllowed) {
            AppLoggerUpgraded.validationFailed(filename, "Extension not allowed: " + fileExtension);
            return UploadResult.error(
                    String.format("Loại file không được phép (%s)", fileExtension),
                    "EXTENSION_NOT_ALLOWED",
                    400);
        }

        return UploadResult.success("Extension valid", filename, 0, 0, 0);
    }

    /**
     * Extract file extension
     */
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1) : "";
    }

    /**
     * Store file metadata (upload time, client info, etc)
     */
    private void setFileMetadata(Path filePath, String clientIp, String device, String filename) {
        try {
            Map<String, String> metadata = new HashMap<>();
            metadata.put("uploaded_at", Instant.now().toString());
            metadata.put("client_ip", clientIp);
            metadata.put("device", device);
            metadata.put("original_name", filename);

            Path metadataPath = filePath.getParent().resolve(filePath.getFileName() + METADATA_SUFFIX);
            Files.writeString(metadataPath, metadata.toString());

            // Set file modification time
            FileTime fileTime = FileTime.from(Instant.now());
            Files.setLastModifiedTime(filePath, fileTime);
        } catch (IOException e) {
            log.warn("Failed to set metadata for file: {}", filename, e);
        }
    }
}