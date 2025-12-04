package com.example.orgmanager.storage;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class TransactionalImportStorage {
    private final ImportStorageService storageService;

    public TransactionalImportStorage(ImportStorageService storageService) {
        this.storageService = storageService;
    }

    public StoredImportFile storeWithTransaction(Long jobId, String originalFileName, byte[] content) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new ImportStorageException(
                    "Хранилище импорта может использоваться только внутри транзакции");
        }

        StagedImportFile staged = storageService.stage(jobId, originalFileName, content);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                staged.commit();
            }

            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    staged.rollback();
                }
            }
        });
        return staged.getFinalObject();
    }
}
