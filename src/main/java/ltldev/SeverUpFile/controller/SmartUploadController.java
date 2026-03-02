package ltldev.SeverUpFile.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ltldev.SeverUpFile.queue.UploadQueueManager;
import ltldev.SeverUpFile.service.BatchUploadService;
import ltldev.SeverUpFile.service.BatchUploadService.BatchUploadResponse;
import ltldev.SeverUpFile.memory.SmartMemoryManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class SmartUploadController {

    private final BatchUploadService batchUploadService;
    private final UploadQueueManager queueManager;
    private final SmartMemoryManager memoryManager;

    /**
     * Handle batch upload (Ctrl+A - multiple files)
     * Returns immediately with batch ID
     */
    @PostMapping("/batch")
    public ResponseEntity<BatchUploadResponse> uploadBatch(
            @RequestParam("files") MultipartFile[] files,
            HttpServletRequest request,
            HttpSession session) {

        String clientIp = getClientIp(request);
        String sessionId = session.getId();

        log.info("BATCH_UPLOAD_REQUEST files={} totalSize={} ip={}",
            files.length,
            getSize(files),
            clientIp);

        // Async batch upload (returns immediately)
        BatchUploadResponse response = batchUploadService.submitBatchUpload(
            files, clientIp, sessionId);

        if (response.isSuccess()) {
            return ResponseEntity.accepted().body(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get batch upload status
     */
    @GetMapping("/batch/{batchId}")
    public ResponseEntity<?> getBatchStatus(@PathVariable String batchId) {
        // TODO: retrieve from persistent storage
        return ResponseEntity.ok("status endpoint");
    }

    /**
     * Get queue statistics
     */
    @GetMapping("/queue/stats")
    public ResponseEntity<?> getQueueStats() {
        return ResponseEntity.ok(Map.of(
            "queue", queueManager.getQueueStats(),
            "memory", memoryManager.getMemoryStats()
        ));
    }

    /**
     * Get upload progress (real-time)
     */
    @GetMapping("/progress/{uploadId}")
    public ResponseEntity<?> getProgress(@PathVariable String uploadId) {
        var task = queueManager.getUploadStatus(uploadId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(task);
    }

    // ========== HELPERS ==========

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return (xff != null) ? xff.split(",")[0].trim() : request.getRemoteAddr();
    }

    private long getSize(MultipartFile[] files) {
        long total = 0;
        for (MultipartFile f : files) {
            total += f.getSize();
        }
        return total;
    }
}