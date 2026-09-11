package com.example.stream.service;

import com.example.stream.model.Video;
import com.example.stream.model.VideoStatus;
import com.example.stream.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;

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