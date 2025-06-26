package com.example.videoprocessor.controller;


import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.example.videoprocessor.dto.VideoProcessingResponseDto;
import com.example.videoprocessor.dto.VideoUploadRequestDto;
import com.example.videoprocessor.service.SyncVideoService;

import java.util.List;

@RestController
@RequestMapping("/api/sync/videos")
@CrossOrigin(origins = "*")
public class SyncVideoUploadController {

    @Autowired
    private SyncVideoService syncVideoService;

    /**
     * SYNCHRONOUS VIDEO UPLOAD
     * - Tüm işlemler sırayla yapılır
     * - Client işlem bitene kadar bekler
     * - Büyük dosyalar için timeout riski var
     * - Sunucu kaynakları uzun süre bloke olur
     */
    
    
    
    @PostMapping("/upload")
    public ResponseEntity<VideoProcessingResponseDto> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "category", required = false) String category) {
        
        try {
            long startTime = System.currentTimeMillis();
            
            VideoUploadRequestDto requestDto = new VideoUploadRequestDto();
            requestDto.setFile(file);
            requestDto.setTitle(title);
            requestDto.setDescription(description);
            requestDto.setCategory(category);
            
            // SENKRON İŞLEM - Client bekler tüm işlemler tamamlanırsa bekleme sona erer.
            VideoProcessingResponseDto response = syncVideoService.processVideoSync(requestDto);
            
            long processingTime = System.currentTimeMillis() - startTime;
            response.setProcessingTimeMs(processingTime);
            response.setProcessingType("SYNCHRONOUS");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            VideoProcessingResponseDto errorResponse = new VideoProcessingResponseDto();
            errorResponse.setStatus("FAILED");
            errorResponse.setMessage("Synchronous processing failed: " + e.getMessage());
            errorResponse.setProcessingType("SYNCHRONOUS");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<VideoProcessingResponseDto> getVideoStatus(@PathVariable Long id) {
        VideoProcessingResponseDto response = syncVideoService.getVideoStatus(id);
        if ("NOT_FOUND".equals(response.getStatus())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/all")
    public ResponseEntity<List<VideoProcessingResponseDto>> getAllVideos() {
        List<VideoProcessingResponseDto> videos = syncVideoService.getAllVideos();
        return ResponseEntity.ok(videos);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVideo(@PathVariable Long id) {
        boolean deleted = syncVideoService.deleteVideo(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}