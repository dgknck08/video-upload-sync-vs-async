package com.example.videoprocessor.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.example.videoprocessor.dto.VideoProcessingResponseDto;
import com.example.videoprocessor.dto.VideoUploadRequestDto;
import com.example.videoprocessor.service.AsyncVideoService;

import java.util.List;

@RestController
@RequestMapping("/api/async/videos")
@CrossOrigin(origins = "*")
public class AsyncVideoUploadController {

    @Autowired
    private AsyncVideoService asyncVideoService;

    /**
     * ASYNCHRONOUS VIDEO UPLOAD
     * - Sadece upload yapılır, işlemler arka planda devam eder
     * - Client hemen response alır (202 Accepted)
     * - Büyük dosyalar için ideal
     * - Sunucu kaynakları verimli kullanılır
     * - Progress tracking mümkün
     */
    @PostMapping("/upload")
    public ResponseEntity<VideoProcessingResponseDto> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "priority", defaultValue = "5") Integer priority) {
        
        try {
            long startTime = System.currentTimeMillis();
            
            VideoUploadRequestDto requestDto = new VideoUploadRequestDto();
            requestDto.setFile(file);
            requestDto.setTitle(title);
            requestDto.setDescription(description);
            requestDto.setCategory(category);
            
            // ASENKRONİŞLEM - Mantık => Hemen response dön işlemler arka planda devam etsin.
            VideoProcessingResponseDto response = asyncVideoService.processVideoAsync(requestDto, priority);
            
            long processingTime = System.currentTimeMillis() - startTime;
            response.setProcessingTimeMs(processingTime);
            response.setProcessingType("ASYNCHRONOUS");
            
            // 202 Accepted - İşlem başlatıldı
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            
        } catch (Exception e) {
            VideoProcessingResponseDto errorResponse = new VideoProcessingResponseDto();
            errorResponse.setStatus("FAILED");
            errorResponse.setMessage("Asynchronous processing failed: " + e.getMessage());
            errorResponse.setProcessingType("ASYNCHRONOUS");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<VideoProcessingResponseDto> getVideoStatus(@PathVariable Long id) {
        VideoProcessingResponseDto response = asyncVideoService.getVideoStatus(id);
        if ("NOT_FOUND".equals(response.getStatus())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/progress")
    public ResponseEntity<VideoProcessingResponseDto> getVideoProgress(@PathVariable Long id) {
        VideoProcessingResponseDto response = asyncVideoService.getVideoProgress(id);
        if ("NOT_FOUND".equals(response.getStatus())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/queue/status")
    public ResponseEntity<List<VideoProcessingResponseDto>> getQueueStatus() {
        List<VideoProcessingResponseDto> queueStatus = asyncVideoService.getProcessingQueue();
        return ResponseEntity.ok(queueStatus);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<VideoProcessingResponseDto> cancelProcessing(@PathVariable Long id) {
        VideoProcessingResponseDto response = asyncVideoService.cancelProcessing(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/all")
    public ResponseEntity<List<VideoProcessingResponseDto>> getAllVideos() {
        List<VideoProcessingResponseDto> videos = asyncVideoService.getAllVideos();
        return ResponseEntity.ok(videos);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVideo(@PathVariable Long id) {
        boolean deleted = asyncVideoService.deleteVideo(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}