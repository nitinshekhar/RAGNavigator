# Description
RAG application using SpringBoot that works with static files and integrates with local llama.cpp

Document Ingestion & Preprocessing

✅ File upload handling (processUploadedFiles)
✅ Multiple file format support (PDF, DOCX, TXT, MD, RTF)
✅ Apache Tika for document parsing
✅ Text extraction and validation

2. Document Chunking/Splitting

✅ DocumentSplitters.recursive(500, 50) - proper chunking strategy
✅ Overlap handling (50 characters)
✅ Segment indexing for traceability

3. Embedding Generation

✅ AllMiniLmL6V2EmbeddingModel for vector embeddings
✅ Proper embedding serialization/deserialization
✅ Vector storage in database

4. Vector Storage & Retrieval

✅ Database storage with JPA/Hibernate
✅ Cosine similarity calculation
✅ Batch processing for efficiency
✅ Similarity search with configurable limits

5. Query Processing

✅ Query embedding generation
✅ Semantic similarity search
✅ Context building from retrieved documents

6. Response Generation

✅ Integration with local LLM (llama.cpp)
✅ Context-aware prompt construction
✅ Proper prompt engineering

# RAG Flow
User Query → Query Embedding → Similarity Search → Context Retrieval →
Prompt Construction → LLM Generation → Response

# Setup
- Update the configuration path in the code and application.properties
- Install llama.cpp if not already done
- Download a compatible model

# Working
- Document Processing: The application reads static files (PDF, TXT, DOCX, MD) from a specified directory,
  splits them into chunks, generates embeddings, and stores them in an H2 database.
- Retrieval: When you ask a question, it finds the most similar document chunks using cosine similarity.
- Generation: It sends the question and relevant context to llama.cpp via command line execution.

# Architecture
This diagram illustrates the workflow of a document processing and user query system. It begins with document ingestion and user queries, which are then processed through chunking and embedding models. The workflow integrates an H2 database for embedding storage and facilitates similarity search for chunk retrieval. Finally, it assembles context for LLM processing.
![img.png](img.png)

# API Endpoints
- POST /api/rag/index-file - Index documents from a directory 
- POST /api/rag/index-directory - Index documents from a directory
- POST /api/rag/query - Ask questions and get RAG responses
- POST /api/rag/clear - Clear the indexes

# URL
Running llama Server
  ./build/bin/llama-server -m llama-2-7b-chat.Q4_K_M.gguf --port 8081 --ctx-size 2048 --n-gpu-layers 1 -t 8 -b 1024 --mlock --no-mmap
Accessing Browser URL
  http://localhost:8080/

# Curl Command for Indexing and Query the RAG system
curl -X POST http://localhost:8080/api/rag/index -H "Content-Type: application/json" -d '{"directoryPath": "/path/to/your/documents"}'
curl -X POST http://localhost:8080/api/rag/query -H "Content-Type: application/json" -d '{"question": "What is the main topic discussed in the documents?"}'

# Debugging Steps
- Check H2 Console - https://localhost:8080/h2-console
- Enable SQL logging - Add spring.jpa.show-swl=true
