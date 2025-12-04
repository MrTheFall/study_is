package com.example.orgmanager.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.import-storage")
public class ImportStorageProperties {
    private String bucket = "org-imports";
    private String prefix = "imports";
    private String region = "us-east-1";
    private boolean pathStyleAccess = true;
    private String endpoint;
    private String accessKey;
    private String secretKey;
}
