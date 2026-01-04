package com.krusty.crab.controller;

import com.krusty.crab.dto.bank.BankPaymentInitRequest;
import com.krusty.crab.dto.bank.BankPaymentInitResponse;
import com.krusty.crab.dto.bank.BankPaymentResultPayload;
import com.krusty.crab.entity.BankPaymentSession;
import com.krusty.crab.entity.enums.BankPaymentStatus;
import com.krusty.crab.service.BankNotificationService;
import com.krusty.crab.service.BankSignatureService;
import com.krusty.crab.service.BankSimulatorService;
import com.krusty.crab.util.BankPayloadUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequiredArgsConstructor
@Slf4j
public class BankSimulatorController {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final BankSimulatorService bankSimulatorService;
    private final BankSignatureService bankSignatureService;
    private final BankNotificationService bankNotificationService;

    @PostMapping("/bank/api/payments")
    public ResponseEntity<BankPaymentInitResponse> createPayment(
            @RequestHeader("X-Signature") String signature,
            @RequestHeader("X-Timestamp") String timestamp,
            @RequestHeader("X-Nonce") String nonce,
            @RequestHeader(name = "X-External-Base-Url", required = false) String externalBaseUrl,
            @RequestBody BankPaymentInitRequest request) {
        String baseUrl = (externalBaseUrl != null && !externalBaseUrl.isBlank())
                ? externalBaseUrl
                : ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        BankPaymentInitResponse response =
                bankSimulatorService.initiatePayment(request, signature, timestamp, nonce, baseUrl);
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/bank/3ds/{transactionId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> showAcs(@PathVariable UUID transactionId) {
        BankPaymentSession session = bankSimulatorService.getSession(transactionId);
        String html = renderAcsPage(session, null);
        return ResponseEntity.ok(html);
    }

    @PostMapping(
            value = "/bank/3ds/{transactionId}",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> submitAcs(
            @PathVariable UUID transactionId,
            @RequestParam(name = "otp", required = false) String otp,
            @RequestParam(name = "action", required = false) String action) {
        BankPaymentSession session = bankSimulatorService.getSession(transactionId);

        if (bankSimulatorService.isExpired(session)) {
            bankSimulatorService.updateStatus(session, BankPaymentStatus.FAILED, "expired");
            bankNotificationService.notifyMerchant(session, BankPaymentStatus.FAILED);
            return buildReturnRedirect(session, BankPaymentStatus.FAILED, "Срок действия подтверждения истёк");
        }

        if ("cancel".equalsIgnoreCase(action)) {
            bankSimulatorService.updateStatus(session, BankPaymentStatus.CANCELLED, "cancelled");
            bankNotificationService.notifyMerchant(session, BankPaymentStatus.CANCELLED);
            return buildReturnRedirect(session, BankPaymentStatus.CANCELLED, "Платеж отменен клиентом");
        }

        if (otp == null || !otp.trim().equals(session.getOtpCode())) {
            String html = renderAcsPage(session, "Неверный код. Проверьте SMS и повторите попытку.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(html);
        }

        BankPaymentStatus finalStatus =
                session.getForceFailure() ? BankPaymentStatus.DECLINED : BankPaymentStatus.APPROVED;

        bankSimulatorService.updateStatus(
                session, finalStatus, finalStatus == BankPaymentStatus.DECLINED ? "declined" : null);
        bankNotificationService.notifyMerchant(session, finalStatus);

        return buildReturnRedirect(session, finalStatus, null);
    }

    private ResponseEntity<String> buildReturnRedirect(
            BankPaymentSession session, BankPaymentStatus status, String message) {
        long timestamp = Instant.now().getEpochSecond();
        String nonce = UUID.randomUUID().toString().replace("-", "");

        BankPaymentResultPayload unsigned = new BankPaymentResultPayload(
                session.getId(), session.getOrderId(), session.getAmount(), status.getValue(), timestamp, nonce, null);

        String signature = bankSignatureService.sign(BankPayloadUtil.buildSignaturePayload(unsigned));
        BankPaymentResultPayload payload = new BankPaymentResultPayload(
                unsigned.transactionId(),
                unsigned.orderId(),
                unsigned.amount(),
                unsigned.status(),
                unsigned.timestamp(),
                unsigned.nonce(),
                signature);

        String html = renderAutoPostForm(session.getReturnUrl(), payload, message);
        return ResponseEntity.ok(html);
    }

    private String renderAcsPage(BankPaymentSession session, String error) {
        String amount = session.getAmount() != null
                ? session.getAmount().stripTrailingZeros().toPlainString()
                : "0";
        String last4 = session.getCardLast4() != null ? session.getCardLast4() : "0000";
        String createdAt =
                session.getCreatedAt() != null ? session.getCreatedAt().format(DATE_FORMAT) : "";
        String expiresAt =
                session.getExpiresAt() != null ? session.getExpiresAt().format(DATE_FORMAT) : "";

        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html lang=\"ru\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>3-D Secure подтверждение</title>")
                .append("<style>")
                .append("body{font-family:Arial,sans-serif;background:#f4f6fb;margin:0;padding:24px;color:#1f2937;}")
                .append(".card{max-width:520px;margin:40px auto;background:#fff;border-radius:16px;padding:24px;")
                .append("box-shadow:0 12px 24px rgba(0,0,0,0.08);}")
                .append(".title{font-size:20px;font-weight:700;margin-bottom:8px;}")
                .append(".subtitle{color:#6b7280;font-size:14px;margin-bottom:16px;}")
                .append(".row{display:flex;justify-content:space-between;font-size:14px;margin:8px 0;}")
                .append(".label{color:#6b7280;}")
                .append(".otp{margin:16px 0;display:flex;gap:12px;}")
                .append("input{flex:1;padding:12px;border:1px solid #d1d5db;border-radius:10px;font-size:16px;}")
                .append("button{padding:10px 16px;border:none;border-radius:10px;font-weight:600;cursor:pointer;}")
                .append(".primary{background:#2563eb;color:#fff;}")
                .append(".ghost{background:#e5e7eb;color:#111827;}")
                .append(".error{background:#fee2e2;color:#b91c1c;padding:10px 12px;border-radius:8px;")
                .append("font-size:13px;margin-bottom:12px;}")
                .append(".hint{font-size:12px;color:#6b7280;margin-top:6px;}")
                .append("</style></head><body><div class=\"card\">")
                .append("<div class=\"title\">Подтвердите оплату 3-D Secure</div>")
                .append("<div class=\"subtitle\">Банк-эмитент карты запрашивает одноразовый код</div>");

        if (error != null) {
            sb.append("<div class=\"error\">").append(error).append("</div>");
        }

        sb.append("<div class=\"row\"><span class=\"label\">Мерчант</span><span>Krusty Krab</span></div>")
                .append("<div class=\"row\"><span class=\"label\">Заказ</span><span>#")
                .append(session.getOrderId())
                .append("</span></div>")
                .append("<div class=\"row\"><span class=\"label\">Сумма</span><span>")
                .append(amount)
                .append(" USD</span></div>")
                .append("<div class=\"row\"><span class=\"label\">Карта</span><span>**** **** **** ")
                .append(last4)
                .append("</span></div>")
                .append("<div class=\"row\"><span class=\"label\">Создан</span><span>")
                .append(createdAt)
                .append("</span></div>")
                .append("<div class=\"row\"><span class=\"label\">Действует до</span><span>")
                .append(expiresAt)
                .append("</span></div>")
                .append("<form method=\"post\" class=\"otp\" action=\"\">")
                .append("<input name=\"otp\" placeholder=\"Код из SMS\" autocomplete=\"one-time-code\"/>")
                .append("<button class=\"primary\" type=\"submit\" name=\"action\" value=\"approve\">")
                .append("Подтвердить</button>")
                .append("</form>")
                .append("<form method=\"post\" action=\"\" style=\"margin-top:8px;\">")
                .append("<button class=\"ghost\" type=\"submit\" name=\"action\" value=\"cancel\">Отменить</button>")
                .append("</form>")
                .append("<div class=\"hint\">Тестовый код: ")
                .append(session.getOtpCode())
                .append("</div>")
                .append("</div></body></html>");

        return sb.toString();
    }

    private String renderAutoPostForm(String returnUrl, BankPaymentResultPayload payload, String message) {
        String safeMessage = message != null ? message : "Переадресация на страницу магазина...";
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html lang=\"ru\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>Возврат в магазин</title></head><body>")
                .append("<p>")
                .append(safeMessage)
                .append("</p>")
                .append("<form id=\"returnForm\" method=\"post\" action=\"")
                .append(returnUrl)
                .append("\">")
                .append("<input type=\"hidden\" name=\"transactionId\" value=\"")
                .append(payload.transactionId())
                .append("\"/>")
                .append("<input type=\"hidden\" name=\"orderId\" value=\"")
                .append(payload.orderId())
                .append("\"/>")
                .append("<input type=\"hidden\" name=\"amount\" value=\"")
                .append(payload.amount().stripTrailingZeros().toPlainString())
                .append("\"/>")
                .append("<input type=\"hidden\" name=\"status\" value=\"")
                .append(payload.status())
                .append("\"/>")
                .append("<input type=\"hidden\" name=\"timestamp\" value=\"")
                .append(payload.timestamp())
                .append("\"/>")
                .append("<input type=\"hidden\" name=\"nonce\" value=\"")
                .append(payload.nonce())
                .append("\"/>")
                .append("<input type=\"hidden\" name=\"signature\" value=\"")
                .append(payload.signature())
                .append("\"/>")
                .append("</form>")
                .append("<script>document.getElementById('returnForm').submit();</script>")
                .append("</body></html>");
        return sb.toString();
    }
}
