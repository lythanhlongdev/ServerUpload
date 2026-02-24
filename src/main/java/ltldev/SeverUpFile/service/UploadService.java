package ltldev.SeverUpFile.service;

import ltldev.SeverUpFile.logging.AppLogger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.*;

@Service
public class UploadService {

    @Value("${app.upload-dir}")
    private String uploadDir;


    // add log
    public HttpStatus handleUpload(MultipartFile file) {
        long startTime = System.currentTimeMillis();

        String originalFilename = file.getOriginalFilename();
        long fileSize = file.getSize();
        double fileSizeMB = fileSize / 1024.0 / 1024.0;

//        AppLogger.info(String.format(
//                "[UPLOAD][START] file=%s size=%.2fMB",
//                originalFilename,
//                fileSizeMB
//        ));

        try {

            Path uploadPath = Paths.get(uploadDir)
                    .toAbsolutePath()
                    .normalize();

            if (!Files.exists(uploadPath)) {
                AppLogger.info("[UPLOAD] Tạo thư mục upload");
                Files.createDirectories(uploadPath);
            }

            if (originalFilename == null || originalFilename.contains("..")) {
                AppLogger.warn("[UPLOAD][BAD_NAME] " + originalFilename);
                return HttpStatus.BAD_REQUEST;
            }

            Path targetLocation = uploadPath.resolve(originalFilename);

            if (Files.exists(targetLocation)) {
//                AppLogger.warn("[UPLOAD][EXISTS] " + originalFilename);
                return HttpStatus.CONFLICT;
            }

            // 🔥 CHECK DISK
            FileStore fileStore = Files.getFileStore(uploadPath);
            long usableSpace = fileStore.getUsableSpace();
            double usableMB = usableSpace / 1024.0 / 1024.0;

            AppLogger.info(String.format(
                    "[UPLOAD][CHECK] free=%.2fMB required=%.2fMB",
                    usableMB,
                    fileSizeMB
            ));

            if (usableSpace < fileSize) {
                AppLogger.warn("[UPLOAD][NO_SPACE] " + originalFilename);
                return HttpStatus.INSUFFICIENT_STORAGE;
            }

            // 🔥 COPY FILE
            try (InputStream inputStream = file.getInputStream()) {
                AppLogger.info("Active threads before start copy file  " + Thread.activeCount());
                Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            }

            long durationMs = System.currentTimeMillis() - startTime;
            double durationSec = durationMs / 1000.0;
            double speed = durationSec > 0 ? fileSizeMB / durationSec : fileSizeMB;

            AppLogger.info(String.format(
                    "[UPLOAD][SUCCESS] file=%s duration=%.2fs speed=%.2fMB/s",
                    originalFilename,
                    durationSec,
                    speed
            ));

            return HttpStatus.OK;

        } catch (Exception e) {

            AppLogger.error(String.format(
                    "[UPLOAD][ERROR] file=%s msg=%s",
                    originalFilename,
                    e.getMessage()
            ));

            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
//
//    public HttpStatus handleUpload(MultipartFile file) {
//
//        AppLogger.info("Nhận file: " + file.getOriginalFilename());
//
//        try {
//
//            Path uploadPath = Paths.get(uploadDir)
//                    .toAbsolutePath()
//                    .normalize();
//
//            if (!Files.exists(uploadPath)) {
//                AppLogger.info("Tạo thư mục upload");
//                Files.createDirectories(uploadPath);
//            }
//
//            String originalFilename = file.getOriginalFilename();
//
//            if (originalFilename == null || originalFilename.contains("..")) {
//                AppLogger.warn("Tên file không hợp lệ");
//                return HttpStatus.BAD_REQUEST;
//            }
//
//            Path targetLocation = uploadPath.resolve(originalFilename);
//
//            if (Files.exists(targetLocation)) {
//                AppLogger.warn("File đã tồn tại: " + originalFilename);
//                return HttpStatus.CONFLICT;
//            }
//
//            // 🔥 KIỂM TRA DUNG LƯỢNG DISK
//            FileStore fileStore = Files.getFileStore(uploadPath);
//            long usableSpace = fileStore.getUsableSpace();
//            long fileSize = file.getSize();
//
//            AppLogger.info("Dung lượng còn trống: " + usableSpace / (1024 * 1024) + " MB");
//            AppLogger.info("Dung lượng file: " + fileSize / (1024 * 1024) + " MB");
//
//            if (usableSpace < fileSize) {
//                AppLogger.warn("Không đủ dung lượng để upload");
//                return HttpStatus.INSUFFICIENT_STORAGE; // 507
//            }
//
//            // 🔥 COPY STREAM AN TOÀN
//            try (InputStream inputStream = file.getInputStream()) {
//                Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
//            }
//
//            AppLogger.info("Upload thành công: " + originalFilename);
//
//            return HttpStatus.OK;
//
//        } catch (Exception e) {
//
//            AppLogger.error("Lỗi upload: " + e.getMessage());
//            return HttpStatus.INTERNAL_SERVER_ERROR;
//        }
//    }
}
