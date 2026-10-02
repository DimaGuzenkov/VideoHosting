package com.example.processor.processing;

import com.example.processor.ffmpeg.Quality;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "video.processing")
@Data
public class VideoProcessingProperties {

    private List<Quality> fast = new ArrayList<>();
    private List<Quality> slow = new ArrayList<>();
    private int hlsTime = 10;
    private String hlsListSize = "0";

    public List<Quality> getAllQualities() {
        List<Quality> all = new ArrayList<>();
        all.addAll(fast);
        all.addAll(slow);
        return all;
    }
}