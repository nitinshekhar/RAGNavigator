package com.nitin.repository;

import com.nitin.entity.Document;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    // OPTIMIZED: Use exists instead of findBy to avoid loading data
    boolean existsByFileName(String fileName);

    // OPTIMIZED: Custom query to fetch only documents with embeddings in batches
    @Query("SELECT d FROM Document d WHERE d.embedding IS NOT NULL")
    List<Document> findDocumentsWithEmbeddings(Pageable pageable);

    @Query("SELECT COUNT(DISTINCT d.fileName) FROM Document d")
    long countDistinctByFileName();
}