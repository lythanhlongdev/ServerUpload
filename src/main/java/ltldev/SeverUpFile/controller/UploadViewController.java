package ltldev.SeverUpFile.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UploadViewController {

    @GetMapping("/")
    public String index() {
        return "index";  // Trỏ tới batch-upload.html
    }

    @GetMapping("/upload")
    public String upload() {
        return "batch-upload";
    }
}