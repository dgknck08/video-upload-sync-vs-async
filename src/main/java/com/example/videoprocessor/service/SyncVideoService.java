package com.example.videoprocessor.service;


import com.example.videoprocessor.dto.VideoMetadataDto;
import com.example.videoprocessor.dto.VideoProcessingResponseDto;
import com.example.videoprocessor.dto.VideoUploadRequestDto;
import com.example.videoprocessor.entity.VideoEntity;
import com.example.videoprocessor.entity.enums.VideoStatus;
import com.example.videoprocessor.repository.VideoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class SyncVideoService {

    @Autowired
    private VideoRepository videoRepository;
    
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * SYNCHRONOUS VIDEO PROCESSING
     * 
     * Avantajları:
     * - Basit implementasyon
     * - İşlem sonucu hemen döner
     * - Hata durumunda hemen fark edilir
     * - Debugging kolay
     * 
     * Dezavantajları:
     * - Büyük dosyalar için timeout riski
     * - Client uzun süre bekler
     * - Sunucu kaynakları bloke olur
     * - Scalability problemi
     * - Concurrent işlemler sorun yaratabilir
     */
    public VideoProcessingResponseDto processVideoSync(VideoUploadRequestDto requestDto) throws Exception {
        VideoEntity video = null;
        
        try {
            //Dosyayı kaydetme ve veritabanına ekleme
            video = saveVideoFile(requestDto);
            video.setProcessingStartTime(LocalDateTime.now());
            video.setStatus(VideoStatus.PROCESSING);
            video.setProgressPercentage(10);
            videoRepository.save(video);
            
            //SENKRON İŞLEMLER - Her adım sırayla
            //Client bunları bekler.
            
            // Thumbnail oluşturma (GERÇEK FFmpeg işlemi)
            video.setStatus(VideoStatus.THUMBNAIL_CREATING);
            video.setProgressPercentage(25);
            videoRepository.save(video);
            
            String thumbnailPath = createThumbnailWithFFmpeg(video.getOriginalPath(), video.getFilename());
            video.setThumbnailPath(thumbnailPath);
            video.setStatus(VideoStatus.THUMBNAIL_CREATED);
            video.setProgressPercentage(40);
            videoRepository.save(video);
            
            // Video transcoding (GERÇEK FFmpeg işlemi)
            video.setStatus(VideoStatus.TRANSCODING);
            video.setProgressPercentage(50);
            videoRepository.save(video);
            
            String transcodedPath = transcodeVideoWithFFmpeg(video.getOriginalPath(), video.getFilename());
            video.setProcessedPath(transcodedPath);
            video.setStatus(VideoStatus.TRANSCODED);
            video.setProgressPercentage(75);
            videoRepository.save(video);
            
            // Metadata çıkarma (GERÇEK FFprobe işlemi)
            video.setStatus(VideoStatus.METADATA_EXTRACTING);
            video.setProgressPercentage(85);
            videoRepository.save(video);
            
            VideoMetadataDto metadata = extractMetadataWithFFprobe(video.getOriginalPath());
            video.setMetadata(objectMapper.writeValueAsString(metadata));
            video.setDuration(metadata.getDuration());
            video.setResolution(metadata.getResolution());
            video.setCodec(metadata.getCodec());
            video.setFrameRate(metadata.getFrameRate());
            video.setStatus(VideoStatus.COMPLETED);
            video.setProgressPercentage(100);
            video.setProcessingEndTime(LocalDateTime.now());
            videoRepository.save(video);
            
            return convertToResponseDto(video);
            
        } catch (Exception e) {
            if (video != null) {
                video.setStatus(VideoStatus.FAILED);
                video.setErrorMessage(e.getMessage());
                video.setProgressPercentage(0);
                video.setProcessingEndTime(LocalDateTime.now());
                videoRepository.save(video);
            }
            throw e;
        }
    }
    private String createThumbnailWithFFmpeg(String videoPath, String filename) throws Exception {
        Path outputPath = Paths.get(System.getProperty("user.dir"), "thumbnails");
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
        }

        String thumbnailFilename = filename + "_thumb.jpg";
        Path thumbnailPath = outputPath.resolve(thumbnailFilename);

        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg", "-i", videoPath,
            "-ss", "00:00:05",
            "-vframes", "1",
            "-vf", "scale=320:240",
            "-q:v", "2",
            "-y",
            thumbnailPath.toAbsolutePath().toString()
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
            throw new RuntimeException("FFmpeg thumbnail creation failed. Exit code: " + exitCode + "\nOutput: " + output.toString());
        }

        return thumbnailPath.toAbsolutePath().toString();
    }
    private String transcodeVideoWithFFmpeg(String inputPath, String filename) throws Exception {
        Path outputPath = Paths.get(System.getProperty("user.dir"), "processed");
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
        }

        String processedFilename = filename + "_processed.mp4";
        Path processedPath = outputPath.resolve(processedFilename);

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
            "-y",
            processedPath.toAbsolutePath().toString()
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
            throw new RuntimeException("FFmpeg transcoding failed. Exit code: " + exitCode + "\nOutput: " + output.toString());
        }

        return processedPath.toAbsolutePath().toString();
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
            throw new RuntimeException("FFprobe metadata extraction failed. Exit code: " + exitCode);
        }

        return parseFFprobeOutput(output.toString());
    }
    private VideoMetadataDto parseFFprobeOutput(String jsonOutput) throws Exception {
        // JSON parsing 
        VideoMetadataDto metadata = new VideoMetadataDto();
        
        // Bu basit bir örnektir, gerçek JSON parsing yapılmalı
        if (jsonOutput.contains("duration")) {
            // Duration parsing
            metadata.setDuration(120L); // Örnek değer
        }
        
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

    private VideoEntity saveVideoFile(VideoUploadRequestDto requestDto) throws IOException {
        Path uploadPath = Paths.get(System.getProperty("user.dir"), "uploads");
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String filename = System.currentTimeMillis() + "_" + requestDto.getFile().getOriginalFilename();
        Path filePath = uploadPath.resolve(filename);

        // Dosyayı kaydetme
        requestDto.getFile().transferTo(filePath.toFile());

        // Veritabanına kaydetme
        VideoEntity video = new VideoEntity();
        video.setFilename(filename);
        video.setOriginalPath(filePath.toAbsolutePath().toString());
        video.setStatus(VideoStatus.UPLOADED);
        video.setFileSize(requestDto.getFile().getSize());

        return videoRepository.save(video);
    }

    public VideoProcessingResponseDto getVideoStatus(Long id) {
        Optional<VideoEntity> videoOpt = videoRepository.findById(id);
        if (videoOpt.isEmpty()) {
            VideoProcessingResponseDto response = new VideoProcessingResponseDto();
            response.setStatus("NOT_FOUND");
            response.setMessage("Video not found");
            return response;
        }
        return convertToResponseDto(videoOpt.get());
    }

    public List<VideoProcessingResponseDto> getAllVideos() {
        return videoRepository.findAll().stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    public boolean deleteVideo(Long id) {
        if (videoRepository.existsById(id)) {
            videoRepository.deleteById(id);
            return true;
        }
        return false;
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
