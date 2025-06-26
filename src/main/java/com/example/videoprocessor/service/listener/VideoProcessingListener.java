package com.example.videoprocessor.service.listener;

import com.example.videoprocessor.dto.VideoProcessingMessageDto;
import com.example.videoprocesor.config.RabbitMQConfig;
import com.example.videoprocessor.dto.VideoMetadataDto;
import com.example.videoprocessor.entity.VideoEntity;
import com.example.videoprocessor.entity.enums.VideoStatus;
import com.example.videoprocessor.repository.VideoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired
    private VideoRepository videoRepository;
    
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * RabbitMQ LISTENER - Video Processing
     * 
     * Özellikler:
     * - Automatic acknowledgment
     * - Error handling ile DLQ'ya gönderme
     * - Concurrent processing
     * - Message prefetch kontrolü
     */
    @RabbitListener(queues = RabbitMQConfig.VIDEO_PROCESSING_QUEUE)
    public void processVideo(VideoProcessingMessageDto message) {
        Optional<VideoEntity> videoOpt = videoRepository.findById(message.getVideoId());
        if (videoOpt.isEmpty()) {
            throw new RuntimeException("Video not found with ID: " + message.getVideoId());
        }
        
        VideoEntity video = videoOpt.get();
        
        try {
            // İşlem başlangıcı
            video.setStatus(VideoStatus.PROCESSING);
            video.setProcessingStartTime(LocalDateTime.now());
            video.setProgressPercentage(10);
            videoRepository.save(video);
            
            // 1. THUMBNAIL OLUŞTURMA
            video.setStatus(VideoStatus.THUMBNAIL_CREATING);
            video.setProgressPercentage(25);
            videoRepository.save(video);
            
            String thumbnailPath = createThumbnailWithFFmpeg(video.getOriginalPath(), video.getFilename());
            video.setThumbnailPath(thumbnailPath);
            video.setStatus(VideoStatus.THUMBNAIL_CREATED);
            video.setProgressPercentage(40);
            videoRepository.save(video);
            
            // 2. VIDEO TRANSCODING
            video.setStatus(VideoStatus.TRANSCODING);
            video.setProgressPercentage(50);
            videoRepository.save(video);
            
            String transcodedPath = transcodeVideoWithFFmpeg(video.getOriginalPath(), video.getFilename());
            video.setProcessedPath(transcodedPath);
            video.setStatus(VideoStatus.TRANSCODED);
            video.setProgressPercentage(75);
            videoRepository.save(video);
            
            // 3. METADATA ÇIKARMA
            video.setStatus(VideoStatus.METADATA_EXTRACTING);
            video.setProgressPercentage(85);
            videoRepository.save(video);
            
            VideoMetadataDto metadata = extractMetadataWithFFprobe(video.getOriginalPath());
            video.setMetadata(objectMapper.writeValueAsString(metadata));
            video.setDuration(metadata.getDuration());
            video.setResolution(metadata.getResolution());
            video.setCodec(metadata.getCodec());
            video.setFrameRate(metadata.getFrameRate());
            
            // İŞLEM TAMAMLANDI
            video.setStatus(VideoStatus.COMPLETED);
            video.setProgressPercentage(100);
            video.setProcessingEndTime(LocalDateTime.now());
            videoRepository.save(video);
            
        } catch (Exception e) {
            // HATA DURUMU - Bu exception RabbitMQ tarafından yakalanacak DLQye gidecek
            video.setStatus(VideoStatus.FAILED);
            video.setErrorMessage(e.getMessage());
            video.setProgressPercentage(0);
            video.setProcessingEndTime(LocalDateTime.now());
            videoRepository.save(video);
            
            //DLQya gönderilir
            throw new RuntimeException("Video processing failed: " + e.getMessage(), e);
        }
    }

    //Dead Letter Queue Listener - Başarısız mesajları silmek için
    @RabbitListener(queues = RabbitMQConfig.VIDEO_PROCESSING_DLQ)
    public void handleFailedVideoProcessing(VideoProcessingMessageDto message) {
        // DLQ'ya düşen mesajları loglayabilir veya admin'e bildirim gönderebiliriz
        System.err.println("Failed video processing message received in DLQ: " + message.getVideoId());
        
        // Veritabanında video durumunu güncelle
        Optional<VideoEntity> videoOpt = videoRepository.findById(message.getVideoId());
        if (videoOpt.isPresent()) {
            VideoEntity video = videoOpt.get();
            video.setStatus(VideoStatus.FAILED);
            video.setErrorMessage("Processing failed and moved to DLQ");
            video.setProcessingEndTime(LocalDateTime.now());
            videoRepository.save(video);
        }
    }

    // FFmpeg metodları 
    private String createThumbnailWithFFmpeg(String videoPath, String filename) throws Exception {
        String outputDir = "thumbnails/";
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
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("FFmpeg thumbnail creation failed");
        }
        
        return thumbnailPath;
    }

    private String transcodeVideoWithFFmpeg(String inputPath, String filename) throws Exception {
        String outputDir = "processed/";
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
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("FFmpeg transcoding failed");
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
            throw new RuntimeException("FFprobe metadata extraction failed");
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