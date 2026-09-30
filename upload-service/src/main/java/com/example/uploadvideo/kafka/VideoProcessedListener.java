package com.example.uploadvideo.kafka;

import com.example.avro.VideoProcessedEvent;
import com.example.uploadvideo.db.model.VideoStatus;
import com.example.uploadvideo.db.VideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoProcessedListener {

    private final VideoService videoService;

    @KafkaListener(topics = "video.processed", groupId = "upload-service-processed-group")
    public void onVideoProcessed(VideoProcessedEvent event) {
        Long videoId = event.getVideoId();
        String status = event.getStatus().toString();
        String playlistPath = event.getPlaylistPath() == null ? null : event.getPlaylistPath().toString();

        log.info("📥 Received video.processed: videoId={}, status={}", videoId, status);

        videoService.updateStatusAndPlaylist(videoId, VideoStatus.valueOf(status), playlistPath);
        log.info("✅ Updated video {} → {}", videoId, status);
    }
}
