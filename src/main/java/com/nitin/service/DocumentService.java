package com.nitin.service;

import com.nitin.entity.Document;
import com.nitin.repository.DocumentRepository;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.data.document.BlankDocumentException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

@Service
public class DocumentService {
    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);

    @Autowired
    private DocumentRepository documentRepository;

    private final EmbeddingModel embeddingModel;
    private final ApacheTikaDocumentParser documentParser;
    private final DocumentSplitter documentSplitter;

    // Constants for batch processing
    private static final int BATCH_SIZE = 1000;
    private static final int SEGMENT_BATCH_SIZE = 50;

    // Cache for document count - Invalidated when documents are added/removed
    private final AtomicLong cachedDocumentCount = new AtomicLong(-1);
    private volatile long lastCountUpdate = 0;
    private static final long CACHE_VALIDITY_MS = 600000;

    public DocumentService() {
        this.embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        this.documentParser = new ApacheTikaDocumentParser();
        this.documentSplitter = DocumentSplitters.recursive(500, 50);
        logger.info("DocumentService initialized with embedding model: {}", embeddingModel.getClass().getSimpleName());
    }

    @Transactional
    @CacheEvict(value = "documentCount", allEntries = true)
    public void processUploadedFiles(MultipartFile[] files) throws IOException {
        //Invalidate Cache before processing
        invalidateDocumentCountCache();

        for (MultipartFile file : files) {
            if(file.isEmpty()) {
                logger.warn("Skipping empty file: {}", file.getOriginalFilename());
                continue;
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.trim().isEmpty()) {
                logger.warn("Skipping file with null or empty filename");
                continue;
            }

            // Validate file extension
            if (!isProcessableFile(originalFilename)) {
                logger.warn("Skipping unsupported file type: {}", originalFilename);
                continue;
            }

            // Create temp file with proper extension
            String extension = "";
            int lastDotIndex = originalFilename.lastIndexOf('.');
            if (lastDotIndex > 0) {
                extension = originalFilename.substring(lastDotIndex);
            }

            Path tempFile = Files.createTempFile("uploaded_", extension);
            try {
                // Copy uploaded file content to temp file
                Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
                logger.debug("Created temp file: {} for uploaded file: {}", tempFile, originalFilename);

                processFile(tempFile);
            } catch (Exception e) {
                logger.error("Failed to process uploaded file: {}", originalFilename, e);
                // Don't rethrow here to continue processing other files
            } finally {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    logger.warn("Failed to delete temp file: {}", tempFile, e);
                }
            }
        }
    }

    @Transactional
    @CacheEvict(value = "documentCount", allEntries = true)
    public void processStaticFiles(String directoryPath) throws IOException {
        logger.info("Starting to process files in directory: {}", directoryPath);

        //Invalidate Cache before processing
        invalidateDocumentCountCache();

        Path dir = Paths.get(directoryPath);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            throw new IOException("Directory does not exist or is not a directory: " + directoryPath);
        }

        AtomicInteger totalFiles = new AtomicInteger(0);
        AtomicInteger processedFiles = new AtomicInteger(0);

        try (Stream<Path> paths = Files.walk(dir)) {
            List<Path> filesToProcess = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> isProcessableFile(path.toString()))
                    .toList();

            totalFiles.set(filesToProcess.size());
            logger.info("Found {} processable files", totalFiles.get());

            if (filesToProcess.isEmpty()) {
                logger.warn("No processable files found in directory: {}", directoryPath);
                return;
            }

            filesToProcess.forEach(path -> {
                try {
                    processFile(path);
                    int processed = processedFiles.incrementAndGet();
                    logger.info("Progress: {}/{} files processed", processed, totalFiles.get());
                } catch (Exception e) {
                    logger.error("Failed to process file: {}", path, e);
                }
            });
        }

        logger.info("Completed processing {} files from directory: {}", processedFiles.get(), directoryPath);
    }

    private boolean isProcessableFile(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return false;
        }
        String lowerCase = fileName.toLowerCase();
        return lowerCase.endsWith(".txt") ||
                lowerCase.endsWith(".pdf") ||
                lowerCase.endsWith(".docx") ||
                lowerCase.endsWith(".doc") ||
                lowerCase.endsWith(".md") ||
                lowerCase.endsWith(".rtf");
    }

    @Transactional
    private void processFile(Path filePath) {
        logger.debug("Processing file: {}", filePath);

        try {
            // Check if file exists and is readable
            if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
                logger.error("File does not exist or is not readable: {}", filePath);
                return;
            }

            // Check file size
            long fileSize = Files.size(filePath);
            if (fileSize == 0) {
                logger.warn("File is empty: {}", filePath);
                return;
            }
            logger.debug("File size: {} bytes", fileSize);

            // Check if file already exists in database - OPTIMIZED
            String fileName = filePath.getFileName().toString();
            if (documentRepository.existsByFileName(fileName)) {
                logger.info("File already processed, skipping: {}", fileName);
                return;
            }

            try (InputStream inputStream = Files.newInputStream(filePath)) {
                dev.langchain4j.data.document.Document document;

                try {
                    document = documentParser.parse(inputStream);
                } catch (BlankDocumentException e) {
                    logger.warn("Document appears to be blank or contains no extractable text: {} - {}",
                            fileName, e.getMessage());
                    return;
                } catch (Exception e) {
                    logger.error("Failed to parse document: {} - {}", fileName, e.getMessage());
                    return;
                }

                // Validate document content
                if (document == null || document.text() == null || document.text().trim().isEmpty()) {
                    logger.warn("Document is null, empty or contains only whitespace: {}", fileName);
                    return;
                }

                String documentText = document.text().trim();
                logger.debug("Extracted text length: {} characters from file: {}", documentText.length(), fileName);

                // Split document into segments
                List<TextSegment> segments;
                try {
                    segments = documentSplitter.split(document);
                } catch (Exception e) {
                    logger.error("Failed to split document: {} - {}", fileName, e.getMessage());
                    return;
                }

                if (segments == null || segments.isEmpty()) {
                    logger.warn("Document splitting resulted in no segments: {}", fileName);
                    return;
                }

                logger.debug("Split document {} into {} segments", fileName, segments.size());

                // OPTIMIZED: Batch process segments
                List<Document> documentsToSave = new ArrayList<>();
                int processedSegments = 0;

                for (int i = 0; i < segments.size(); i++) {
                    TextSegment segment = segments.get(i);

                    if (segment == null || segment.text() == null || segment.text().trim().isEmpty()) {
                        logger.debug("Skipping empty segment {} in file {}", i, fileName);
                        continue;
                    }

                    try {
                        String segmentText = segment.text().trim();

                        // Generate embedding
                        Embedding embedding = embeddingModel.embed(segmentText).content();

                        if (embedding == null || embedding.vector() == null || embedding.vector().length == 0) {
                            logger.warn("Failed to generate embedding for segment {} in file {}", i, fileName);
                            continue;
                        }

                        // Create document entity
                        Document docEntity = new Document(
                                segmentText,
                                fileName,
                                serializeEmbedding(embedding),
                                i
                        );

                        documentsToSave.add(docEntity);
                        processedSegments++;

                        // Batch save every 50 documents
                        if (documentsToSave.size() >= SEGMENT_BATCH_SIZE) {
                            documentRepository.saveAll(documentsToSave);
                            documentsToSave.clear();
                            logger.debug("Batch saved {} segments for file: {}", SEGMENT_BATCH_SIZE, fileName);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to process segment {} in file {}: {}", i, fileName, e.getMessage());
                        // Continue processing other segments
                    }
                }

                // Save remaining documents
                if (!documentsToSave.isEmpty()) {
                    documentRepository.saveAll(documentsToSave);
                }

                if (processedSegments == 0) {
                    logger.warn("No segments were successfully processed for file: {}", fileName);
                } else {
                    logger.info("Successfully processed file: {} ({}/{} segments processed)",
                            fileName, processedSegments, segments.size());
                }
            }
        } catch (Exception e) {
            logger.error("Error processing file: {} - {}", filePath, e.getMessage(), e);
            // Don't rethrow to allow processing of other files to continue
        }
    }

    private byte[] serializeEmbedding(Embedding embedding) {
        if (embedding == null || embedding.vector() == null) {
            throw new IllegalArgumentException("Embedding or vector cannot be null");
        }

        float[] vector = embedding.vector();
        if (vector.length == 0) {
            throw new IllegalArgumentException("Embedding vector cannot be empty");
        }

        ByteBuffer buffer = ByteBuffer.allocate(vector.length * 4);

        for (float value : vector) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    private float[] deserializeEmbedding(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Embedding bytes cannot be null or empty");
        }

        if (bytes.length % 4 != 0) {
            throw new IllegalArgumentException("Invalid embedding bytes length");
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        float[] vector = new float[bytes.length / 4];

        for (int i = 0; i < vector.length; i++) {
            vector[i] = buffer.getFloat();
        }

        return vector;
    }

    // Process documents in batches instead of loading all at once
    public List<Document> findSimilarDocuments(String query, int limit) {
        logger.debug("Finding similar documents for query: {} (limit: {})", query, limit);

        if (query == null || query.trim().isEmpty()) {
            logger.warn("Empty query provided");
            return List.of();
        }

        try {
            Embedding queryEmbedding = embeddingModel.embed(query.trim()).content();

            if (queryEmbedding == null || queryEmbedding.vector() == null) {
                logger.error("Failed to generate embedding for query: {}", query);
                return List.of();
            }

            // Check if we have any documents first
            long totalDocs = documentRepository.count();
            if (totalDocs == 0) {
                logger.warn("No documents found in database");
                return List.of();
            }

            logger.debug("Processing {} documents in batches", totalDocs);

            List<ScoredDocument> scoredDocuments = new ArrayList<>();
            int page = 0;

            // Process documents in batches to avoid loading all into memory
            while (true) {
                Pageable pageable = PageRequest.of(page, BATCH_SIZE);
                List<Document> batch = documentRepository.findDocumentsWithEmbeddings(pageable);

                if (batch.isEmpty()) {
                    break;
                }

                for (Document doc : batch) {
                    if (doc.getEmbedding() != null && doc.getEmbedding().length > 0) {
                        try {
                            double similarity = calculateSimilarity(queryEmbedding, deserializeEmbedding(doc.getEmbedding()));
                            scoredDocuments.add(new ScoredDocument(doc, similarity));
                        } catch (Exception e) {
                            logger.error("Error calculating similarity for document {}", doc.getId(), e);
                        }
                    }
                }

                page++;
                logger.debug("Processed batch {} ({} documents)", page, batch.size());
            }

            // Sort by similarity and return top results
            List<Document> similarDocs = scoredDocuments.stream()
                    .sorted((d1, d2) -> Double.compare(d2.score, d1.score)) // Descending order
                    .limit(limit)
                    .map(sd -> sd.document)
                    .toList();

            logger.debug("Found {} similar documents", similarDocs.size());
            return similarDocs;
        } catch (Exception e) {
            logger.error("Error finding similar documents for query: {}", query, e);
            return List.of();
        }
    }

    // Helper class for scoring documents
    private static class ScoredDocument {
        final Document document;
        final double score;

        ScoredDocument(Document document, double score) {
            this.document = document;
            this.score = score;
        }
    }

    private double calculateSimilarity(Embedding e1, float[] e2Vector) {
        if (e1 == null || e1.vector() == null || e2Vector == null) {
            throw new IllegalArgumentException("Embeddings cannot be null");
        }

        float[] e1Vector = e1.vector();

        if (e1Vector.length != e2Vector.length) {
            throw new IllegalArgumentException("Vector dimensions don't match: " +
                    e1Vector.length + " vs " + e2Vector.length);
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < e1Vector.length; i++) {
            dotProduct += e1Vector[i] * e2Vector[i];
            normA += Math.pow(e1Vector[i], 2);
            normB += Math.pow(e2Vector[i], 2);
        }

        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return denominator == 0 ? 0 : dotProduct / denominator;
    }

    // Utility methods
    public long getDocumentCount() {
        //return documentRepository.count();
        long currentTime = System.currentTimeMillis();

        // Check if cache is still valid
        if(cachedDocumentCount.get() != -1 && (currentTime - lastCountUpdate) < CACHE_VALIDITY_MS) {
            return cachedDocumentCount.get();
        }

        // Cache is invalid or expired
        long count = documentRepository.countDistinctByFileName();
        cachedDocumentCount.set(count);
        lastCountUpdate = currentTime;

        logger.debug("Document count cache updated: {}", count);
        return count;
    }

    // Method to invalidate the cache when documents are added/removed
    private void invalidateDocumentCountCache(){
        cachedDocumentCount.set(-1);
        lastCountUpdate = 0;
        logger.debug("Document count cache invalidated");
    }

    @CacheEvict(value = "documentCount", allEntries = true)
    public void clearIndex() {
        // Invalidate cache before clearing
        invalidateDocumentCountCache();

        // Clear database
        documentRepository.deleteAll();
        // Clear vector index if you're using one
        // vectorService.clearIndex();
        logger.info("Index cleared and cache invalidated");
    }
}