package com.example.orgmanager.storage;

import java.io.InputStream;

public interface ImportStorageService {
    StagedImportFile stage(Long jobId, String originalFileName, byte[] content);

    InputStream openStream(String bucket, String objectKey);

    StoredImportFile locateByJobId(Long jobId);
}
