package com.example.processor.storage;

import io.minio.*;
import io.minio.errors.MinioException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    public void uploadFile(Path file, String objectName) {
        try (InputStream is = Files.newInputStream(file)) {
            String contentType = file.getFileName().toString().endsWith(".m3u8")
                    ? "application/vnd.apple.mpegurl"
                    : "video/MP2T";

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(is, Files.size(file), -1)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Upload failed for " + objectName + ": " + e.getMessage(), e);
        }
    }


    public void uploadDirectory(Path dir, String basePath) {
        try (var stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile)
                    .forEach(file -> uploadFile(file, basePath + file.getFileName().toString()));
        } catch (IOException e) {
            throw new RuntimeException("Directory upload failed: " + e.getMessage(), e);
        }
    }


    public InputStream downloadFile(String objectName) throws IOException {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (MinioException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Error downloading file from MinIO: " + e.getMessage(), e);
        }
    }
}