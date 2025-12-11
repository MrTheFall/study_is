package com.krusty.crab.controller;

import com.krusty.crab.security.UserPrincipal;
import com.krusty.crab.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login/client")
    public ResponseEntity<LoginResponse> loginClient(@RequestBody ClientLoginRequest request) {
        String token = authService.loginClient(request.getEmail(), request.getPassword());
        return ResponseEntity.ok(new LoginResponse(token, "CLIENT"));
    }

    @PostMapping("/login/employee")
    public ResponseEntity<LoginResponse> loginEmployee(@RequestBody EmployeeLoginRequest request) {
        String token = authService.loginEmployee(request.getLogin(), request.getPassword());
        return ResponseEntity.ok(new LoginResponse(token, "EMPLOYEE"));
    }

    @GetMapping("/me")
    public ResponseEntity<UserInfoResponse> getCurrentUser(HttpServletRequest request) {
        UserPrincipal userPrincipal = (UserPrincipal) request.getAttribute("userPrincipal");
        if (userPrincipal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UserInfoResponse response = new UserInfoResponse();
        response.setUserId(userPrincipal.getUserId());
        response.setUsername(userPrincipal.getUsername());
        response.setUserType(userPrincipal.getUserType());
        response.setRole(userPrincipal.getRole());

        return ResponseEntity.ok(response);
    }

    @Data
    public static class ClientLoginRequest {
        private String email;
        private String password;
    }

    @Data
    public static class EmployeeLoginRequest {
        private String login;
        private String password;
    }

    @Data
    public static class LoginResponse {
        private String token;
        private String userType;

        public LoginResponse(String token, String userType) {
            this.token = token;
            this.userType = userType;
        }
    }

    @Data
    public static class UserInfoResponse {
        private Integer userId;
        private String username;
        private String userType;
        private String role;
    }
}

