package com.example.orgmanager.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "coordinates")
public class Coordinates {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Min(-523) // > -524
    @Column(nullable = false)
    private int x;

    @NotNull
    @Max(476)
    @Column(nullable = false)
    private Float y;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public int getX() { return x; }
    public void setX(int x) { this.x = x; }
    public Float getY() { return y; }
    public void setY(Float y) { this.y = y; }
}

