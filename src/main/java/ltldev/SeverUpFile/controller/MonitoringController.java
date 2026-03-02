package ltldev.SeverUpFile.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ltldev.SeverUpFile.service.UploadMetricsService;
import ltldev.SeverUpFile.service.MonitorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final MonitorService systemMonitor;
    private final UploadMetricsService uploadMetrics;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(health);
    }

    @GetMapping("/metrics/upload")
    public ResponseEntity<Object> getUploadMetrics() {
        return ResponseEntity.ok(uploadMetrics.getMetrics());
    }

    @GetMapping("/metrics/system")
    public ResponseEntity<Object> getSystemMetrics() {
        return ResponseEntity.ok(systemMonitor.getSystemMonitor());
    }

    @GetMapping("/metrics/combined")
    public ResponseEntity<Map<String, Object>> getCombinedMetrics() {
        Map<String, Object> combined = new HashMap<>();
        combined.put("upload", uploadMetrics.getMetrics());
        combined.put("system", systemMonitor.getSystemMonitor());
        combined.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(combined);
    }
}