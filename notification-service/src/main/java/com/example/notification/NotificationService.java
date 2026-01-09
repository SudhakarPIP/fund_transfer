package com.example.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Service for sending email notifications.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;

    public NotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends an email notification when a transaction is completed.
     *
     * @param toEmail Recipient email address
     * @param transactionRef Transaction reference ID
     * @param fromAccount Source account number
     * @param toAccount Destination account number
     * @param amount Transaction amount
     */
    public void sendTransactionCompleted(String toEmail, String transactionRef, String fromAccount,
                                         String toAccount, String amount) {
        log.debug("Preparing transaction completed email: transactionRef={}, toEmail={}, fromAccount={}, toAccount={}, amount={}", 
                transactionRef, toEmail, fromAccount, toAccount, amount);

        String subject = "Transaction Completed: " + transactionRef;
        String body = buildEmailBody(transactionRef, fromAccount, toAccount, amount);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            message.setFrom(getFromEmail());
            
            mailSender.send(message);
            log.info("Transaction completed email sent successfully: transactionRef={}, toEmail={}", 
                    transactionRef, toEmail);
        } catch (Exception ex) {
            log.error("Failed to send transaction completed email: transactionRef={}, toEmail={}, error={}", 
                    transactionRef, toEmail, ex.getMessage(), ex);
            // Log the notification even if email fails (for audit purposes)
            log.info("Transaction notification logged (email failed): Transaction {} from {} to {} for amount {} - Email: {}", 
                    transactionRef, fromAccount, toAccount, amount, toEmail);
            throw new NotificationException("Failed to send email notification: " + ex.getMessage(), ex);
        }
    }

    private String buildEmailBody(String transactionRef, String fromAccount, String toAccount, String amount) {
        return String.format(
                "Dear Customer,\n\n" +
                "Your transaction has been completed successfully.\n\n" +
                "Transaction Details:\n" +
                "Transaction Reference: %s\n" +
                "From Account: %s\n" +
                "To Account: %s\n" +
                "Amount: %s\n\n" +
                "Thank you for using our service.\n\n" +
                "Best Regards,\n" +
                "Fund Transfer System",
                transactionRef, fromAccount, toAccount, amount
        );
    }

    private String getFromEmail() {
        // This should be configured via application properties
        // For now, use a default
        return "noreply@fundtransfer.com";
    }
}

