package com.krusty.crab.service;

import com.krusty.crab.entity.Review;
import com.krusty.crab.exception.EntityNotFoundException;
import com.krusty.crab.exception.ReviewException;
import com.krusty.crab.repository.OrderRepository;
import com.krusty.crab.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {
    
    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    
    public Review getReviewById(Integer id) {
        return reviewRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Review", id));
    }
    
    public List<Review> getReviewsByClientId(Integer clientId) {
        return reviewRepository.findByClientId(clientId);
    }
    
    public List<Review> getReviewsByOrderId(Integer orderId) {
        return reviewRepository.findByOrderId(orderId);
    }
    
    public List<Review> getAllReviews() {
        return reviewRepository.findAll();
    }
    
    @Transactional
    public Review createReview(Review review) {
        orderRepository.findById(review.getOrder().getId())
            .orElseThrow(() -> new EntityNotFoundException("Order", review.getOrder().getId()));
        
        List<Review> existing = reviewRepository.findByOrderId(review.getOrder().getId());
        if (existing.stream().anyMatch(r -> r.getClient().getId().equals(review.getClient().getId()))) {
            throw new ReviewException("Review already exists for this order and client");
        }
        
        Review saved = reviewRepository.save(review);
        log.info("Review created with ID: {} for order: {}", saved.getId(), review.getOrder().getId());
        return saved;
    }
    
    @Transactional
    public Review updateReview(Integer id, Review reviewData) {
        Review review = getReviewById(id);
        if (reviewData.getRating() != null) {
            review.setRating(reviewData.getRating());
        }
        if (reviewData.getComment() != null) {
            review.setComment(reviewData.getComment());
        }
        Review updated = reviewRepository.save(review);
        log.info("Review {} updated", id);
        return updated;
    }
    
    @Transactional
    public void deleteReview(Integer id) {
        Review review = getReviewById(id);
        reviewRepository.delete(review);
        log.info("Review {} deleted", id);
    }
}

