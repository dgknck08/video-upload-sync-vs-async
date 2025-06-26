package com.example.videoprocessor.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VideoMetadataDto {
    private Long duration;
    private String resolution;
    private String codec;
    private Double frameRate;
    private Long fileSize;
    private String format;
    private Integer bitrate;
    private String audioCodec;
    private Integer audioChannels;
    private Integer audioSampleRate;
} 