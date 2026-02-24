package ltldev.SeverUpFile.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MonitorViewController {

    @GetMapping("/monitor")
    public String monitorPage() {
        return "monitor";   // file monitor.html trong templates
    }
}