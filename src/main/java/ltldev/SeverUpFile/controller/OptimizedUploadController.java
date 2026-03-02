package ltldev.SeverUpFile.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ltldev.SeverUpFile.service.OptimizedUploadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class OptimizedUploadController {

    private final OptimizedUploadService uploadService;

    /**
     * ✅ Option 1: Direct streaming (đơn giản, nhanh)
     */
    @PostMapping("/stream")
    public ResponseEntity<?> uploadStream(
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "File rỗng"
            ));
        }

        long uploadedBytes = uploadService.streamUpload(
                file.getInputStream(),
                file.getOriginalFilename(),
                file.getSize()
        );

        return ResponseEntity.ok(Map.of(
                "filename", file.getOriginalFilename(),
                "size", uploadedBytes,
                "message", "Upload thành công"
        ));
    }

    /**
     * ✅ Option 2: Streaming with callback (progress real-time)
     */
    @PostMapping("/stream-progress")
    public ResponseEntity<?> uploadStreamWithProgress(
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "File rỗng"
            ));
        }

        // Progress callback (in production, gửi qua WebSocket)
        OptimizedUploadService.ProgressCallback callback =
                (uploaded, total, percent, speed) -> {
                    log.debug("Progress: {:.1f}% - {:.2f}MB/s", percent, speed);
                };

        long uploadedBytes = uploadService.streamUploadWithCallback(
                file.getInputStream(),
                file.getOriginalFilename(),
                file.getSize(),
                callback
        );

        return ResponseEntity.ok(Map.of(
                "filename", file.getOriginalFilename(),
                "size", uploadedBytes,
                "message", "Upload thành công"
        ));
    }

    /**
     * ✅ Option 3: Atomic streaming (safer - temp file first)
     */
    @PostMapping("/stream-atomic")
    public ResponseEntity<?> uploadStreamAtomic(
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "File rỗng"
            ));
        }

        try {
            long uploadedBytes = uploadService.streamUploadAtomic(
                    file.getInputStream(),
                    file.getOriginalFilename(),
                    file.getSize()
            );

            return ResponseEntity.ok(Map.of(
                    "filename", file.getOriginalFilename(),
                    "size", uploadedBytes,
                    "message", "Upload thành công (atomic)"
            ));

        } catch (IOException e) {
            log.error("Upload atomic failed", e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }
}