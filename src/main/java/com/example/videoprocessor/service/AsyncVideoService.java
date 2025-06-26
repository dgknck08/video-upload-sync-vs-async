package com.example.videoprocessor.service;

import com.example.videoprocessor.dto.VideoUploadRequestDto;
import com.example.videoprocessor.dto.VideoProcessingResponseDto;
import com.example.videoprocesor.config.RabbitMQConfig;
import com.example.videoprocessor.dto.VideoMetadataDto;
import com.example.videoprocessor.dto.VideoProcessingMessageDto;
import com.example.videoprocessor.entity.VideoEntity;
import com.example.videoprocessor.entity.enums.VideoStatus;
import com.example.videoprocessor.repository.VideoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Transactional
public class AsyncVideoService {

    @Autowired
    private VideoRepository videoRepository;
    
    @Autowired
    private RabbitTemplate rabbitTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    // İşlem durumlarını takip etmek için
    private final ConcurrentHashMap<Long, String> processingStatus = new ConcurrentHashMap<>();

    /**
     * ASYNCHRONOUS VIDEO PROCESSING WITH RABBITMQ
     * 
     * Avantajları:
     * - RabbitMQ ile güvenilir mesaj kuyruğu
     * - Dead Letter Queue ile hata yönetimi
     * - Message TTL ile zaman aşımı korunması
     * - Priority queue desteği
     * - Cluster ve high availability
     * - Durable queues ile mesaj kalıcılığı
     */
    public VideoProcessingResponseDto processVideoAsync(VideoUploadRequestDto requestDto, Integer priority) throws Exception {
        
        try {
            // 1. Dosyayı kaydet ve veritabanına ekle
            VideoEntity video = saveVideoFile(requestDto);
            video.setStatus(VideoStatus.UPLOADED);
            video.setProgressPercentage(0);
            videoRepository.save(video);
            
            // 2. ASENKRON İŞLEM BAŞLAT - RabbitMQ'ya gönder
            VideoProcessingMessageDto message = new VideoProcessingMessageDto(
                video.getId(),
                video.getOriginalPath(),
                video.getFilename(),
                "FULL_PROCESSING"
            );
            message.setPriority(priority);
            
            // RabbitMQ'ya mesaj gönder
            sendVideoProcessingMessage(message, priority);
            
            // Processing status'u takip et
            processingStatus.put(video.getId(), "QUEUED");
            
            VideoProcessingResponseDto response = convertToResponseDto(video);
            response.setMessage("Video uploaded successfully. Processing started asynchronously.");
            response.setStatus("PROCESSING");
            
            return response;
            
        } catch (Exception e) {
            throw new RuntimeException("Async video processing failed: " + e.getMessage());
        }
    }

    /**
     * RabbitMQ'ya priority ile mesaj gönderme
     */
    private void sendVideoProcessingMessage(VideoProcessingMessageDto message, Integer priority) {
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.VIDEO_PROCESSING_EXCHANGE,
            RabbitMQConfig.VIDEO_PROCESSING_ROUTING_KEY,
            message,
            messagePostProcessor -> {
                MessageProperties properties = messagePostProcessor.getMessageProperties();
                if (priority != null) {
                    properties.setPriority(priority);
                }
                properties.setExpiration("3600000"); // 1 saat expiration
                return messagePostProcessor;
            }
        );
    }

    /**
     * Bulk video processing - Birden fazla video
     */
    public List<VideoProcessingResponseDto> processMultipleVideosAsync(List<VideoUploadRequestDto> requestDtos, Integer priority) throws Exception {
        List<VideoProcessingResponseDto> responses = new java.util.ArrayList<>();
        
        for (VideoUploadRequestDto requestDto : requestDtos) {
            try {
                VideoProcessingResponseDto response = processVideoAsync(requestDto, priority);
                responses.add(response);
            } catch (Exception e) {
                // Hatalı olanları da response'a ekle
                VideoProcessingResponseDto errorResponse = new VideoProcessingResponseDto();
                errorResponse.setStatus("FAILED");
                errorResponse.setMessage("Failed to process: " + e.getMessage());
                responses.add(errorResponse);
            }
        }
        
        return responses;
    }

    /**
     * Priority ile video processing
     */
    public VideoProcessingResponseDto processVideoWithPriority(VideoUploadRequestDto requestDto, String priorityLevel) throws Exception {
        Integer priority = switch (priorityLevel.toUpperCase()) {
            case "HIGH" -> 10;
            case "MEDIUM" -> 5;
            case "LOW" -> 1;
            default -> 5;
        };
        
        return processVideoAsync(requestDto, priority);
    }

    public VideoProcessingResponseDto getVideoStatus(Long id) {
        Optional<VideoEntity> videoOpt = videoRepository.findById(id);
        if (videoOpt.isEmpty()) {
            VideoProcessingResponseDto response = new VideoProcessingResponseDto();
            response.setStatus("NOT_FOUND");
            response.setMessage("Video not found");
            return response;
        }
        
        VideoProcessingResponseDto response = convertToResponseDto(videoOpt.get());
        
        // Processing status ekle
        String currentProcessingStatus = processingStatus.get(id);
        if (currentProcessingStatus != null) {
            response.setMessage("Current processing status: " + currentProcessingStatus);
        }
        
        return response;
    }

    public VideoProcessingResponseDto getVideoProgress(Long id) {
        VideoProcessingResponseDto response = getVideoStatus(id);
        
        if (response.getProgressPercentage() != null && response.getProgressPercentage() > 0) {
            // Estimated time remaining hesaplama
            long estimatedTimeRemaining = calculateEstimatedTimeRemaining(id, response.getProgressPercentage());
            response.setEstimatedTimeRemaining(estimatedTimeRemaining);
        }
        
        return response;
    }

    public List<VideoProcessingResponseDto> getProcessingQueue() {
        // Şu anda işlenen videoları getir
        return videoRepository.findByStatusIn(List.of(
                VideoStatus.PROCESSING,
                VideoStatus.THUMBNAIL_CREATING,
                VideoStatus.TRANSCODING,
                VideoStatus.METADATA_EXTRACTING
            )).stream()
            .map(this::convertToResponseDto)
            .collect(Collectors.toList());
    }

    public VideoProcessingResponseDto cancelProcessing(Long id) {
        Optional<VideoEntity> videoOpt = videoRepository.findById(id);
        if (videoOpt.isEmpty()) {
            VideoProcessingResponseDto response = new VideoProcessingResponseDto();
            response.setStatus("NOT_FOUND");
            response.setMessage("Video not found");
            return response;
        }
        
        VideoEntity video = videoOpt.get();
        if (video.getStatus() == VideoStatus.COMPLETED || video.getStatus() == VideoStatus.FAILED) {
            VideoProcessingResponseDto response = convertToResponseDto(video);
            response.setMessage("Cannot cancel completed or failed processing");
            return response;
        }
        
        // Processing'i iptal et
        video.setStatus(VideoStatus.CANCELLED);
        video.setProgressPercentage(0);
        video.setProcessingEndTime(LocalDateTime.now());
        videoRepository.save(video);
        
        // Processing status'u temizle
        processingStatus.remove(id);
        
        VideoProcessingResponseDto response = convertToResponseDto(video);
        response.setMessage("Processing cancelled successfully");
        
        return response;
    }

    public List<VideoProcessingResponseDto> getAllVideos() {
        return videoRepository.findAll().stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    public boolean deleteVideo(Long id) {
        if (videoRepository.existsById(id)) {
            videoRepository.deleteById(id);
            processingStatus.remove(id);
            return true;
        }
        return false;
    }

    private VideoEntity saveVideoFile(VideoUploadRequestDto requestDto) throws IOException {
        String uploadDir = "uploads/";
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        
        String filename = System.currentTimeMillis() + "_" + requestDto.getFile().getOriginalFilename();
        String filePath = uploadDir + filename;
        
        // Dosyayı kaydet
        requestDto.getFile().transferTo(Paths.get(filePath).toFile());
        
        // Veritabanına kaydet
        VideoEntity video = new VideoEntity();
        video.setFilename(filename);
        video.setOriginalPath(filePath);
        video.setStatus(VideoStatus.UPLOADED);
        video.setFileSize(requestDto.getFile().getSize());
        
        return videoRepository.save(video);
    }

    private long calculateEstimatedTimeRemaining(Long videoId, Integer progressPercentage) {
        // Basit bir hesaplama 
        if (progressPercentage >= 100) return 0;
        
        //Ortalama işlem süresine göre tahmini süre
        long averageProcessingTime = 300000; // 5 dakika 
        long remainingPercentage = 100 - progressPercentage;
        
        return (averageProcessingTime * remainingPercentage) / 100;
    }

    private VideoProcessingResponseDto convertToResponseDto(VideoEntity video) {
        VideoProcessingResponseDto dto = new VideoProcessingResponseDto();
        dto.setVideoId(video.getId());
        dto.setStatus(video.getStatus().name());
        dto.setProgressPercentage(video.getProgressPercentage());
        dto.setThumbnailPath(video.getThumbnailPath());
        dto.setProcessedPath(video.getProcessedPath());
        dto.setCreatedAt(video.getCreatedAt());
        dto.setUpdatedAt(video.getUpdatedAt());
        
        if (video.getMetadata() != null) {
            try {
                dto.setMetadata(objectMapper.readValue(video.getMetadata(), VideoMetadataDto.class));
            } catch (Exception e) {
         
            }
        }
        
        return dto;
    }
}