package ltldev.SeverUpFile.controller;

import ltldev.SeverUpFile.service.ImageService;
import ltldev.SeverUpFile.util.FileRangeResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/media")
@CrossOrigin(origins = "*")
public class ImageController {

    @Autowired
    private ImageService imageService;

    /**
     * 📸📹 Lấy ảnh + video theo trang (lazy load)
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getMedia(
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(imageService.getMediaByPage(page));
    }

    /**
     * 🔍 Tìm kiếm ảnh + video
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchMedia(
            @RequestParam String keyword) {
        List<Map<String, String>> results = imageService.searchMedia(keyword);
        return ResponseEntity.ok(Map.of(
                "media", results,
                "keyword", keyword,
                "count", results.size()
        ));
    }

    /**
     * 📥 Download/View ảnh hoặc video (hỗ trợ Range Request)
     */
    @GetMapping("/{filename}")
    public ResponseEntity<?> getMedia(
            @PathVariable String filename,
            @RequestHeader(value = "Range", required = false) String range) {
        try {
            Path filePath = imageService.getMediaPath(filename);
            File file = filePath.toFile();

            if (!file.exists() || !file.canRead()) {
                return ResponseEntity.notFound().build();
            }

            long fileSize = file.length();
            String mediaType = imageService.getMediaType(filename);

            // ✅ Hỗ trợ Range Request (video streaming)
            if (range != null) {
                return handleRangeRequest(file, range, fileSize, mediaType, filename);
            }

            // Ảnh nhỏ - stream bình thường
            if ("image".equals(mediaType) && fileSize < 10 * 1024 * 1024) { // < 10MB
                Resource resource = new UrlResource(filePath.toUri());
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "inline; filename=\"" + filename + "\"")
                        .body(resource);
            }

            // File lớn - dùng Range Request
            return handleRangeRequest(file, "bytes=0-" + (fileSize - 1), fileSize, mediaType, filename);

        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * ✅ Xử lý Range Request (stream video mà không load hết RAM)
     */
    private ResponseEntity<?> handleRangeRequest(
            File file, String range, long fileSize, String mediaType, String filename) {
        try {
            // Parse range header: "bytes=0-1023"
            long rangeStart = 0;
            long rangeEnd = fileSize - 1;

            if (range != null && range.startsWith("bytes=")) {
                String[] ranges = range.substring(6).split("-");
                rangeStart = Long.parseLong(ranges[0]);

                if (ranges.length > 1 && !ranges[1].isEmpty()) {
                    rangeEnd = Long.parseLong(ranges[1]);
                }
            }

            long contentLength = rangeEnd - rangeStart + 1;

            // ✅ Chỉ stream chunk cần thiết, không load hết vào RAM
            return ResponseEntity
                    .status(HttpStatus.PARTIAL_CONTENT)
                    .header(HttpHeaders.CONTENT_TYPE, getContentType(mediaType, filename))
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength))
                    .header(HttpHeaders.CONTENT_RANGE,
                            "bytes " + rangeStart + "-" + rangeEnd + "/" + fileSize)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + filename + "\"")
                    .body(new FileRangeResource(file, rangeStart, contentLength));

        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * ✅ Lấy content type dựa trên extension
     */
    private String getContentType(String mediaType, String filename) {
        if ("video".equals(mediaType)) {
            String ext = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
            switch (ext) {
                case "mp4": return "video/mp4";
                case "webm": return "video/webm";
                case "mov": return "video/quicktime";
                case "mkv": return "video/x-matroska";
                case "avi": return "video/x-msvideo";
                case "flv": return "video/x-flv";
                default: return "video/mp4";
            }
        } else {
            String ext = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
            switch (ext) {
                case "jpg":
                case "jpeg": return "image/jpeg";
                case "png": return "image/png";
                case "gif": return "image/gif";
                case "webp": return "image/webp";
                case "bmp": return "image/bmp";
                case "tiff": return "image/tiff";
                default: return "image/jpeg";
            }
        }
    }

    /**
     * ℹ️ Lấy thông tin hệ thống
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, String>> getInfo() {
        return ResponseEntity.ok(Map.of(
                "viewDir", imageService.getViewDir(),
                "status", "ready"
        ));
    }
    /**
     * ✅ Map /view -> view-images.html
     */
    @GetMapping("/view")
    public String viewImages() {
        return "view-images";  // Trỏ đến templates/view-images.html
    }
}