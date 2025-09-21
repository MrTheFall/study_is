package com.example.orgmanager.service.lock;

import com.example.orgmanager.repository.AppLockRepository;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseLockService {
    private final AppLockRepository appLockRepository;

    public DatabaseLockService(AppLockRepository appLockRepository) {
        this.appLockRepository = appLockRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryAcquire(String lockName) {
        ensureLockExists(lockName);
        try {
            return appLockRepository.findByNameForUpdate(lockName).isPresent();
        } catch (PessimisticLockingFailureException | PessimisticLockException | LockTimeoutException ex) {
            return false;
        }
    }

    private void ensureLockExists(String lockName) {
        appLockRepository.insertIgnore(lockName);
    }
}
