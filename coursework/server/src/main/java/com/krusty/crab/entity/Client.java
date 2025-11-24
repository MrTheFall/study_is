package com.krusty.crab.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "clients",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_clients_email", columnNames = "email"),
        @UniqueConstraint(name = "uq_clients_phone", columnNames = "phone")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(name = "name", nullable = false, length = 255)
    private String name;
    
    @Column(name = "phone", nullable = false, length = 64)
    private String phone;
    
    @Column(name = "email", nullable = false, length = 255)
    private String email;
    
    @Column(name = "password_hash", nullable = false, columnDefinition = "text")
    private String passwordHash;
    
    @Column(name = "default_address", columnDefinition = "text")
    private String defaultAddress;
    
    @Column(name = "registered_at", nullable = false)
    private LocalDate registeredAt;
    
    @Column(name = "loyalty_points", nullable = false)
    @Builder.Default
    private Integer loyaltyPoints = 0;
    
    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Order> orders = new ArrayList<>();
    
    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Review> reviews = new ArrayList<>();
}

