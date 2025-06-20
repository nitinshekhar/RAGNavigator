package com.nitin.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents", indexes = {@Index(name = "idx_documents_created", columnList = "created_at")})
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 2000)
    private String content;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "chunk_index")
    private Integer chunkIndex;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "embedding", columnDefinition = "LONGBLOB")
    private byte[] embedding;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Document() {
    }

    public Document(String content, String fileName, byte[] embedding) {
        this.content = content;
        this.fileName = fileName;
        this.embedding = embedding;
        this.chunkIndex = 0;
    }

    public Document(String content, String fileName, byte[] embedding, Integer chunkIndex) {
        this.content = content;
        this.fileName = fileName;
        this.embedding = embedding;
        this.chunkIndex = chunkIndex;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }

    public byte[] getEmbedding() { return embedding;}
    public void setEmbedding(byte[] embedding) { this.embedding = embedding; }
}
