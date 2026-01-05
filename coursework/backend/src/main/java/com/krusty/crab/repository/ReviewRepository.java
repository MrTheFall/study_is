package com.krusty.crab.repository;

import com.krusty.crab.entity.Review;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Integer> {
    List<Review> findByClientId(Integer clientId);

    List<Review> findByOrderId(Integer orderId);
}
