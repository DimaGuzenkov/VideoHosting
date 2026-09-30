package com.example.processor.scheduler.data;

import com.example.avro.VideoUploadedEvent;
import com.example.processor.ffmpeg.Quality;

import java.util.List;

/**
 * Задача в очереди. Может нести одно качество (fast) или несколько (slow).
 * Несколько качеств обрабатываются одним FFmpeg за один проход.
 */
public record VideoTask(
        VideoUploadedEvent event,
        List<Quality> qualities,
        Priority priority
) {
    public enum Priority { FAST, SLOW }
}
