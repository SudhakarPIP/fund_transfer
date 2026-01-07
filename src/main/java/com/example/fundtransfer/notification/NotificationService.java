package com.example.fundtransfer.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;

    public NotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendTransactionCompleted(String toEmail, String transactionRef, String fromAccount,
                                         String toAccount, String amount) {
        String subject = "Transaction Completed: " + transactionRef;
        String body = "Your transaction " + transactionRef + " from " + fromAccount +
                " to " + toAccount + " for amount " + amount + " has completed successfully.";

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Notification email sent for transaction {} to {}", transactionRef, toEmail);
        } catch (Exception ex) {
            // For demo purposes, just log the error – saga already completed
            // This will happen if SMTP is not properly configured, but the bean exists
            log.warn("Failed to send notification email for transaction {} (SMTP may not be configured): {}",
                    transactionRef, ex.getMessage());
            log.info("Transaction notification logged: Transaction {} from {} to {} for amount {} - Email: {}",
                    transactionRef, fromAccount, toAccount, amount, toEmail);
        }
    }
}


