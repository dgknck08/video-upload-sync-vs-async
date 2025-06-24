package com.example.videoproccesor.dto;

public class VideoProcessingMessage {
    private Long videoId;
    private String filePath;
    private String filename;
    
    public VideoProcessingMessage() {}
    
    public VideoProcessingMessage(Long videoId, String filePath, String filename) {
        this.videoId = videoId;
        this.filePath = filePath;
        this.filename = filename;
    }
    
    // getters and setters
    public Long getVideoId() { return videoId; }
    public void setVideoId(Long videoId) { this.videoId = videoId; }
    
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
}
