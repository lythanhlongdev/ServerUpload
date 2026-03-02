//package ltldev.SeverUpFile.controller;
//
//import jakarta.servlet.http.HttpServletRequest;
//import lombok.Data;
//import ltldev.SeverUpFile.logging.AppLogger;
//import ltldev.SeverUpFile.service.UploadService;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.multipart.MultipartFile;
//
//@Data
//@RestController
//@RequestMapping("/upload")
//public class UploadController {
//
//    private final UploadService uploadService;
//
//    @PostMapping("")
//    public ResponseEntity<String> upload(
//            @RequestParam("file") MultipartFile file,
//            HttpServletRequest request) {
//
//        if (file == null || file.isEmpty()) {
//            AppLogger.warn("File rỗng");
//            return ResponseEntity.badRequest().body("File rỗng");
//        }
//        String clientIp = getClientIp(request);
//        String device = detectDevice(request.getHeader("User-Agent"));
//
//        AppLogger.info(String.format(
//                "[UPLOAD][REQUEST] file = %s size = %.2fMB ip = %s device = %s",
//                file.getOriginalFilename(),
//                file.getSize() / 1024.0 / 1024.0,
//                clientIp,
//                device
//        ));
//
//        var status = uploadService.handleUpload(file);
//
//        switch (status) {
//
//            case OK:
////                AppLogger.info("[UPLOAD][SUCCESS] " + file.getOriginalFilename());
//                return ResponseEntity.ok("Upload thành công");
//
//            case CONFLICT:
//                AppLogger.warn("[UPLOAD][EXISTS] " + file.getOriginalFilename());
//                return ResponseEntity.status(409).body("File đã tồn tại");
//
//            case INSUFFICIENT_STORAGE:
//                AppLogger.warn("[UPLOAD][NO_SPACE] " + file.getOriginalFilename());
//                return ResponseEntity.status(507).body("Disk không đủ dung lượng");
//
//            case BAD_REQUEST:
//                AppLogger.warn("[UPLOAD][BAD_NAME] " + file.getOriginalFilename());
//                return ResponseEntity.badRequest().body("Tên file không hợp lệ");
//
//            default:
//                AppLogger.error("[UPLOAD][ERROR] " + file.getOriginalFilename());
//                return ResponseEntity.status(500).body("Upload thất bại");
//        }
//    }
//
//    private String getClientIp(HttpServletRequest request) {
//        String xfHeader = request.getHeader("X-Forwarded-For");
//        if (xfHeader == null) {
//            return request.getRemoteAddr();
//        }
//        return xfHeader.split(",")[0];
//    }
//
//    private String detectDevice(String userAgent) {
//        if (userAgent == null) return "UNKNOWN";
//
//        String ua = userAgent.toLowerCase();
//
//        if (ua.contains("android")) return "ANDROID";
//        if (ua.contains("iphone")) return "IPHONE";
//        if (ua.contains("mobile")) return "MOBILE";
//        if (ua.contains("windows")) return "WINDOWS-PC";
//        if (ua.contains("mac")) return "MAC";
//        if (ua.contains("linux")) return "LINUX-PC";
//
//        return "OTHER";
//    }
//}
//
////package ltldev.SeverUpFile.controller;
////
////import lombok.Data;
////import ltldev.SeverUpFile.logging.AppLogger;
////import ltldev.SeverUpFile.service.UploadService;
////import org.springframework.http.ResponseEntity;
////import org.springframework.web.bind.annotation.*;
////import org.springframework.web.multipart.MultipartFile;
////@Data
////@RestController
////public class UploadController {
////
////    private final UploadService uploadService;
////
////    @PostMapping("/upload")
////    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) {
////
////        if (file == null || file.isEmpty()) {
////            AppLogger.warn("File rỗng");
////            return ResponseEntity.badRequest().body("File rỗng");
////        }
////
////        var status = uploadService.handleUpload(file);
////
////        switch (status) {
////
////            case OK:
////                return ResponseEntity.ok("Upload thành công");
////
////            case CONFLICT:
////                return ResponseEntity.status(409).body("File đã tồn tại");
////
////            case INSUFFICIENT_STORAGE:
////                return ResponseEntity.status(507).body("Disk không đủ dung lượng");
////
////            case BAD_REQUEST:
////                return ResponseEntity.badRequest().body("Tên file không hợp lệ");
////
////            default:
////                return ResponseEntity.status(500).body("Upload thất bại");
////        }
////    }
//////    public UploadController(UploadService uploadService) {
//////        this.uploadService = uploadService;
//////    }
//////
//////    @PostMapping("/upload")
//////    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) {
//////
//////        if (file == null || file.isEmpty()) {
//////            AppLogger.warn("File rỗng");
//////            return ResponseEntity.badRequest().body("File rỗng");
//////        }
//////
//////        var status = uploadService.handleUpload(file);
//////
//////        if (status.is2xxSuccessful()) {
//////            return ResponseEntity.ok("Upload thành công");
//////        }
//////
//////        if (status.value() == 409) {
//////            return ResponseEntity.status(409).body("File đã tồn tại");
//////        }
//////
//////        return ResponseEntity.status(status).body("Upload thất bại");
//////    }
////}