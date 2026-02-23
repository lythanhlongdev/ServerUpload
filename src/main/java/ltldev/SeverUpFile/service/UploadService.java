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

    public HttpStatus handleUpload(MultipartFile file) {

        AppLogger.info("Nhận file: " + file.getOriginalFilename());

        try {

            Path uploadPath = Paths.get(uploadDir)
                    .toAbsolutePath()
                    .normalize();

            if (!Files.exists(uploadPath)) {
                AppLogger.info("Tạo thư mục upload");
                Files.createDirectories(uploadPath);
            }

            String originalFilename = file.getOriginalFilename();

            if (originalFilename == null || originalFilename.contains("..")) {
                AppLogger.warn("Tên file không hợp lệ");
                return HttpStatus.BAD_REQUEST;
            }

            Path targetLocation = uploadPath.resolve(originalFilename);

            if (Files.exists(targetLocation)) {
                AppLogger.warn("File đã tồn tại: " + originalFilename);
                return HttpStatus.CONFLICT;
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetLocation);
            }

            AppLogger.info("Upload thành công: " + originalFilename);

            return HttpStatus.OK;

        } catch (Exception e) {

            AppLogger.error("Lỗi upload: " + e.getMessage());
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}
