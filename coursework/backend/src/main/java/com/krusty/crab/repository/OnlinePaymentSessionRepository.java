package com.krusty.crab.repository;

import com.krusty.crab.entity.OnlinePaymentSession;
import com.krusty.crab.entity.enums.OnlinePaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OnlinePaymentSessionRepository extends JpaRepository<OnlinePaymentSession, UUID> {

    Optional<OnlinePaymentSession> findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(
        Integer orderId,
        Collection<OnlinePaymentStatus> statuses
    );

    Optional<OnlinePaymentSession> findByBankTransactionId(UUID bankTransactionId);
}
