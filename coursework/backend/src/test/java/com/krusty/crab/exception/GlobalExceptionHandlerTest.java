package com.krusty.crab.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlesAuthenticationAndDomainExceptions() {
        assertError(
                handler.handleAuthenticationException(new BadCredentialsException("bad")),
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "bad");

        assertError(
                handler.handleAccessDeniedException(new AccessDeniedException("forbidden")),
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "forbidden");

        assertError(
                handler.handleEntityNotFound(new EntityNotFoundException("Client", 1)),
                HttpStatus.NOT_FOUND,
                "NOT_FOUND",
                "Client with id 1 not found");

        assertError(
                handler.handleDuplicateEntity(new DuplicateEntityException("Client", "email", "a@b")),
                HttpStatus.CONFLICT,
                "DUPLICATE_ENTITY",
                "Client with email 'a@b' already exists");

        assertError(
                handler.handleValidationException(new ValidationException("email", "bad")),
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Validation failed for field 'email': bad");

        assertError(
                handler.handlePasswordException(new PasswordException("pw")),
                HttpStatus.BAD_REQUEST,
                "PASSWORD_ERROR",
                "pw");

        assertError(
                handler.handleOrderException(new OrderException("order")),
                HttpStatus.BAD_REQUEST,
                "ORDER_ERROR",
                "order");

        assertError(
                handler.handlePaymentException(new PaymentException("payment")),
                HttpStatus.BAD_REQUEST,
                "PAYMENT_ERROR",
                "payment");

        assertError(
                handler.handleInventoryException(new InventoryException("inventory")),
                HttpStatus.BAD_REQUEST,
                "INVENTORY_ERROR",
                "inventory");

        assertError(
                handler.handleMenuException(new MenuException("menu")),
                HttpStatus.BAD_REQUEST,
                "MENU_ERROR",
                "menu");

        assertError(
                handler.handleEmployeeException(new EmployeeException("employee")),
                HttpStatus.BAD_REQUEST,
                "EMPLOYEE_ERROR",
                "employee");

        assertError(
                handler.handleShiftException(new ShiftException("shift")),
                HttpStatus.BAD_REQUEST,
                "SHIFT_ERROR",
                "shift");

        assertError(
                handler.handleReviewException(new ReviewException("review")),
                HttpStatus.BAD_REQUEST,
                "REVIEW_ERROR",
                "review");

        assertError(
                handler.handleIllegalArgument(new IllegalArgumentException("bad request")),
                HttpStatus.BAD_REQUEST,
                "BAD_REQUEST",
                "bad request");
    }

    @Test
    void handlesMethodArgumentMismatchAndRuntimeExceptions() {
        MethodArgumentTypeMismatchException mismatch = new MethodArgumentTypeMismatchException(
                "abc", Integer.class, "id", null, new IllegalArgumentException("bad"));

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleMethodArgumentTypeMismatch(mismatch);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("BAD_REQUEST");
        assertThat(response.getBody().getMessage())
                .contains("Invalid value 'abc'", "parameter 'id'", "Integer");

        assertError(
                handler.handleRuntimeException(new RuntimeException("boom")),
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "boom");
    }

    private void assertError(
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response,
            HttpStatus status,
            String code,
            String message) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(code);
        assertThat(response.getBody().getMessage()).isEqualTo(message);
    }
}
