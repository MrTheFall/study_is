package com.krusty.crab.controller;

import com.krusty.crab.api.AuthApi;
import com.krusty.crab.dto.generated.GetCurrentUser200Response;
import com.krusty.crab.dto.generated.LoginClient200Response;
import com.krusty.crab.dto.generated.LoginClientRequest;
import com.krusty.crab.dto.generated.LoginEmployee200Response;
import com.krusty.crab.dto.generated.LoginEmployeeRequest;
import com.krusty.crab.service.AuthService;
import com.krusty.crab.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class AuthController implements AuthApi {

    private final AuthService authService;

    @Override
    public ResponseEntity<LoginClient200Response> loginClient(LoginClientRequest loginClientRequest) {
        log.info("Client login attempt for email: {}", loginClientRequest.getEmail());
        String token = authService.loginClient(loginClientRequest.getEmail(), loginClientRequest.getPassword());
        
        LoginClient200Response response = new LoginClient200Response();
        response.setToken(token);
        response.setUserType(LoginClient200Response.UserTypeEnum.CLIENT);
        
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<LoginEmployee200Response> loginEmployee(LoginEmployeeRequest loginEmployeeRequest) {
        log.info("Employee login attempt for login: {}", loginEmployeeRequest.getLogin());
        String token = authService.loginEmployee(loginEmployeeRequest.getLogin(), loginEmployeeRequest.getPassword());
        
        LoginEmployee200Response response = new LoginEmployee200Response();
        response.setToken(token);
        response.setUserType(LoginEmployee200Response.UserTypeEnum.EMPLOYEE);
        
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<GetCurrentUser200Response> getCurrentUser() {
        try {
            com.krusty.crab.security.UserPrincipal userPrincipal = SecurityUtil.getCurrentUser();
            
            GetCurrentUser200Response response = new GetCurrentUser200Response();
            response.setUserId(userPrincipal.getUserId());
            response.setUsername(userPrincipal.getUsername());
            response.setUserType(GetCurrentUser200Response.UserTypeEnum.fromValue(userPrincipal.getUserType()));
            response.setRole(userPrincipal.getRole());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Unauthorized access attempt to /auth/me");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}
