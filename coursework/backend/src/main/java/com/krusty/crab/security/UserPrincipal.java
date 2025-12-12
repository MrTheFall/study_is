package com.krusty.crab.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserPrincipal {
    private Integer userId;
    private String username;
    private String userType; // "CLIENT" or "EMPLOYEE"
    private String role; // Role name for employees, null for clients
}


