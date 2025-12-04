package com.example.orgmanager.service.dto;

import java.io.IOException;
import java.io.InputStream;

public record ImportFileData(
        String fileName,
        Long fileSize,
        String contentType,
        InputStream stream) implements AutoCloseable {

    @Override
    public void close() throws IOException {
        stream.close();
    }
}
