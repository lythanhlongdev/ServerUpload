package ltldev.SeverUpFile.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class SmartMemoryManager {

    @Value("${upload.memory.max-usage-percent:75}")
    private double maxMemoryUsagePercent;

    @Value("${upload.memory.warn-percent:60}")
    private double warnMemoryPercent;

    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private final AtomicLong totalStreamingBytes = new AtomicLong(0);

    // ========== MEMORY MONITORING ==========

    /**
     * Check if memory is available for new upload
     */
    public boolean canAcceptUpload(long estimatedMemoryNeeded) {
        long usedHeap = memoryMXBean.getHeapMemoryUsage().getUsed();
        long maxHeap = memoryMXBean.getHeapMemoryUsage().getMax();

        double currentUsagePercent = (usedHeap * 100.0) / maxHeap;

        if (currentUsagePercent + (estimatedMemoryNeeded * 100.0 / maxHeap) > maxMemoryUsagePercent) {
            log.warn("MEMORY_PRESSURE current={:.1f}% needed={} max_allowed={:.1f}%",
                    currentUsagePercent,
                    estimatedMemoryNeeded,
                    maxMemoryUsagePercent);
            return false;
        }

        if (currentUsagePercent > warnMemoryPercent) {
            log.warn("MEMORY_WARNING current_usage={:.1f}%", currentUsagePercent);
            triggerGarbageCollection();
        }

        return true;
    }

    /**
     * Register streaming bytes (for multiple concurrent streams)
     */
    public void registerStream(long streamSize) {
        totalStreamingBytes.addAndGet(streamSize);
        log.debug("Stream registered: total_streaming={}MB",
                totalStreamingBytes.get() / (1024.0 * 1024.0));
    }

    /**
     * Unregister stream when done
     */
    public void unregisterStream(long streamSize) {
        totalStreamingBytes.addAndGet(-streamSize);
    }

    /**
     * Get current memory stats
     */
    public MemoryStats getMemoryStats() {
        long usedHeap = memoryMXBean.getHeapMemoryUsage().getUsed();
        long maxHeap = memoryMXBean.getHeapMemoryUsage().getMax();
        long freeHeap = maxHeap - usedHeap;

        return MemoryStats.builder()
                .usedHeapMB(usedHeap / (1024 * 1024))
                .maxHeapMB(maxHeap / (1024 * 1024))
                .freeHeapMB(freeHeap / (1024 * 1024))
                .usagePercent((usedHeap * 100.0) / maxHeap)
                .totalStreamingMB(totalStreamingBytes.get() / (1024.0 * 1024.0))
                .build();
    }

    private void triggerGarbageCollection() {
        log.info("Triggering garbage collection");
        System.gc();
    }

    @lombok.Data
    @lombok.Builder
    public static class MemoryStats {
        long usedHeapMB;
        long maxHeapMB;
        long freeHeapMB;
        double usagePercent;
        double totalStreamingMB;
    }
}