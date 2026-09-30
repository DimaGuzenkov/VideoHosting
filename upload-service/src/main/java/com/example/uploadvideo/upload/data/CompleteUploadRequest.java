package com.example.uploadvideo.upload.data;

import lombok.Data;

import java.util.List;

@Data
public class CompleteUploadRequest {
    private String uploadId;
    private String objectKey;
    private String title;
    private String description;
    private String fileName;
    private List<CompletedPart> parts;

    @Data
    public static class CompletedPart {
        private int partNumber;
        private String etag;
    }
}
