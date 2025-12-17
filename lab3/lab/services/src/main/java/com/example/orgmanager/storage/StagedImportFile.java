package com.example.orgmanager.storage;

public interface StagedImportFile {
    StoredImportFile getFinalObject();

    void commit();

    void rollback();
}
