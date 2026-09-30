package com.example.uploadvideo.upload;

import com.example.uploadvideo.upload.data.InitUploadRequest;
import com.example.uploadvideo.upload.data.PresignedPart;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MultipartUploadService {

    private final S3AsyncClient s3AsyncClient;
    private final S3Presigner s3Presigner;

    @Value("${minio.bucket-name}")
    private String bucket;

    private static final int MAX_PARTS = 10_000;
    private static final long MIN_PART_SIZE = 5L * 1024 * 1024;      // 5 MB
    private static final long MAX_PART_SIZE = 500L * 1024 * 1024;   // 500 MB

    public record InitResult(String uploadId, String objectKey, long partSize,
                             List<PresignedPart> parts) {}

    /**
     * Шаг 1: создаём multipart upload и генерируем presigned URL для каждой части.
     */
    public InitResult initiate(Long userId, InitUploadRequest req) throws Exception {
        String objectKey = "videos/" + userId + "/" +
                UUID.randomUUID() + "_" + sanitize(req.getFileName());

        // 1. Создаём multipart upload
        CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(req.getContentType())
                .build();

        CompletableFuture<CreateMultipartUploadResponse> createFuture =
                s3AsyncClient.createMultipartUpload(createRequest);

        CreateMultipartUploadResponse createResponse = createFuture.get(10, TimeUnit.SECONDS);
        String uploadId = createResponse.uploadId();

        // 2. Считаем размер части
        long partSize = computePartSize(req.getFileSize());
        int partCount = (int) Math.ceil((double) req.getFileSize() / partSize);

        // 3. Генерируем presigned URL для каждой части
        List<PresignedPart> parts = new ArrayList<>(partCount);
        for (int i = 1; i <= partCount; i++) {
            UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .uploadId(uploadId)
                    .partNumber(i)
                    .build();

            UploadPartPresignRequest presignRequest = UploadPartPresignRequest.builder()
                    .signatureDuration(Duration.ofHours(1))
                    .uploadPartRequest(uploadPartRequest)
                    .build();

            PresignedUploadPartRequest presignedRequest =
                    s3Presigner.presignUploadPart(presignRequest);

            parts.add(new PresignedPart(i, presignedRequest.url().toString()));
        }

        log.info("🚀 Initiated multipart uploadId={}, parts={}, partSize={}MB",
                uploadId, partCount, partSize / 1024 / 1024);
        return new InitResult(uploadId, objectKey, partSize, parts);
    }

    /**
     * Шаг 2: завершаем multipart upload, передавая список (partNumber, ETag).
     */
    public void complete(String uploadId, String objectKey, List<CompletedPart> parts) throws Exception {
        CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder()
                        .parts(parts)
                        .build())
                .build();

        CompletableFuture<CompleteMultipartUploadResponse> completeFuture =
                s3AsyncClient.completeMultipartUpload(completeRequest);

        completeFuture.get(30, TimeUnit.SECONDS);
        log.info("✅ Completed multipart uploadId={}, object={}", uploadId, objectKey);
    }

    /**
     * Прерываем multipart upload.
     */
    public void abort(String uploadId, String objectKey) {
        try {
            AbortMultipartUploadRequest abortRequest = AbortMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .uploadId(uploadId)
                    .build();

            s3AsyncClient.abortMultipartUpload(abortRequest).get(10, TimeUnit.SECONDS);
            log.info("🗑️ Aborted multipart uploadId={}", uploadId);
        } catch (Exception e) {
            log.warn("Failed to abort uploadId={}: {}", uploadId, e.getMessage());
        }
    }

    private long computePartSize(long fileSize) {
        long size = Math.max(MIN_PART_SIZE, fileSize / MAX_PARTS);
        // округляем вверх до 1 МБ
        size = ((size + 1024 * 1024 - 1) / (1024 * 1024)) * 1024 * 1024;
        return Math.min(size, MAX_PART_SIZE);
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}