package com.example.transaction.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Client for communicating with Notification Service via REST API.
 */
@Component
public class NotificationServiceClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceClient.class);

    private final WebClient webClient;

    public NotificationServiceClient(@Value("${webclient.notification-service-base-url:http://notification-service:8083}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        log.info("NotificationServiceClient initialized with base URL: {}", baseUrl);
    }

    /**
     * Sends a transaction completed notification to the notification service.
     * This is a fire-and-forget operation - failures are logged but don't affect the transaction.
     *
     * @param toEmail Recipient email address
     * @param transactionRef Transaction reference ID
     * @param fromAccount Source account number
     * @param toAccount Destination account number
     * @param amount Transaction amount
     */
    public void sendTransactionCompleted(String toEmail, String transactionRef, String fromAccount,
                                         String toAccount, String amount) {
        log.debug("Calling notification service to send transaction completed email: transactionRef={}, toEmail={}", 
                transactionRef, toEmail);

        TransactionCompletedRequest request = new TransactionCompletedRequest(
                toEmail, transactionRef, fromAccount, toAccount, amount
        );

        try {
            webClient.post()
                    .uri("/notifications/transaction-completed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
            log.info("Transaction completed notification sent successfully via notification service: transactionRef={}, toEmail={}", 
                    transactionRef, toEmail);
        } catch (WebClientResponseException ex) {
            log.warn("Failed to send notification via notification service: transactionRef={}, status={}, message={}", 
                    transactionRef, ex.getStatusCode(), ex.getMessage());
            // Don't throw - notification failure shouldn't fail the transaction
        } catch (Exception ex) {
            log.error("Unexpected error sending notification via notification service: transactionRef={}, toEmail={}", 
                    transactionRef, toEmail, ex);
            // Don't throw - notification failure shouldn't fail the transaction
        }
    }

    /**
     * Request DTO matching Notification Service API.
     */
    public record TransactionCompletedRequest(
            String toEmail,
            String transactionRef,
            String fromAccount,
            String toAccount,
            String amount
    ) {
    }
}

