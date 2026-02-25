package ltldev.SeverUpFile.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class ViewController {

    /**
     * ✅ Map /view -> view-images.html
     */
    @GetMapping("/view")
    public ModelAndView viewImages() {
        return new ModelAndView("view-images");
    }
}