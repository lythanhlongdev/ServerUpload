package ltldev.SeverUpFile.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ltldev.SeverUpFile.dto.UploadResult;
import ltldev.SeverUpFile.service.UploadServiceEnhanced;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class UploadControllerEnhanced {

    private final UploadServiceEnhanced uploadService;

    @PostMapping("")
    public ResponseEntity<UploadResult> upload(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {

        String clientIp = getClientIp(request);
        String device = detectDevice(request.getHeader("User-Agent"));

        log.info("UPLOAD_REQUEST file={} size={} ip={} device={}",
            file.getOriginalFilename(),
            file.getSize(),
            clientIp,
            device);

        UploadResult result = uploadService.handleUpload(file, clientIp, device);

        return ResponseEntity
            .status(result.getHttpStatus())
            .body(result);
    }

    @GetMapping("/status")
    public ResponseEntity<String> getStatus() {
        return ResponseEntity.ok("Upload service is running");
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String detectDevice(String userAgent) {
        if (userAgent == null) return "UNKNOWN";

        String ua = userAgent.toLowerCase();
        if (ua.contains("android")) return "ANDROID";
        if (ua.contains("iphone")) return "IPHONE";
        if (ua.contains("mobile")) return "MOBILE";
        if (ua.contains("windows")) return "WINDOWS";
        if (ua.contains("mac")) return "MACOS";
        if (ua.contains("linux")) return "LINUX";

        return "BROWSER";
    }
}