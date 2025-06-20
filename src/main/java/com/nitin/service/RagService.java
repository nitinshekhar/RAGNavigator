package com.nitin.service;

import com.nitin.entity.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

@Service
public class RagService {

    private static final Logger logger = LoggerFactory.getLogger(RagService.class);
    private static final int DEFAULT_SIMILARITY_LIMIT = 3;
    private static final int MAX_CONTEXT_LENGTH = 4000; // Adjust based on your LLM's context window
    //Only 1 Llama operation at a time
    private final Semaphore llamaSemaphore = new Semaphore(1);
    @Autowired
    private DocumentService documentService;

    @Autowired
    private LlamaService llamaService;

    public String query(String question) {
        return query(question, DEFAULT_SIMILARITY_LIMIT);
    }

    public String query(String question, int similarityLimit) {
        logger.info("Processing RAG query (cache miss): {}", question.substring(0, Math.min(50, question.length())));
        logger.info("Processing query: {} (limit {})", question, similarityLimit);

        if (question.trim().isEmpty()) {
            logger.warn("Empty or null question provided");
            return "Please provide a valid question.";
        }

        long startTime = System.currentTimeMillis();

        try {
            llamaSemaphore.acquire();
            // Retrieve relevant documents
            List<Document> relevantDocs = documentService.findSimilarDocuments(question, similarityLimit);

            if (relevantDocs.isEmpty()) {
                logger.warn("No relevant documents found for query {}", question);
                //return "I couldn't find relevant information in the document to answer your question.";
            } else {
                logger.info("Found {} relevant documents for query : {}", relevantDocs.size(), question);
            }
            // Build context from retrieved documents
            String context = buildOptimizedContext(relevantDocs);

            logger.info("Built context with {} characters", context.length());
            if (logger.isDebugEnabled()) {
                logger.debug("Context preview: {}", context.substring(0, Math.min(200, context.length())));
            }

            // Generate response using llama.cpp
            String response = llamaService.generateResponse(question.trim(), context);
            long processingTime = System.currentTimeMillis() - startTime;

            logger.info("RAG query processed in {}ms", processingTime);
            return response;
        } catch (Exception e) {
            logger.error("Error processing query: {}", question, e);
            return "I encountered an error while processing your question. Please try again.";
        } finally {
            llamaSemaphore.release();
        }
    }

    /**
     * Build optimized context by managing content length and removing duplicates
     */
    private String buildOptimizedContext(List<Document> documents) {
        StringBuilder contextBuilder = new StringBuilder();
        int currentLength = 0;

        // Remove duplicate content and build context within limits
        List<String> uniqueContents = documents.stream()
                .map(Document::getContent)
                .distinct() // Remove exact duplicates
                .toList();

        for (String content : uniqueContents) {
            // Check if adding this content would exceed our limit
            if (currentLength + content.length() + 2 > MAX_CONTEXT_LENGTH) { // +2 for "\n\n"
                logger.debug("Context length limit reached. Truncating at {} characters", currentLength);
                break;
            }

            if (contextBuilder.isEmpty()) {
                contextBuilder.append("\n\n");
                currentLength += 2;
            }

            contextBuilder.append(content);
            currentLength += content.length();
        }

        return contextBuilder.toString();
    }
}
