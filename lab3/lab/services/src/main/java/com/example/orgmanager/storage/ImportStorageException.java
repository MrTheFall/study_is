package com.example.orgmanager.storage;

public class ImportStorageException extends RuntimeException {
    public ImportStorageException(String message) {
        super(message);
    }

    public ImportStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
