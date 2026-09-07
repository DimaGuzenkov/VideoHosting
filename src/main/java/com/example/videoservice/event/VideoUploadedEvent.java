package com.example.videoservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VideoUploadedEvent implements Serializable {
    private Long videoId;
    private String filePath;
    private Long userId;
}