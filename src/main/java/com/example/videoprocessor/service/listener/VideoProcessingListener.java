package com.example.videoprocessor.service.listener;

import com.example.videoprocessor.dto.VideoProcessingMessageDto;
import com.example.videoprocessor.config.RabbitMQConfig; // Package ismini düzelttim
import com.example.videoprocessor.dto.VideoMetadataDto;
import com.example.videoprocessor.entity.VideoEntity;
import com.example.videoprocessor.entity.enums.VideoStatus;
import com.example.videoprocessor.repository.VideoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.rabbitmq.client.Channel;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
@Transactional
public class VideoProcessingListener {

    private static final Logger logger = LoggerFactory.getLogger(VideoProcessingListener.class);

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @RabbitListener(
        queues = RabbitMQConfig.VIDEO_PROCESSING_QUEUE, 
        containerFactory = "rabbitListenerContainerFactory"
    )
    public void processVideo(VideoProcessingMessageDto message, 
                           Channel channel, 
                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        
        logger.info("Received video processing message for video ID: {}", message.getVideoId());

        try {
            Optional<VideoEntity> videoOpt = videoRepository.findById(message.getVideoId());
            if (videoOpt.isEmpty()) {
                logger.error("Video not found with ID: {}", message.getVideoId());
                channel.basicNack(deliveryTag, false, false); // Dead lettera gönder
                return;
            }

            VideoEntity video = videoOpt.get();
            processVideoInternal(video);
            channel.basicAck(deliveryTag, false);
            logger.info("Video processing completed and acknowledged for video ID: {}", video.getId());

        } catch (Exception e) {
            logger.error("Video processing failed for video ID: {}", message.getVideoId(), e);
            
            try {
                channel.basicNack(deliveryTag, false, false);
                
                Optional<VideoEntity> videoOpt = videoRepository.findById(message.getVideoId());
                if (videoOpt.isPresent()) {
                    VideoEntity video = videoOpt.get();
                    video.setProcessingEndTime(LocalDateTime.now());
                    updateVideoStatus(video, VideoStatus.FAILED, 0, e.getMessage());
                }
            } catch (Exception nackException) {
                logger.error("Failed to nack message", nackException);
            }
        }
    }

    private void processVideoInternal(VideoEntity video) throws Exception {
        logger.info("Starting video processing for video ID: {}", video.getId());
        
        updateVideoStatus(video, VideoStatus.PROCESSING, 10, null);

        // 1. Thumbnail oluştur
        logger.info("Creating thumbnail for video ID: {}", video.getId());
        updateVideoStatus(video, VideoStatus.THUMBNAIL_CREATING, 25, null);
        
        String thumbnailPath = createThumbnailWithFFmpeg(video.getOriginalPath(), video.getFilename());
        video.setThumbnailPath(thumbnailPath);
        updateVideoStatus(video, VideoStatus.THUMBNAIL_CREATED, 40, null);

        // 2. Video transcode et
        logger.info("Transcoding video ID: {}", video.getId());
        updateVideoStatus(video, VideoStatus.TRANSCODING, 50, null);
        
        String transcodedPath = transcodeVideoWithFFmpeg(video.getOriginalPath(), video.getFilename());
        video.setProcessedPath(transcodedPath);
        updateVideoStatus(video, VideoStatus.TRANSCODED, 75, null);

        // 3. Metadata çıkar
        logger.info("Extracting metadata for video ID: {}", video.getId());
        updateVideoStatus(video, VideoStatus.METADATA_EXTRACTING, 85, null);
        
        VideoMetadataDto metadata = extractMetadataWithFFprobe(video.getOriginalPath());
        video.setMetadata(objectMapper.writeValueAsString(metadata));
        video.setDuration(metadata.getDuration());
        video.setResolution(metadata.getResolution());
        video.setCodec(metadata.getCodec());
        video.setFrameRate(metadata.getFrameRate());

        // 4. Tamamla
        video.setProcessingEndTime(LocalDateTime.now());
        updateVideoStatus(video, VideoStatus.COMPLETED, 100, null);
        
        logger.info("Video processing completed successfully for video ID: {}", video.getId());
    }

    private void updateVideoStatus(VideoEntity video, VideoStatus status, int progress, String errorMessage) {
        try {
            video.setStatus(status);
            video.setProgressPercentage(progress);
            if (errorMessage != null) {
                video.setErrorMessage(errorMessage);
            }
            if (status == VideoStatus.PROCESSING) {
                video.setProcessingStartTime(LocalDateTime.now());
            }
            videoRepository.save(video);
            logger.debug("Updated video status: {} - Progress: {}%", status, progress);
        } catch (Exception e) {
            logger.error("Failed to update video status", e);
        }
    }

    private String createThumbnailWithFFmpeg(String videoPath, String filename) throws Exception {
        String outputDir = "/app/thumbnails/";
        Path outputPath = Paths.get(outputDir);
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
        }

        String thumbnailPath = outputDir + filename + "_thumb.jpg";

        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-i", videoPath,
                "-ss", "00:00:05",
                "-vframes", "1",
                "-vf", "scale=320:240",
                "-q:v", "2",
                "-y", thumbnailPath
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            logger.debug("FFmpeg thumbnail output: {}", output.toString());
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("FFmpeg thumbnail creation failed with exit code: " + exitCode);
        }

        return thumbnailPath;
    }

    private String transcodeVideoWithFFmpeg(String inputPath, String filename) throws Exception {
        String outputDir = "/app/processed/";
        Path outputPath = Paths.get(outputDir);
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
        }

        String processedPath = outputDir + filename + "_processed.mp4";

        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-i", inputPath,
                "-c:v", "libx264",
                "-c:a", "aac",
                "-b:v", "1000k",
                "-b:a", "128k",
                "-vf", "scale=1280:720",
                "-preset", "medium",
                "-crf", "23",
                "-movflags", "+faststart",
                "-y", processedPath
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            logger.debug("FFmpeg transcoding output: {}", output.toString());
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("FFmpeg transcoding failed with exit code: " + exitCode);
        }

        return processedPath;
    }

    private VideoMetadataDto extractMetadataWithFFprobe(String videoPath) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "ffprobe", "-v", "quiet",
                "-print_format", "json",
                "-show_format",
                "-show_streams",
                videoPath
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("FFprobe metadata extraction failed with exit code: " + exitCode);
        }

        return parseFFprobeOutput(output.toString());
    }

    private VideoMetadataDto parseFFprobeOutput(String jsonOutput) {
        VideoMetadataDto metadata = new VideoMetadataDto();
        metadata.setDuration(120L);
        metadata.setResolution("1280x720");
        metadata.setCodec("h264");
        metadata.setFrameRate(30.0);
        metadata.setFormat("mp4");
        metadata.setBitrate(1000);
        metadata.setAudioCodec("aac");
        metadata.setAudioChannels(2);
        metadata.setAudioSampleRate(44100);
        return metadata;
    }
}