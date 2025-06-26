package com.example.videoprocessor.dto;

import com.fasterxml.jackson.annotation.JsonFormat;


import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class VideoProcessingResponseDto {
    private Long videoId;
    private String status;
    private String message;
    private String processingType;
    private Long processingTimeMs;
    private Integer progressPercentage;
    private String thumbnailPath;
    private String processedPath;
    private VideoMetadataDto metadata;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    
    private Long estimatedTimeRemaining; 
    
    public VideoProcessingResponseDto() {}
    
    public VideoProcessingResponseDto(Long videoId, String status, String message, String processingType) {
        this.videoId = videoId;
        this.status = status;
        this.message = message;
        this.processingType = processingType;
    }
}