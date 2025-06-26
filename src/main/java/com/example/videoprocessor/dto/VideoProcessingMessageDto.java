package com.example.videoprocessor.dto;

import java.io.Serializable;



import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
public class VideoProcessingMessageDto implements Serializable {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Long videoId;
    private String originalPath;
    private String filename;
    private String processingType; // THUMBNAIL, TRANSCODING, METADATA
    private Integer priority; 
    
    public VideoProcessingMessageDto() {}
    
    public VideoProcessingMessageDto(Long videoId, String originalPath, String filename, String processingType) {
        this.videoId = videoId;
        this.originalPath = originalPath;
        this.filename = filename;
        this.processingType = processingType;
        this.priority = 5; 
    }
    
    
}

