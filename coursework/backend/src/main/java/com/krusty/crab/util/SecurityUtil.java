package com.krusty.crab.util;

import com.krusty.crab.exception.ValidationException;
import com.krusty.crab.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class SecurityUtil {
    
    private static final String USER_PRINCIPAL_ATTRIBUTE = "userPrincipal";
    
    public static HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new ValidationException("No active request found");
        }
        return attributes.getRequest();
    }
    
    public static UserPrincipal getCurrentUser() {
        HttpServletRequest request = getCurrentRequest();
        UserPrincipal userPrincipal = (UserPrincipal) request.getAttribute(USER_PRINCIPAL_ATTRIBUTE);
        if (userPrincipal == null) {
            throw new ValidationException("User not authenticated");
        }
        return userPrincipal;
    }
    
    public static void requireRole(String requiredRole) {
        UserPrincipal userPrincipal = getCurrentUser();
        
        if (!"EMPLOYEE".equals(userPrincipal.getUserType())) {
            throw new ValidationException("Only employees can perform this action");
        }
        
        if (!requiredRole.equals(userPrincipal.getRole())) {
            throw new ValidationException("Access denied. Required role: " + requiredRole);
        }
    }
    
    public static boolean hasRole(String role) {
        try {
            UserPrincipal userPrincipal = getCurrentUser();
            return "EMPLOYEE".equals(userPrincipal.getUserType()) 
                && role.equals(userPrincipal.getRole());
        } catch (Exception e) {
            return false;
        }
    }
    
    public static boolean isManager() {
        return hasRole("Manager");
    }
}

