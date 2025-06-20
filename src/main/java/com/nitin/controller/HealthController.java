package com.nitin.controller;

import com.nitin.service.DocumentService;
import com.nitin.service.LlamaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/actuator/health")
public class HealthController {

    @Autowired
    private LlamaService llamaService;

    @Autowired
    private DocumentService documentService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        boolean isHealthy = llamaService.testConnection();

        health.put("status", isHealthy ? "UP" : "DOWN");
        health.put("documentCount", documentService.getDocumentCount());
        health.put("timestamp", System.currentTimeMillis());

        return isHealthy
                ? ResponseEntity.ok(health)
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
    }
}