package com.example.uploadvideo.upload.data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class InitUploadRequest {
    @NotBlank private String title;
    private String description;
    @NotBlank private String fileName;
    @Positive private long fileSize;
    @NotBlank private String contentType;
}
