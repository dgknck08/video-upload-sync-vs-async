package com.example.videoproccesor.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.videoproccesor.entity.VideoEntity;
import com.example.videoproccesor.entity.enums.VideoStatus;

@Repository
public interface VideoRepository extends JpaRepository<VideoEntity, Long> {
    List<VideoEntity> findByStatus(VideoStatus status);
}
