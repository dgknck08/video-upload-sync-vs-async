package com.example.videoprocessor.entity.enums;

public enum VideoStatus {
    UPLOADED,          
    PROCESSING,        
    THUMBNAIL_CREATING,
    THUMBNAIL_CREATED,  
    TRANSCODING,      
    TRANSCODED,        
    METADATA_EXTRACTING,
    METADATA_EXTRACTED, 
    COMPLETED,          
    FAILED,             
    CANCELLED           
}