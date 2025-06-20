package com.nitin.controller;

import com.nitin.dto.IndexRequest;
import com.nitin.dto.QueryRequest;
import com.nitin.service.DocumentService;
import com.nitin.service.RagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/rag")
@CrossOrigin(origins = "*") // Enable CORS for web interface
public class RagController {
    @Autowired
    private RagService ragService;

    @Autowired
    private DocumentService documentService;

    @PostMapping("/query")
    public ResponseEntity<String> query(@RequestBody QueryRequest request) {
        try {
            // Handle both query & question field names
            String response = ragService.query(request.getQuery());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error Processing query :" +e.getMessage());
        }
    }

    @PostMapping("/index-file")
    public ResponseEntity<String> indexFiles(@RequestParam("files")MultipartFile[] files){
        try {
            if (files == null || files.length == 0) {
                return ResponseEntity.badRequest().body("No files provided");
            }
            // Process uploaded files
            documentService.processUploadedFiles(files);
            return ResponseEntity.ok("Files indexed successfully");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error indexing files : "+e.getMessage());
        }
    }

    // Indexing all files in a given directory
    @PostMapping("/index-directory")
    public ResponseEntity<String> indexDirectory(@RequestBody Map<String, String> request) {
        try {
            String directoryPath = request.get("directoryPath");
            if (directoryPath == null || directoryPath.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Directory path is required");
            }

            documentService.processStaticFiles(directoryPath);
            return ResponseEntity.ok("Directory indexed successfully");
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Error indexing directory: " + e.getMessage());
        }
    }
    // Clear all the indexed files
    @PostMapping("/clear")
    public ResponseEntity<String> clearIndex() {
        try {
            documentService.clearIndex();
            return ResponseEntity.ok("Index cleared successfully");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error clearing index : "+e.getMessage());
        }
    }
}
