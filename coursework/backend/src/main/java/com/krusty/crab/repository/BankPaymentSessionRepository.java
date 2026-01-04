package com.krusty.crab.repository;

import com.krusty.crab.entity.BankPaymentSession;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BankPaymentSessionRepository extends JpaRepository<BankPaymentSession, UUID> {}
