package com.example.orgmanager.storage;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class S3ImportStorageService implements ImportStorageService {
    private static final Logger LOGGER = LoggerFactory.getLogger(S3ImportStorageService.class);
    private static final String YAML_CONTENT_TYPE = "application/x-yaml";

    private final S3Client s3Client;
    private final ImportStorageProperties properties;
    private final AtomicBoolean bucketReady = new AtomicBoolean(false);

    public S3ImportStorageService(S3Client s3Client, ImportStorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    public StagedImportFile stage(Long jobId, String originalFileName, byte[] content) {
        ensureBucketExists();

        String sanitizedName = sanitizeFileName(originalFileName);
        String stagingKey = "%s/staging/%d/%s".formatted(
                properties.getPrefix(),
                jobId,
                UUID.randomUUID());
        String finalKey = "%s/jobs/%d/%s".formatted(
                properties.getPrefix(),
                jobId,
                sanitizedName);
        try {
            var request = PutObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(stagingKey)
                    .contentType(YAML_CONTENT_TYPE)
                    .contentLength((long) content.length)
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(content));
        } catch (SdkException ex) {
            throw new ImportStorageException("Не удалось сохранить файл импорта", ex);
        }

        StoredImportFile finalObject = StoredImportFile.builder()
                .bucket(properties.getBucket())
                .objectKey(finalKey)
                .fileName(sanitizedName)
                .contentLength(content.length)
                .contentType(YAML_CONTENT_TYPE)
                .build();

        return new S3StagedImportFile(s3Client, properties.getBucket(), stagingKey, finalObject);
    }

    @Override
    public InputStream openStream(String bucket, String objectKey) {
        try {
            return s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
        } catch (SdkException ex) {
            throw new ImportStorageException("Не удалось получить файл импорта", ex);
        }
    }

    private void ensureBucketExists() {
        if (bucketReady.get()) {
            return;
        }
        synchronized (bucketReady) {
            if (bucketReady.get()) {
                return;
            }
            HeadBucketRequest headRequest =
                    HeadBucketRequest.builder().bucket(properties.getBucket()).build();
            try {
                s3Client.headBucket(headRequest);
                bucketReady.set(true);
                return;
            } catch (NoSuchBucketException ex) {
                // proceed to create
            } catch (SdkException ex) {
                throw new ImportStorageException("Не удалось проверить состояние бакета", ex);
            }
            try {
                s3Client.createBucket(CreateBucketRequest.builder()
                        .bucket(properties.getBucket())
                        .build());
                bucketReady.set(true);
            } catch (SdkException ex) {
                throw new ImportStorageException("Не удалось создать бакет для импортов", ex);
            }
        }
    }

    private String sanitizeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "import.yaml";
        }
        String normalized = originalFileName.replace("\\", "/");
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash >= 0) {
            normalized = normalized.substring(lastSlash + 1);
        }
        normalized = normalized.replaceAll("[^A-Za-z0-9._-]", "_");
        if (normalized.length() > 120) {
            normalized = normalized.substring(normalized.length() - 120);
        }
        return normalized.isBlank() ? "import.yaml" : normalized;
    }

    private record S3StagedImportFile(
            S3Client s3Client,
            String bucket,
            String stagingKey,
            StoredImportFile finalObject) implements StagedImportFile {

        @Override
        public StoredImportFile getFinalObject() {
            return finalObject;
        }

        @Override
        public void commit() {
            try {
                s3Client.copyObject(CopyObjectRequest.builder()
                        .sourceBucket(bucket)
                        .sourceKey(stagingKey)
                        .destinationBucket(bucket)
                        .destinationKey(finalObject.getObjectKey())
                        .build());
                deleteQuietly(stagingKey);
            } catch (SdkException ex) {
                throw new ImportStorageException("Не удалось зафиксировать файл импорта", ex);
            }
        }

        @Override
        public void rollback() {
            deleteQuietly(stagingKey);
            deleteQuietly(finalObject.getObjectKey());
        }

        private void deleteQuietly(String key) {
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build());
            } catch (SdkException ex) {
                LOGGER.warn("Не удалось удалить временный файл импорта {}: {}", key, ex.getMessage());
            }
        }
    }
}
