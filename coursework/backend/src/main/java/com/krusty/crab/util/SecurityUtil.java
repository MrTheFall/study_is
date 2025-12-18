package com.krusty.crab.util;

import com.krusty.crab.security.UserPrincipal;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtil {

    public static UserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("User not authenticated");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            return userPrincipal;
        }

        throw new AuthenticationCredentialsNotFoundException("User not authenticated");
    }
    
    public static void requireRole(String requiredRole) {
        getCurrentUser();
        if (!hasRole(requiredRole)) {
            throw new AccessDeniedException("Access denied. Required role: " + requiredRole);
        }
    }
    
    public static boolean hasRole(String role) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null) {
                return false;
            }
            return authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_" + role));
        } catch (Exception e) {
            return false;
        }
    }
    
    public static boolean isManager() {
        return hasRole("Manager");
    }
}
