package com.example.videoprocessor.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.videoprocessor.entity.VideoEntity;
import com.example.videoprocessor.entity.enums.VideoStatus;

import java.util.List;

@Repository
public interface VideoRepository extends JpaRepository<VideoEntity, Long> {
    List<VideoEntity> findByStatus(VideoStatus status);
    List<VideoEntity> findByStatusIn(List<VideoStatus> statuses);
    List<VideoEntity> findByFilenameContaining(String filename);
}
