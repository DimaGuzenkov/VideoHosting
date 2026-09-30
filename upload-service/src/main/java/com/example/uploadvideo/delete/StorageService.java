package com.example.uploadvideo.delete;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.util.List;

@Service
@Slf4j
public class StorageService {

    private final S3Client s3Client;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.public-endpoint}")
    private String publicEndpoint;

    public StorageService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public String getPublicUrl(String objectName) {
        return publicEndpoint + "/" + bucketName + "/" + objectName;
    }

    public void uploadFile(InputStream inputStream, long size, String objectName, String contentType) {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectName)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromInputStream(inputStream, size)
            );
        } catch (Exception e) {
            throw new RuntimeException("Error uploading to MinIO: " + e.getMessage(), e);
        }
    }

    public void deleteFile(String objectName) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Error deleting from MinIO: " + e.getMessage(), e);
        }
    }

    public InputStream downloadFile(String objectName) {
        try {
            ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(
                    GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectName)
                            .build());
            return stream;
        } catch (Exception e) {
            throw new RuntimeException("Error downloading from MinIO: " + e.getMessage(), e);
        }
    }

    public void deleteFilesWithPrefix(String prefix) {
        try {
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .build();

            ListObjectsV2Response listResponse;
            do {
                listResponse = s3Client.listObjectsV2(listRequest);

                List<ObjectIdentifier> toDelete = listResponse.contents().stream()
                        .map(obj -> ObjectIdentifier.builder().key(obj.key()).build())
                        .toList();

                if (!toDelete.isEmpty()) {
                    s3Client.deleteObjects(DeleteObjectsRequest.builder()
                            .bucket(bucketName)
                            .delete(Delete.builder().objects(toDelete).build())
                            .build());
                    toDelete.forEach(o -> log.info("Deleted: {}", o.key()));
                }

                listRequest = listRequest.toBuilder()
                        .continuationToken(listResponse.nextContinuationToken())
                        .build();
            } while (listResponse.isTruncated());
        } catch (Exception e) {
            throw new RuntimeException("Error deleting by prefix: " + e.getMessage(), e);
        }
    }
}