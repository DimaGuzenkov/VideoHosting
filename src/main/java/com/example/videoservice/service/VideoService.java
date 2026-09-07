package com.example.videoservice.service;

import com.example.videoservice.event.VideoUploadedEvent;
import com.example.videoservice.model.Video;
import com.example.videoservice.model.VideoStatus;
import com.example.videoservice.repository.VideoRepository;
import com.example.videoservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;
    private final UserRepository userRepository;
    private final EventPublisher eventPublisher; // новый продюсер

    @Transactional
    public Video createVideo(String title, String description, String filePath, Long userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Video video = Video.builder()
                .title(title)
                .description(description)
                .filePath(filePath)
                .status(VideoStatus.UPLOADED)
                .user(user)
                .views(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Video saved = videoRepository.save(video);

        // Публикуем событие в Kafka
        eventPublisher.publishVideoUploaded(
                new VideoUploadedEvent(saved.getId(), saved.getFilePath(), userId)
        );

        return saved;
    }

    public List<Video> getVideosByUserId(Long userId) {
        return videoRepository.findAllByUserId(userId);
    }

    public Video getVideoById(Long id) {
        return videoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Video not found"));
    }

    @Transactional
    public void updateStatus(Long videoId, VideoStatus status) {
        Video video = getVideoById(videoId);
        video.setStatus(status);
        video.setUpdatedAt(LocalDateTime.now());
        videoRepository.save(video);
    }

    @Transactional
    public void updateStatusAndPlaylist(Long videoId, VideoStatus status, String playlistPath) {
        Video video = getVideoById(videoId);
        video.setStatus(status);
        video.setPlaylistPath(playlistPath);
        video.setUpdatedAt(LocalDateTime.now());
        videoRepository.save(video);
    }

    @Transactional
    public void incrementViews(Long videoId) {
        videoRepository.incrementViews(videoId);
    }

    @Transactional
    public void deleteVideo(Long id) {
        Video video = getVideoById(id); // проверяет существование и выбрасывает исключение
        videoRepository.delete(video);
    }
}