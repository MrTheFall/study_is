package com.krusty.crab.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "couriers", uniqueConstraints = @UniqueConstraint(name = "uq_couriers_phone", columnNames = "phone"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Courier {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(name = "name", nullable = false, length = 255)
    private String name;
    
    @Column(name = "phone", nullable = false, length = 64)
    private String phone;
    
    @Column(name = "vehicle_info", length = 255)
    private String vehicleInfo;
    
    @OneToMany(mappedBy = "courier", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Order> orders = new ArrayList<>();
}

