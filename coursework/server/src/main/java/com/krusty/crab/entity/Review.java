package com.krusty.crab.entity;

import com.krusty.crab.entity.enums.Rating;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "reviews",
    uniqueConstraints = @UniqueConstraint(name = "uq_reviews_order_client", columnNames = {"order_id", "client_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_reviews_order"))
    private Order order;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_reviews_client"))
    private Client client;
    
    @Column(name = "rating", nullable = false)
    @Convert(converter = Rating.RatingConverter.class)
    private Rating rating;
    
    @Column(name = "comment", columnDefinition = "text")
    private String comment;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

