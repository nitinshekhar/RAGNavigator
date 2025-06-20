package com.nitin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@Service
public class LlamaService {
    private static final Logger logger = LoggerFactory.getLogger(LlamaService.class);

    @Value("${llama.server.url}")
    private String llamaServerUrl;

    @Value("${llama.completion.max-tokens:512}")
    private int maxTokens;

    @Value("${llama.completion.temperature:0.7}")
    private double temperature;

    @Value("${llama.completion.timeout:30}")
    private int timeoutSeconds;

    private final RestTemplate restTemplate;

    public LlamaService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String generateResponse(String prompt, String context) {
        logger.debug("Generating response for prompt length: {}, context length: {}",
                prompt.length(), context.length());
        try {
            // Check if server is accessible
            if (!isServerHealthy()) {
                throw new RuntimeException("Llama.cpp server is not accessible at: " + llamaServerUrl);
            }

            String fullPrompt = buildPrompt(prompt, context);
            logger.debug("Full prompt length: {}", fullPrompt.length());

            Map<String, Object> requestBody = buildRequestBody(fullPrompt);
            HttpEntity<Map<String, Object>> request = createHttpEntity(requestBody);
            long startTime = System.currentTimeMillis();
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    llamaServerUrl + "/completion", request, Map.class);
            long duration = System.currentTimeMillis() - startTime;

            logger.info("LLM response received in {}ms", duration);

            return extractResponseContent(response);

        } catch (ResourceAccessException e) {
            logger.error("Failed to connect to llama.cpp server at {}: {}", llamaServerUrl, e.getMessage());
            throw new RuntimeException("Cannot connect to llama.cpp server. Please ensure it's running.", e);
        } catch (HttpClientErrorException e) {
            logger.error("Client error calling llama.cpp server: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Invalid request to llama.cpp server: " + e.getMessage(), e);
        } catch (HttpServerErrorException e) {
            logger.error("Server error from llama.cpp: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("llama.cpp server error: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error calling llama.cpp server", e);
            throw new RuntimeException("Error calling llama.cpp server: " + e.getMessage(), e);
        }
    }

    private boolean isServerHealthy() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(llamaServerUrl + "/health", String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            logger.warn("Health check failed for llama.cpp server: {}", e.getMessage());
            return false;
        }
    }

    private Map<String, Object> buildRequestBody(String fullPrompt) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("prompt", fullPrompt);
        requestBody.put("n_predict", maxTokens);
        requestBody.put("temperature", temperature);
        requestBody.put("top_k", 40);
        requestBody.put("top_p", 0.9);
        requestBody.put("repeat_penalty", 1.1);
        requestBody.put("stop", Arrays.asList("\n\n", "Human:", "Context:", "Question:"));
        requestBody.put("stream", false);

        return requestBody;
    }

    private HttpEntity<Map<String, Object>> createHttpEntity(Map<String, Object> requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);

        return new HttpEntity<>(requestBody, headers);
    }

    private String extractResponseContent(ResponseEntity<Map> response) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to get response from llama.cpp server. Status: " + response.getStatusCode());
        }

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null) {
            throw new RuntimeException("Empty response from llama.cpp server");
        }

        String content = (String) responseBody.get("content");
        if (content == null || content.trim().isEmpty()) {
            logger.warn("Empty content received from llama.cpp server");
            return "I apologize, but I couldn't generate a proper response. Please try rephrasing your question.";
        }

        // Clean up the response
        content = content.trim();

        // Remove any prompt artifacts that might have leaked through
        content = cleanResponse(content);

        logger.debug("Generated response length: {}", content.length());
        return content;
    }

    private String cleanResponse(String response) {
        // Remove common prompt artifacts
        String[] artifactsToRemove = {
                "Context:", "Question:", "Answer based on the context provided:",
                "Based on the context provided:", "According to the context:"
        };

        String cleaned = response;
        for (String artifact : artifactsToRemove) {
            if (cleaned.startsWith(artifact)) {
                cleaned = cleaned.substring(artifact.length()).trim();
            }
        }

        return cleaned;
    }

    private String buildPrompt(String question, String context) {
        // Truncate context if too long (keep within reasonable token limits)
        String truncatedContext = context.length() > 4000 ?
                context.substring(0, 4000) + "..." : context;

        return String.format("""
                You are a helpful assistant that answers questions based on the provided context. 
                Only use information from the context to answer the question. If the context doesn't 
                contain enough information to answer the question, say so clearly.
                
                Context:
                %s
                
                Question: %s
                
                Answer:""", truncatedContext, question);
    }

    // Utility method for testing connectivity
    public boolean testConnection() {
        try {
            return isServerHealthy();
        } catch (Exception e) {
            logger.error("Connection test failed", e);
            return false;
        }
    }
}
