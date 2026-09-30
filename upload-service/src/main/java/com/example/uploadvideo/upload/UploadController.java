package com.example.uploadvideo.upload;

import com.example.uploadvideo.upload.data.CompleteUploadRequest;
import com.example.uploadvideo.upload.data.InitUploadRequest;
import com.example.uploadvideo.db.VideoService;
import com.example.uploadvideo.upload.data.InitUploadResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.s3.model.CompletedPart;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/videos/upload")
@RequiredArgsConstructor
@Slf4j
public class UploadController {

    private final MultipartUploadService multipart;
    private final UploadSessionService sessions;
    private final VideoService videoService;

    @PostMapping("/init")
    public ResponseEntity<?> init(@Valid @RequestBody InitUploadRequest req,
                                  @RequestHeader("X-User-Id") Long userId) throws Exception {
        var result = multipart.initiate(userId, req);

        sessions.save(result.uploadId(), new UploadSessionService.Session(
                userId, result.objectKey(), req.getTitle(), req.getDescription(),
                req.getFileName(), req.getFileSize(), req.getContentType()
        ));

        return ResponseEntity.ok(new InitUploadResponse(
                result.uploadId(), result.objectKey(), result.partSize(), result.parts()
        ));
    }

    @PostMapping("/complete")
    public ResponseEntity<?> complete(@RequestBody CompleteUploadRequest req,
                                      @RequestHeader("X-User-Id") Long userId) throws Exception {
        UploadSessionService.Session session = sessions.get(req.getUploadId());
        if (session == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Session not found or expired"));
        }
        if (!session.userId().equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        // Конвертируем DTO в CompletedPart из AWS SDK
        List<CompletedPart> parts = req.getParts().stream()
                .map(p -> CompletedPart.builder()
                        .partNumber(p.getPartNumber())
                        .eTag(p.getEtag())
                        .build())
                .toList();

        multipart.complete(req.getUploadId(), session.objectKey(), parts);

        var video = videoService.createVideo(
                session.title(), session.description(), session.objectKey(), userId
        );
        sessions.delete(req.getUploadId());

        return ResponseEntity.ok(Map.of(
                "id", video.getId(),
                "status", video.getStatus(),
                "filePath", video.getFilePath()
        ));
    }

    @PostMapping("/abort")
    public ResponseEntity<?> abort(@RequestBody Map<String, String> body,
                                   @RequestHeader("X-User-Id") Long userId) {
        String uploadId = body.get("uploadId");
        UploadSessionService.Session session = sessions.get(uploadId);
        if (session == null) {
            return ResponseEntity.ok().build();
        }
        if (!session.userId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }
        multipart.abort(uploadId, session.objectKey());
        sessions.delete(uploadId);
        return ResponseEntity.ok().build();
    }
}