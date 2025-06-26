package com.example.videoprocessor.entity;

import java.time.LocalDateTime;

import com.example.videoprocessor.entity.enums.VideoStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;


@Setter
@Getter
@Entity
@Table(name = "videos")
public class VideoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String filename;
    
    @Column(name = "original_path", nullable = false)
    private String originalPath;
    
    @Column(name = "processed_path")
    private String processedPath;
    
    @Column(name = "thumbnail_path")
    private String thumbnailPath;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VideoStatus status;
    
    @Column(name = "file_size")
    private Long fileSize;
    
    @Column(name = "duration")
    private Long duration; // seconds
    
    @Column(name = "resolution")
    private String resolution;
    
    @Column(name = "codec")
    private String codec;
    
    @Column(name = "frame_rate")
    private Double frameRate;
    
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "processing_start_time")
    private LocalDateTime processingStartTime;
    
    @Column(name = "processing_end_time")
    private LocalDateTime processingEndTime;
    
    @Column(name = "error_message")
    private String errorMessage;
    
    // Progress tracking for async processing
    @Column(name = "progress_percentage")
    private Integer progressPercentage = 0;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
