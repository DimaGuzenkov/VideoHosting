package com.example.processor.processing;

import com.example.processor.ffmpeg.Quality;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "video.processing")
public class VideoProcessingProperties {

    private List<Quality> fast = new ArrayList<>();
    private List<Quality> slow = new ArrayList<>();

    private int hlsTime;
    private long hlsListSize;
}
