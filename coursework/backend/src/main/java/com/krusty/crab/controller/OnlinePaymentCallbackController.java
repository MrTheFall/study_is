package com.krusty.crab.controller;

import com.krusty.crab.dto.bank.BankPaymentResultPayload;
import com.krusty.crab.entity.enums.OnlinePaymentStatus;
import com.krusty.crab.exception.ValidationException;
import com.krusty.crab.service.OnlinePaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class OnlinePaymentCallbackController {

    private final OnlinePaymentService onlinePaymentService;

    @Value("${frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @PostMapping(
        value = "/payments/online/return",
        consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
        produces = MediaType.TEXT_HTML_VALUE
    )
    public ResponseEntity<String> handleReturn(
        @RequestParam(name = "transactionId") String transactionId,
        @RequestParam(name = "orderId") String orderId,
        @RequestParam(name = "amount") String amount,
        @RequestParam(name = "status") String status,
        @RequestParam(name = "timestamp") String timestamp,
        @RequestParam(name = "nonce") String nonce,
        @RequestParam(name = "signature") String signature,
        HttpServletRequest request
    ) {
        OnlinePaymentStatus finalStatus = OnlinePaymentStatus.FAILED;
        String errorMessage = null;

        try {
            BankPaymentResultPayload payload = new BankPaymentResultPayload(
                UUID.fromString(transactionId),
                Integer.parseInt(orderId),
                parseAmount(amount),
                status,
                Long.parseLong(timestamp),
                nonce,
                signature
            );
            finalStatus = onlinePaymentService.finalizeFromBank(payload);
        } catch (Exception e) {
            log.warn("Failed to finalize online payment: {}", e.getMessage());
            errorMessage = e.getMessage();
        }

        String redirectUrl = buildFrontendRedirect(orderId, finalStatus, errorMessage, request);
        return ResponseEntity.ok(renderRedirectPage(redirectUrl));
    }

    @PostMapping(value = "/payments/online/notify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> handleNotify(@RequestBody BankPaymentResultPayload payload) {
        onlinePaymentService.finalizeFromBank(payload);
        return ResponseEntity.ok().build();
    }

    private BigDecimal parseAmount(String amount) {
        try {
            return new BigDecimal(amount);
        } catch (NumberFormatException e) {
            throw new ValidationException("Invalid amount");
        }
    }

    private String buildFrontendRedirect(
        String orderId,
        OnlinePaymentStatus status,
        String message,
        HttpServletRequest request
    ) {
        String baseUrl = resolveFrontendBaseUrl(request);
        String statusParam = mapStatus(status);
        StringBuilder sb = new StringBuilder(baseUrl)
            .append("/payment/result?orderId=").append(urlEncode(orderId))
            .append("&status=").append(urlEncode(statusParam));

        if (message != null && !message.isBlank()) {
            sb.append("&message=").append(urlEncode(message));
        }

        return sb.toString();
    }

    private String resolveFrontendBaseUrl(HttpServletRequest request) {
        String host = request != null ? request.getServerName() : null;
        String[] urls = frontendUrl.split(",");
        Optional<String> first = Optional.empty();

        for (String url : urls) {
            String trimmed = url.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (first.isEmpty()) {
                first = Optional.of(trimmed);
            }
            if (host == null) {
                continue;
            }
            try {
                URI uri = URI.create(trimmed);
                if (uri.getHost() != null && uri.getHost().equalsIgnoreCase(host)) {
                    return trimmed;
                }
            } catch (IllegalArgumentException e) {
                log.warn("Invalid frontend url: {}", trimmed);
            }
        }

        if (first.isPresent()) {
            return first.get();
        }

        String scheme = request != null ? request.getScheme() : "http";
        String resolvedHost = host != null ? host : "localhost";
        int port = request != null ? request.getServerPort() : 5173;
        return scheme + "://" + resolvedHost + ":" + port;
    }

    private String mapStatus(OnlinePaymentStatus status) {
        return switch (status) {
            case SUCCEEDED -> "success";
            case CANCELLED -> "cancelled";
            case FAILED -> "failed";
            default -> "pending";
        };
    }

    private String renderRedirectPage(String redirectUrl) {
        return """
            <!doctype html>
            <html lang="ru">
              <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Возврат в магазин</title>
              </head>
              <body>
                <p>Возвращаем вас в приложение...</p>
                <script>
                  window.location.href = "%s";
                </script>
              </body>
            </html>
            """.formatted(redirectUrl);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
