package ltldev.SeverUpFile.service;


import org.apache.coyote.AbstractProtocol;
import org.apache.coyote.ProtocolHandler;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.stereotype.Service;
import org.apache.catalina.connector.Connector;

import java.lang.management.*;
import java.util.*;

import com.sun.management.OperatingSystemMXBean;

@Service
public class MonitorService {

    private final WebServerApplicationContext context;

    public MonitorService(WebServerApplicationContext context) {
        this.context = context;
    }

    public Map<String, Object> getSystemMonitor() {

        Map<String, Object> result = new LinkedHashMap<>();

        Runtime runtime = Runtime.getRuntime();
        OperatingSystemMXBean osBean =
                (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        long usedHeap = runtime.totalMemory() - runtime.freeMemory();
        long maxHeap = runtime.maxMemory();

        // ================= JVM =================
        Map<String, Object> jvm = new LinkedHashMap<>();

        jvm.put("pid", ProcessHandle.current().pid());
        jvm.put("javaVersion", System.getProperty("java.version"));
        jvm.put("usedHeapMB", toMB(usedHeap));
        jvm.put("maxHeapMB", toMB(maxHeap));
        jvm.put("heapUsagePercent", percent(usedHeap, maxHeap));
        jvm.put("liveThreads",
                ManagementFactory.getThreadMXBean().getThreadCount());
        jvm.put("processCpuPercent",
                round(osBean.getProcessCpuLoad() * 100));

        // ================= OS =================
        Map<String, Object> os = new LinkedHashMap<>();

        long totalRam = osBean.getTotalPhysicalMemorySize();
        long freeRam = osBean.getFreePhysicalMemorySize();

        os.put("cpuCores", osBean.getAvailableProcessors());
        os.put("systemCpuPercent",
                round(osBean.getSystemCpuLoad() * 100));
        os.put("totalRamMB", toMB(totalRam));
        os.put("freeRamMB", toMB(freeRam));
        os.put("ramUsagePercent",
                percent(totalRam - freeRam, totalRam));

        // ================= TOMCAT =================
        Map<String, Object> tomcatInfo = new LinkedHashMap<>();

        try {

            TomcatWebServer tomcat =
                    (TomcatWebServer) context.getWebServer();

            Connector connector =
                    tomcat.getTomcat()
                            .getService()
                            .findConnectors()[0];

            ProtocolHandler handler = connector.getProtocolHandler();

            tomcatInfo.put("port", connector.getPort());
            tomcatInfo.put("protocol", connector.getProtocol());

            if (handler instanceof AbstractProtocol<?> protocol) {

                tomcatInfo.put("maxThreads",
                        protocol.getMaxThreads());
                tomcatInfo.put("connectionTimeout",
                        protocol.getConnectionTimeout());

                if (protocol.getExecutor() instanceof ThreadPoolExecutor executor) {

                    tomcatInfo.put("activeThreads",
                            executor.getActiveCount());
                    tomcatInfo.put("poolSize",
                            executor.getPoolSize());
                    tomcatInfo.put("queueSize",
                            executor.getQueue().size());
                }
            }

        } catch (Exception e) {
            tomcatInfo.put("error",
                    "Tomcat metrics unavailable: " + e.getMessage());
        }

        result.put("jvm", jvm);
        result.put("os", os);
        result.put("tomcat", tomcatInfo);

        return result;
    }

    private long toMB(long bytes) {
        return bytes / (1024 * 1024);
    }

    private double percent(long value, long total) {
        return round((double) value / total * 100);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}