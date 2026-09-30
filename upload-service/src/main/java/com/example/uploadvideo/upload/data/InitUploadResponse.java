package com.example.uploadvideo.upload.data;

import java.util.List;

public record InitUploadResponse(
        String uploadId,
        String objectKey,
        long partSize,
        List<PresignedPart> parts
) {}
