package com.nitin.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class WebController {
    private static final Logger logger = LoggerFactory.getLogger(WebController.class);

    @GetMapping("/")
    public String index() {
        logger.info("Redirecting to /chat");
        return "redirect:/chat";
    }

    @GetMapping("/chat")
    public String chat(Model model, @RequestParam(value = "dir", required = false, defaultValue = "") String directoryPath) {
        logger.info("Serving chat page with directoryPath: {}", directoryPath);
        model.addAttribute("directoryPath", directoryPath);
        return "chat-page";
    }
    @GetMapping("/test")
    public String test(Model model) {
        model.addAttribute("directoryPath", "test-path");
        return "chat-page";
    }
    // Diagnostic endpoints
    @GetMapping("/debug")
    @ResponseBody
    public String debug() {
        return "Controller is working! If you see this, the issue is with template resolution.";
    }

    @GetMapping("/working-test")
    @ResponseBody
    public String workingTest() {
        return "test";
    }

}
