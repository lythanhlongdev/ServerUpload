package ltldev.SeverUpFile.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ltldev.SeverUpFile.service.AsyncUploadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class AsyncUploadController {

    private final AsyncUploadService uploadService;

    /**
     * ✅ Option 1: Dùng @Async (chỉ nhận void hoặc CompletableFuture)
     */
    @PostMapping("/async")
    public ResponseEntity<?> uploadAsync(
            @RequestParam("file") MultipartFile file) {

        String uploadId = UUID.randomUUID().toString();

        // ✅ Gọi async method - trả response ngay
        uploadService.uploadAsync(uploadId, file);

        return ResponseEntity.accepted().body(Map.of(
                "uploadId", uploadId,
                "statusUrl", "/upload/status/" + uploadId,
                "message", "Upload started. Check status via statusUrl"
        ));
    }

    /**
     * ✅ Option 2: Dùng CompletableFuture (nếu cần return value)
     */
    @PostMapping("/async/with-future")
    public ResponseEntity<?> uploadAsyncWithFuture(
            @RequestParam("file") MultipartFile file) {

        String uploadId = UUID.randomUUID().toString();

        // ✅ Trả CompletableFuture - có thể chain
        uploadService.uploadAsyncWithFuture(uploadId, file)
                .thenAccept(status -> {
                    log.info("Upload completed: {} - {}", uploadId, status.getState());
                })
                .exceptionally(ex -> {
                    log.error("Upload error: {}", uploadId, ex);
                    return null;
                });

        return ResponseEntity.accepted().body(Map.of(
                "uploadId", uploadId,
                "statusUrl", "/upload/status/" + uploadId
        ));
    }

    /**
     * Check status
     */
    @GetMapping("/status/{uploadId}")
    public ResponseEntity<?> getUploadStatus(
            @PathVariable String uploadId) {

        AsyncUploadService.UploadStatus status = uploadService.getStatus(uploadId);
        return ResponseEntity.ok(Map.of(
                "uploadId", uploadId,
                "state", status.getState(),
                "progress", status.getFormattedProgress(),
                "speed", String.format("%.2f MB/s", status.getSpeedMBps()),
                "duration", String.format("%.1fs", status.getDurationMs() / 1000.0)
        ));
    }
}