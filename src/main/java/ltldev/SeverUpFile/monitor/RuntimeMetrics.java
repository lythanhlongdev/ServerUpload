package ltldev.SeverUpFile.monitor;

import java.util.Map;

public record RuntimeMetrics(
        Map<String, Object> jvm,
        Map<String, Object> os,
        Map<String, Object> tomcat
) {}