package com.krusty.crab.entity;

import com.krusty.crab.entity.enums.OrderStatus;
import com.krusty.crab.entity.enums.OrderType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_orders_client"))
    private Client client;
    
    @Column(name = "type", nullable = false, length = 32)
    @Convert(converter = OrderType.OrderTypeConverter.class)
    private OrderType type;
    
    @Column(name = "status", nullable = false, length = 32)
    @Convert(converter = OrderStatus.OrderStatusConverter.class)
    private OrderStatus status;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "courier_id",
        foreignKey = @ForeignKey(name = "fk_orders_courier"))
    private Courier courier;
    
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
    
    @Column(name = "delivery_address", columnDefinition = "text")
    private String deliveryAddress;
    
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();
    
    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private Payment payment;
    
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Review> reviews = new ArrayList<>();
}

