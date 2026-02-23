package ltldev.SeverUpFile.controller;

import ltldev.SeverUpFile.logging.AppLogger;
import ltldev.SeverUpFile.service.UploadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            AppLogger.warn("File rỗng");
            return ResponseEntity.badRequest().body("File rỗng");
        }

        var status = uploadService.handleUpload(file);

        if (status.is2xxSuccessful()) {
            return ResponseEntity.ok("Upload thành công");
        }

        if (status.value() == 409) {
            return ResponseEntity.status(409).body("File đã tồn tại");
        }

        return ResponseEntity.status(status).body("Upload thất bại");
    }
}