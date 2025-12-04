package com.example.orgmanager.storage;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StoredImportFile {
    String bucket;
    String objectKey;
    String fileName;
    long contentLength;
    String contentType;
}
