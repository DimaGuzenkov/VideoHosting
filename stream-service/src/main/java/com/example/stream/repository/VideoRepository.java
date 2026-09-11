package com.example.stream.repository;

import com.example.stream.model.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VideoRepository extends JpaRepository<Video, Long> {
    List<Video> findAllByUserId(Long userId);

    @Modifying
    @Query("UPDATE Video v SET v.views = v.views + 1 WHERE v.id = :id")
    void incrementViews(@Param("id") Long id);
}