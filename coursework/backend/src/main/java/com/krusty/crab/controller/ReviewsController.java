package com.krusty.crab.controller;

import com.krusty.crab.api.ReviewsApi;
import com.krusty.crab.dto.generated.ReviewCreateRequest;
import com.krusty.crab.entity.Review;
import com.krusty.crab.entity.Client;
import com.krusty.crab.entity.Order;
import com.krusty.crab.mapper.ReviewMapper;
import com.krusty.crab.service.ReviewService;
import com.krusty.crab.service.ClientService;
import com.krusty.crab.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ReviewsController implements ReviewsApi {
    
    private final ReviewService reviewService;
    private final ReviewMapper reviewMapper;
    private final ClientService clientService;
    private final OrderService orderService;
    
    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.Review> createReview(ReviewCreateRequest reviewCreateRequest) {
        log.info("Creating review for order: {}", reviewCreateRequest.getOrderId());
        Order order = orderService.getOrderById(reviewCreateRequest.getOrderId());
        Client client = clientService.getClientById(reviewCreateRequest.getClientId());
        Review review = reviewMapper.toEntityWithRelations(reviewCreateRequest, order, client);
        Review saved = reviewService.createReview(review);
        com.krusty.crab.dto.generated.Review dto = reviewMapper.toDto(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
    
    @Override
    public ResponseEntity<List<com.krusty.crab.dto.generated.Review>> getAllReviews(Integer clientId, Integer orderId) {
        log.info("Getting reviews, clientId: {}, orderId: {}", clientId, orderId);
        List<Review> reviews;
        if (clientId != null && orderId != null) {
            reviews = reviewService.getReviewsByClientId(clientId).stream()
                .filter(r -> r.getOrder().getId().equals(orderId))
                .collect(Collectors.toList());
        } else if (clientId != null) {
            reviews = reviewService.getReviewsByClientId(clientId);
        } else if (orderId != null) {
            reviews = reviewService.getReviewsByOrderId(orderId);
        } else {
            reviews = reviewService.getAllReviews();
        }
        return ResponseEntity.ok(reviewMapper.toDtoList(reviews));
    }
    
    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.Review> getReviewById(Integer reviewId) {
        log.info("Getting review by ID: {}", reviewId);
        Review review = reviewService.getReviewById(reviewId);
        com.krusty.crab.dto.generated.Review dto = reviewMapper.toDto(review);
        return ResponseEntity.ok(dto);
    }
}

