package com.example.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/transaction-completed")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void sendTransactionCompleted(@Valid @RequestBody TransactionCompletedRequest request) {
        log.info("Received transaction completed notification request: transactionRef={}, toEmail={}", 
                request.transactionRef(), request.toEmail());
        
        try {
            notificationService.sendTransactionCompleted(
                    request.toEmail(),
                    request.transactionRef(),
                    request.fromAccount(),
                    request.toAccount(),
                    request.amount()
            );
            log.info("Transaction completed notification sent successfully: transactionRef={}, toEmail={}", 
                    request.transactionRef(), request.toEmail());
        } catch (Exception ex) {
            log.error("Failed to send transaction completed notification: transactionRef={}, toEmail={}", 
                    request.transactionRef(), request.toEmail(), ex);
            throw ex;
        }
    }

    public record TransactionCompletedRequest(
            @NotBlank @Email String toEmail,
            @NotBlank String transactionRef,
            @NotBlank String fromAccount,
            @NotBlank String toAccount,
            @NotBlank String amount
    ) {
    }
}

