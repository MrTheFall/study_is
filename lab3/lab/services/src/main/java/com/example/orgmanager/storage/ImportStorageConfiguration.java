package com.example.orgmanager.storage;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@EnableConfigurationProperties(ImportStorageProperties.class)
public class ImportStorageConfiguration {
    @Bean
    public S3Client s3Client(ImportStorageProperties properties) {
        var serviceConfig = S3Configuration.builder()
                .pathStyleAccessEnabled(properties.isPathStyleAccess())
                .build();
        var builder = S3Client.builder()
                .serviceConfiguration(serviceConfig)
                .overrideConfiguration(ClientOverrideConfiguration.builder().build())
                .region(Region.of(properties.getRegion()));

        if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
            builder = builder.endpointOverride(URI.create(properties.getEndpoint()));
        }
        if (properties.getAccessKey() != null && properties.getSecretKey() != null) {
            builder = builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                            properties.getAccessKey(),
                            properties.getSecretKey())));
        }
        return builder.build();
    }

    @Bean
    public ImportStorageService importStorageService(
            S3Client s3Client,
            ImportStorageProperties properties) {
        return new S3ImportStorageService(s3Client, properties);
    }

    @Bean
    public TransactionalImportStorage transactionalImportStorage(
            ImportStorageService storageService) {
        return new TransactionalImportStorage(storageService);
    }
}
