package ltldev.SeverUpFile.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ImageService {

    @Value("${app.view-dir:./images}")
    private String viewDir;

    @Value("${app.image-page-size:12}")
    private int pageSize;

    // Cache danh sách file
    private List<String> cachedFiles = null;
    private long lastCacheTime = 0;
    private static final long CACHE_TTL = 60000; // 1 phút

    private static final String[] IMAGE_EXTENSIONS = {
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "tiff"
    };

    private static final String[] VIDEO_EXTENSIONS = {
            "mp4", "webm", "mov", "mkv", "avi", "flv", "m4v"
    };

    /**
     * ✅ Kiểm tra xem file có hợp lệ (ảnh hoặc video)
     */
    private boolean isValidMediaFile(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }
        int dotIndex = filename.lastIndexOf(".");
        if (dotIndex == -1) {
            return false;
        }
        String extension = filename.substring(dotIndex + 1).toLowerCase();

        return Arrays.asList(IMAGE_EXTENSIONS).contains(extension) ||
                Arrays.asList(VIDEO_EXTENSIONS).contains(extension);
    }

    /**
     * ✅ Lấy loại file (image hoặc video)
     */
    public String getMediaType(String filename) {
        if (filename == null) return null;

        int dotIndex = filename.lastIndexOf(".");
        if (dotIndex == -1) return null;

        String extension = filename.substring(dotIndex + 1).toLowerCase();

        if (Arrays.asList(IMAGE_EXTENSIONS).contains(extension)) {
            return "image";
        }
        if (Arrays.asList(VIDEO_EXTENSIONS).contains(extension)) {
            return "video";
        }
        return null;
    }

    /**
     * ✅ Lấy tất cả file (ảnh + video) với cache
     */
    private List<String> getAllMediaFiles() {
        long now = System.currentTimeMillis();

        // Cache hợp lệ?
        if (cachedFiles != null && (now - lastCacheTime) < CACHE_TTL) {
            return cachedFiles;
        }

        // Scan folder
        File folder = new File(viewDir);
        if (!folder.exists()) {
            folder.mkdirs();
            cachedFiles = new ArrayList<>();
            return cachedFiles;
        }

        File[] files = folder.listFiles((dir, name) -> isValidMediaFile(name));
        if (files == null) {
            files = new File[0];
        }

        cachedFiles = Arrays.stream(files)
                .map(File::getName)
                .sorted(Comparator.reverseOrder())  // Mới nhất trước
                .collect(Collectors.toList());

        lastCacheTime = now;
        return cachedFiles;
    }

    /**
     * ✅ Lấy file theo trang (lazy loading)
     */
    public Map<String, Object> getMediaByPage(int page) {
        try {
            List<String> allFiles = getAllMediaFiles();

            int totalCount = allFiles.size();
            int startIndex = page * pageSize;
            int endIndex = Math.min(startIndex + pageSize, totalCount);

            List<Map<String, String>> pageMedia = new ArrayList<>();

            if (startIndex < totalCount) {
                for (String filename : allFiles.subList(startIndex, endIndex)) {
                    Map<String, String> item = new HashMap<>();
                    item.put("filename", filename);
                    item.put("type", getMediaType(filename));
                    pageMedia.add(item);
                }
            }

            boolean hasMore = endIndex < totalCount;

            return Map.of(
                    "media", pageMedia,
                    "page", page,
                    "pageSize", pageSize,
                    "totalCount", totalCount,
                    "hasMore", hasMore
            );

        } catch (Exception e) {
            return Map.of(
                    "media", new ArrayList<>(),
                    "totalCount", 0,
                    "error", e.getMessage(),
                    "hasMore", false
            );
        }
    }

    /**
     * ✅ T��m kiếm file (ảnh + video)
     */
    public List<Map<String, String>> searchMedia(String keyword) {
        try {
            List<String> allFiles = getAllMediaFiles();

            return allFiles.stream()
                    .filter(name -> name.toLowerCase().contains(keyword.toLowerCase()))
                    .map(filename -> {
                        Map<String, String> item = new HashMap<>();
                        item.put("filename", filename);
                        item.put("type", getMediaType(filename));
                        return item;
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * ✅ Lấy đường dẫn file (ngăn path traversal attack)
     */
    public Path getMediaPath(String filename) {
        if (filename == null || filename.isEmpty()) {
            throw new IllegalArgumentException("Tên file không hợp lệ");
        }
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("Tên file không hợp lệ");
        }
        return Paths.get(viewDir).resolve(filename).normalize();
    }

    /**
     * ✅ Lấy view directory
     */
    public String getViewDir() {
        return viewDir;
    }

    /**
     * ✅ Lấy page size
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * ✅ Invalidate cache (khi có file mới)
     */
    public void invalidateCache() {
        cachedFiles = null;
        lastCacheTime = 0;
    }
}