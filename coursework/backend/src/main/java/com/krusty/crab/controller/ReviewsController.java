package com.krusty.crab.controller;

import com.krusty.crab.api.ReviewsApi;
import com.krusty.crab.dto.generated.ReviewCreateRequest;
import com.krusty.crab.entity.Client;
import com.krusty.crab.entity.Order;
import com.krusty.crab.entity.Review;
import com.krusty.crab.exception.ValidationException;
import com.krusty.crab.mapper.ReviewMapper;
import com.krusty.crab.security.UserPrincipal;
import com.krusty.crab.service.ClientService;
import com.krusty.crab.service.OrderService;
import com.krusty.crab.service.ReviewService;
import com.krusty.crab.security.SecurityContext;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ReviewsController implements ReviewsApi {

    private final ReviewService reviewService;
    private final ReviewMapper reviewMapper;
    private final ClientService clientService;
    private final OrderService orderService;

    @Override
    @PreAuthorize("hasRole('CLIENT') and authentication.principal.userId == #p0.clientId")
    public ResponseEntity<com.krusty.crab.dto.generated.Review> createReview(ReviewCreateRequest reviewCreateRequest) {
        log.info("Creating review for order: {}", reviewCreateRequest.getOrderId());
        UserPrincipal user = SecurityContext.getCurrentUser();
        if (reviewCreateRequest.getClientId() == null) {
            throw new ValidationException("clientId is required");
        }
        if (!reviewCreateRequest.getClientId().equals(user.getUserId())) {
            throw new AccessDeniedException("Access denied");
        }

        Order order = orderService.getOrderById(reviewCreateRequest.getOrderId());
        Integer orderClientId = order.getClient() != null ? order.getClient().getId() : null;
        if (orderClientId == null || !orderClientId.equals(user.getUserId())) {
            throw new AccessDeniedException("Access denied");
        }
        assertOrderReviewable(order);

        Client client = clientService.getClientById(user.getUserId());
        Review review = reviewMapper.toEntityWithRelations(reviewCreateRequest, order, client);
        Review saved = reviewService.createReview(review);
        com.krusty.crab.dto.generated.Review dto = reviewMapper.toDto(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Override
    @PreAuthorize(
            "hasRole('Manager') or (hasRole('CLIENT') and (#p0 == null or authentication.principal.userId == #p0))")
    public ResponseEntity<List<com.krusty.crab.dto.generated.Review>> getAllReviews(Integer clientId, Integer orderId) {
        log.info("Getting reviews, clientId: {}, orderId: {}", clientId, orderId);
        UserPrincipal user = SecurityContext.getCurrentUser();
        if ("CLIENT".equals(user.getUserType())) {
            if (clientId == null) {
                clientId = user.getUserId();
            } else if (!clientId.equals(user.getUserId())) {
                throw new AccessDeniedException("Access denied");
            }
        } else if (!"EMPLOYEE".equals(user.getUserType())) {
            throw new AccessDeniedException("Access denied");
        }

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
    @PreAuthorize("hasRole('Manager') or hasRole('CLIENT')")
    public ResponseEntity<com.krusty.crab.dto.generated.Review> getReviewById(Integer reviewId) {
        log.info("Getting review by ID: {}", reviewId);
        Review review = reviewService.getReviewById(reviewId);
        assertReviewAccess(review);
        com.krusty.crab.dto.generated.Review dto = reviewMapper.toDto(review);
        return ResponseEntity.ok(dto);
    }

    private void assertReviewAccess(Review review) {
        UserPrincipal user = SecurityContext.getCurrentUser();
        if ("CLIENT".equals(user.getUserType())) {
            Integer reviewClientId =
                    review.getClient() != null ? review.getClient().getId() : null;
            if (reviewClientId == null || !reviewClientId.equals(user.getUserId())) {
                throw new AccessDeniedException("Access denied");
            }
            return;
        }
        if ("EMPLOYEE".equals(user.getUserType())) {
            return;
        }
        throw new AccessDeniedException("Access denied");
    }

    private void assertOrderReviewable(Order order) {
        String status = order.getStatus() != null ? order.getStatus().getValue() : null;
        boolean finished =
                status != null && ("delivered".equalsIgnoreCase(status) || "completed".equalsIgnoreCase(status));
        if (!finished) {
            throw new ValidationException("Review can only be created after the order is finished");
        }
        if (order.getPayment() == null
                || !Boolean.TRUE.equals(order.getPayment().getSuccess())) {
            throw new ValidationException("Only paid orders can be reviewed");
        }
    }
}
