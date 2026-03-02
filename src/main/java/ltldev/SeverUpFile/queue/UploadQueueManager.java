package ltldev.SeverUpFile.queue;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class UploadQueueManager {

    // ========== CONFIGURATION ==========
    @Value("${upload.queue.large-file-threshold:536870912}") // 512MB
    private long largeFileThreshold;

    @Value("${upload.queue.medium-file-threshold:104857600}") // 100MB
    private long mediumFileThreshold;

    // ========== LANES: Separate ThreadPools ==========

    // Lane 1: Large files (1 concurrent) - sequential, lock memory
    private final ExecutorService largeLane = new ThreadPoolExecutor(
            1, 1, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(10),
            createThreadFactory("large-upload")
    );

    // Lane 2: Medium files (5 concurrent) - controlled parallel
    private final ExecutorService mediumLane = new ThreadPoolExecutor(
            3, 5, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(20),
            createThreadFactory("medium-upload")
    );

    // Lane 3: Small files (batch) (20 concurrent) - full parallel
    private final ExecutorService smallLane = new ThreadPoolExecutor(
            10, 20, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            createThreadFactory("small-upload")
    );

    // ========== METRICS ==========
    private final AtomicInteger largeQueueSize = new AtomicInteger(0);
    private final AtomicInteger mediumQueueSize = new AtomicInteger(0);
    private final AtomicInteger smallQueueSize = new AtomicInteger(0);

    private final Map<String, UploadTask> uploadTasks = new ConcurrentHashMap<>();

    // ========== CLASSIFYING UPLOADS ==========

    public enum UploadLane {
        LARGE,    // >512MB - sequential
        MEDIUM,   // 100-512MB - controlled
        SMALL     // <100MB - parallel
    }

    /**
     * Classify upload size and return appropriate lane
     */
    public UploadLane classifyUpload(long fileSize) {
        if (fileSize > largeFileThreshold) {
            return UploadLane.LARGE;
        } else if (fileSize > mediumFileThreshold) {
            return UploadLane.MEDIUM;
        } else {
            return UploadLane.SMALL;
        }
    }

    /**
     * Submit upload to appropriate lane
     */
    public String submitUpload(
            String uploadId,
            String filename,
            long fileSize,
            Runnable uploadTask) {

        UploadLane lane = classifyUpload(fileSize);

        UploadTask task = UploadTask.builder()
                .uploadId(uploadId)
                .filename(filename)
                .fileSize(fileSize)
                .lane(lane)
                .submittedAt(System.currentTimeMillis())
                .build();

        uploadTasks.put(uploadId, task);

        log.info("UPLOAD_QUEUED uploadId={} filename={} size={} lane={}",
                uploadId, filename, fileSize, lane);

        try {
            ExecutorService executor = getExecutor(lane);

            // Wrap task with monitoring
            executor.submit(() -> {
                try {
                    task.setStartedAt(System.currentTimeMillis());
                    task.setState(UploadTask.State.IN_PROGRESS);

                    uploadTask.run();

                    task.setState(UploadTask.State.SUCCESS);
                    task.setCompletedAt(System.currentTimeMillis());

                } catch (Exception e) {
                    log.error("Upload failed: {}", uploadId, e);
                    task.setState(UploadTask.State.FAILED);
                    task.setError(e.getMessage());
                }
            });

            return lane.toString();

        } catch (RejectedExecutionException e) {
            log.error("Upload queue full: {} - {}", uploadId, lane);
            throw new RuntimeException("Upload queue full. Try again later.", e);
        }
    }

    /**
     * Get executor by lane
     */
    private ExecutorService getExecutor(UploadLane lane) {
        return switch (lane) {
            case LARGE -> largeLane;
            case MEDIUM -> mediumLane;
            case SMALL -> smallLane;
        };
    }

    /**
     * Get queue statistics
     */
    public Map<String, Object> getQueueStats() {
        return Map.of(
                "large", Map.of(
                        "queue_size", largeQueueSize.get(),
                        "capacity", 10,
                        "active_threads", ((ThreadPoolExecutor)largeLane).getActiveCount()
                ),
                "medium", Map.of(
                        "queue_size", mediumQueueSize.get(),
                        "capacity", 20,
                        "active_threads", ((ThreadPoolExecutor)mediumLane).getActiveCount()
                ),
                "small", Map.of(
                        "queue_size", smallQueueSize.get(),
                        "capacity", 100,
                        "active_threads", ((ThreadPoolExecutor)smallLane).getActiveCount()
                ),
                "total_tasks", uploadTasks.size()
        );
    }

    /**
     * Get upload status
     */
    public UploadTask getUploadStatus(String uploadId) {
        return uploadTasks.get(uploadId);
    }

    private ThreadFactory createThreadFactory(String name) {
        return new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger();
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, name + "-" + count.incrementAndGet());
                t.setDaemon(false);
                t.setPriority(Thread.NORM_PRIORITY);
                return t;
            }
        };
    }

    public void shutdown() {
        largeLane.shutdown();
        mediumLane.shutdown();
        smallLane.shutdown();
    }
}

// ========== DATA CLASS ==========

@Data
@lombok.Builder
class UploadTask {
    enum State {
        QUEUED, IN_PROGRESS, SUCCESS, FAILED, CANCELLED
    }

    String uploadId;
    String filename;
    long fileSize;
    UploadQueueManager.UploadLane lane;
    State state = State.QUEUED;

    long submittedAt;
    long startedAt;
    long completedAt;
    String error;

    public long getQueueWaitMs() {
        return startedAt - submittedAt;
    }

    public long getDurationMs() {
        return completedAt - startedAt;
    }

    public double getSpeedMBps() {
        long duration = getDurationMs();
        if (duration == 0) return 0;
        return (fileSize / (1024.0 * 1024.0)) / (duration / 1000.0);
    }
}