package com.example.videoprocessor.service;

import com.example.videoprocessor.dto.VideoUploadRequestDto;
import com.example.videoprocessor.dto.VideoProcessingResponseDto;
import com.example.videoprocessor.config.RabbitMQConfig;
import com.example.videoprocessor.dto.VideoMetadataDto;
import com.example.videoprocessor.dto.VideoProcessingMessageDto;
import com.example.videoprocessor.entity.VideoEntity;
import com.example.videoprocessor.entity.enums.VideoStatus;
import com.example.videoprocessor.repository.VideoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
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

    private static final Logger logger = LoggerFactory.getLogger(AsyncVideoService.class);

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private final ConcurrentHashMap<Long, String> processingStatus = new ConcurrentHashMap<>();

    public VideoProcessingResponseDto processVideoAsync(VideoUploadRequestDto requestDto, Integer priority) throws Exception {
        try {
            // 1. Video dosyasını kaydet
            VideoEntity video = saveVideoFile(requestDto);
            video.setStatus(VideoStatus.UPLOADED);
            video.setProgressPercentage(0);
            video = videoRepository.save(video);
            
            logger.info("Video saved with ID: {}, path: {}", video.getId(), video.getOriginalPath());

            // 2. RabbitMQ mesajını oluştur
            VideoProcessingMessageDto message = new VideoProcessingMessageDto(
                    video.getId(),
                    video.getOriginalPath(),
                    video.getFilename(),
                    "FULL_PROCESSING"
            );
            message.setPriority(priority);

            // 3. Mesajı kuyruğa gönder
            boolean messageSent = sendVideoProcessingMessage(message, priority);
            
            if (!messageSent) {
                video.setStatus(VideoStatus.FAILED);
                video.setErrorMessage("Failed to send message to processing queue");
                videoRepository.save(video);
                throw new RuntimeException("Failed to send video processing message");
            }

            // 4. Status'u güncelle
            processingStatus.put(video.getId(), "QUEUED");
            
            logger.info("Video processing message sent successfully for video ID: {}", video.getId());

            // 5. Response oluştur
            VideoProcessingResponseDto response = convertToResponseDto(video);
            response.setMessage("Video uploaded successfully. Processing started asynchronously.");
            response.setStatus("PROCESSING");

            return response;

        } catch (Exception e) {
            logger.error("Async video processing failed", e);
            throw new RuntimeException("Async video processing failed: " + e.getMessage(), e);
        }
    }

    private boolean sendVideoProcessingMessage(VideoProcessingMessageDto message, Integer priority) {
        try {
            logger.info("Sending video processing message to queue: {}", RabbitMQConfig.VIDEO_PROCESSING_QUEUE);
            logger.debug("Message details - VideoId: {}, Priority: {}", message.getVideoId(), priority);
            
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.VIDEO_PROCESSING_EXCHANGE,
                    RabbitMQConfig.VIDEO_PROCESSING_ROUTING_KEY,
                    message,
                    messagePostProcessor -> {
                        MessageProperties properties = messagePostProcessor.getMessageProperties();
                        if (priority != null) {
                            properties.setPriority(priority);
                        }
                        properties.setExpiration("3600000"); // 1 hour TTL
                        properties.setContentType("application/json");
                        return messagePostProcessor;
                    }
            );
            
            logger.info("Message sent successfully to exchange: {} with routing key: {}", 
                       RabbitMQConfig.VIDEO_PROCESSING_EXCHANGE, 
                       RabbitMQConfig.VIDEO_PROCESSING_ROUTING_KEY);
            
            return true;
            
        } catch (AmqpException e) {
            logger.error("Failed to send message to RabbitMQ", e);
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error while sending message", e);
            return false;
        }
    }

    public List<VideoProcessingResponseDto> processMultipleVideosAsync(List<VideoUploadRequestDto> requestDtos, Integer priority) throws Exception {
        List<VideoProcessingResponseDto> responses = new java.util.ArrayList<>();
        for (VideoUploadRequestDto requestDto : requestDtos) {
            try {
                VideoProcessingResponseDto response = processVideoAsync(requestDto, priority);
                responses.add(response);
            } catch (Exception e) {
                logger.error("Failed to process video: {}", requestDto.getFile().getOriginalFilename(), e);
                VideoProcessingResponseDto errorResponse = new VideoProcessingResponseDto();
                errorResponse.setStatus("FAILED");
                errorResponse.setMessage("Failed to process: " + e.getMessage());
                responses.add(errorResponse);
            }
        }
        return responses;
    }

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
        String currentProcessingStatus = processingStatus.get(id);
        if (currentProcessingStatus != null) {
            response.setMessage("Current processing status: " + currentProcessingStatus);
        }

        return response;
    }

    public VideoProcessingResponseDto getVideoProgress(Long id) {
        VideoProcessingResponseDto response = getVideoStatus(id);
        if (response.getProgressPercentage() != null && response.getProgressPercentage() > 0) {
            long estimatedTimeRemaining = calculateEstimatedTimeRemaining(id, response.getProgressPercentage());
            response.setEstimatedTimeRemaining(estimatedTimeRemaining);
        }
        return response;
    }

    public List<VideoProcessingResponseDto> getProcessingQueue() {
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

        video.setStatus(VideoStatus.CANCELLED);
        video.setProgressPercentage(0);
        video.setProcessingEndTime(LocalDateTime.now());
        videoRepository.save(video);

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
        // Docker container içinde /app/uploads dizinini kullan
        Path uploadPath = Paths.get("/app/uploads");
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            logger.info("Created uploads directory: {}", uploadPath.toAbsolutePath());
        }

        String originalFilename = requestDto.getFile().getOriginalFilename();
        if (originalFilename == null) {
            originalFilename = "unknown_file";
        }
        
        String filename = System.currentTimeMillis() + "_" + originalFilename;
        Path filePath = uploadPath.resolve(filename);

        // Dosyayı kaydet
        requestDto.getFile().transferTo(filePath.toFile());
        logger.info("File saved to: {}", filePath.toAbsolutePath());

        // Video entity oluştur
        VideoEntity video = new VideoEntity();
        video.setFilename(filename);
        video.setOriginalPath(filePath.toAbsolutePath().toString());
        video.setStatus(VideoStatus.UPLOADED);
        video.setFileSize(requestDto.getFile().getSize());

        return videoRepository.save(video);
    }

    private long calculateEstimatedTimeRemaining(Long videoId, Integer progressPercentage) {
        if (progressPercentage >= 100) return 0;
        long averageProcessingTime = 300000; 
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
            } catch (Exception ignored) {
                logger.warn("Failed to parse video metadata for video ID: {}", video.getId());
            }
        }

        return dto;
    }
}