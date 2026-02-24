package ltldev.SeverUpFile.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;

import com.sun.management.OperatingSystemMXBean;

@Component
public class StartupDiagnosticLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupDiagnosticLogger.class);

    @Override
    public void run(ApplicationArguments args) {

        Runtime runtime = Runtime.getRuntime();
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        OperatingSystemMXBean osBean =
                (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        // ================= JVM =================

        long pid = ProcessHandle.current().pid();

        long xms = runtime.totalMemory();
        long xmx = runtime.maxMemory();
        long usedHeap = xms - runtime.freeMemory();

        int threadCount = ManagementFactory.getThreadMXBean().getThreadCount();
        double processCpu = osBean.getProcessCpuLoad() * 100;

        log.info("=================================================");
        log.info("============== JVM INFORMATION ==================");
        log.info("[JVM] PID                    : {}", pid);
        log.info("[JVM] Java Version           : {}", System.getProperty("java.version"));
        log.info("[JVM] JVM Name               : {}", runtimeMXBean.getVmName());
        log.info("[JVM] Initial Heap (Xms)     : {} MB", bytesToMB(xms));
        log.info("[JVM] Max Heap (Xmx)         : {} MB", bytesToMB(xmx));
        log.info("[JVM] Used Heap              : {} MB", bytesToMB(usedHeap));
        log.info("[JVM] Live Threads           : {}", threadCount);
        log.info("[JVM] Process CPU Load       : {:.2f} %", processCpu);
        log.info("=================================================");

        // ================= OS =================

        long totalRam = osBean.getTotalPhysicalMemorySize();
        long freeRam = osBean.getFreePhysicalMemorySize();
        int cpuCores = osBean.getAvailableProcessors();
        double systemCpu = osBean.getSystemCpuLoad() * 100;

        log.info("============== OS INFORMATION ===================");
        log.info("[OS ] Available CPU Cores    : {}", cpuCores);
        log.info("[OS ] Total Physical RAM     : {} MB", bytesToMB(totalRam));
        log.info("[OS ] Free Physical RAM      : {} MB", bytesToMB(freeRam));
        log.info("[OS ] System CPU Load        : {:.2f} %", systemCpu);
        log.info("=================================================");

        log.info("ServerUpload Started Successfully");
        log.info("Heap Usage: {} MB / {} MB",
                bytesToMB(usedHeap),
                bytesToMB(xmx));
        log.info("System RAM Free: {} MB", bytesToMB(freeRam));
        log.info("=================================================");
    }

    private long bytesToMB(long bytes) {
        return bytes / (1024 * 1024);
    }
}