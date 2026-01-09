package com.example.transaction;

import com.example.transaction.client.AccountServiceClient;
import com.example.transaction.common.InsufficientBalanceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = TransactionServiceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TransactionControllerIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(TransactionControllerIntegrationTest.class);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionRepository transactionRepository;

    @MockBean
    private AccountServiceClient accountServiceClient;

    private static final String FROM_ACCOUNT = "ACC1001";
    private static final String TO_ACCOUNT = "ACC1002";
    private static final String IDEMPOTENCY_KEY = "IDEMPOTENT-KEY-123";

    @BeforeEach
    void setUp() {
        log.info("Resetting database state for TransactionControllerIntegrationTest");
        transactionRepository.deleteAll();
        
        // Setup default successful account service responses
        doNothing().when(accountServiceClient).lockFunds(anyString(), any(BigDecimal.class), anyString(), any(OffsetDateTime.class));
        doNothing().when(accountServiceClient).credit(anyString(), any(BigDecimal.class));
        doNothing().when(accountServiceClient).releaseLock(anyString());
        doNothing().when(accountServiceClient).unlockFunds(anyString(), any(BigDecimal.class));
    }

    @Test
    void testInitiateFundTransfer() throws Exception {
        TransactionController.TransferRequestBody request = new TransactionController.TransferRequestBody(
                FROM_ACCOUNT,
                TO_ACCOUNT,
                new BigDecimal("2000.00"),
                "INR",
                null
        );

        log.info("Initiating fund transfer from {} to {} for {}", FROM_ACCOUNT, TO_ACCOUNT, new BigDecimal("2000.00"));

        mockMvc.perform(post("/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.transactionRef").exists())
                .andExpect(jsonPath("$.fromAccount").value(FROM_ACCOUNT))
                .andExpect(jsonPath("$.toAccount").value(TO_ACCOUNT))
                .andExpect(jsonPath("$.amount").value(2000.00))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.status").value("INITIATED"));

        // Wait for async processing
        Thread.sleep(500);

        // Verify transaction was processed
        Transaction transaction = transactionRepository.findAll().stream()
                .filter(tx -> tx.getFromAccount().equals(FROM_ACCOUNT))
                .findFirst()
                .orElseThrow();
        
        assertThat(transaction.getStatus()).isIn(Transaction.Status.SUCCESS, Transaction.Status.PROCESSING);
        
        // Verify account service was called
        verify(accountServiceClient, atLeastOnce()).lockFunds(eq(FROM_ACCOUNT), any(BigDecimal.class), anyString(), any(OffsetDateTime.class));
        verify(accountServiceClient, atLeastOnce()).credit(eq(TO_ACCOUNT), any(BigDecimal.class));
    }

    @Test
    void testGetTransactionNotFound() throws Exception {
        mockMvc.perform(get("/transactions/{transactionId}", "NON-EXISTENT-REF"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testReverseTransaction() throws Exception {
        // First create a successful transaction
        TransactionController.TransferRequestBody request = new TransactionController.TransferRequestBody(
                FROM_ACCOUNT,
                TO_ACCOUNT,
                new BigDecimal("2500.00"),
                "INR",
                null
        );

        String createResponse = mockMvc.perform(post("/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Transaction createdTx = objectMapper.readValue(createResponse, Transaction.class);
        String transactionRef = createdTx.getTransactionRef();

        // Wait for transaction to complete
        Thread.sleep(1000);
        
        // Update transaction status to SUCCESS for reversal test
        Transaction tx = transactionRepository.findByTransactionRef(transactionRef).orElseThrow();
        tx.setStatus(Transaction.Status.SUCCESS);
        transactionRepository.save(tx);

        // Reverse the transaction using transactionId
        mockMvc.perform(post("/transactions/{transactionId}/reverse", transactionRef))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.transactionRef").value(transactionRef))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").exists());

        // Verify account service was called for reversal
        verify(accountServiceClient, atLeastOnce()).lockFunds(eq(TO_ACCOUNT), any(BigDecimal.class), anyString(), any(OffsetDateTime.class));
        verify(accountServiceClient, atLeastOnce()).credit(eq(FROM_ACCOUNT), any(BigDecimal.class));
        verify(accountServiceClient, atLeastOnce()).unlockFunds(eq(TO_ACCOUNT), any(BigDecimal.class));
    }

    @Test
    void testTransferWithInsufficientBalance() throws Exception {
        // Mock insufficient balance exception
        doThrow(new InsufficientBalanceException("Insufficient balance"))
                .when(accountServiceClient).lockFunds(anyString(), any(BigDecimal.class), anyString(), any(OffsetDateTime.class));

        TransactionController.TransferRequestBody request = new TransactionController.TransferRequestBody(
                FROM_ACCOUNT,
                TO_ACCOUNT,
                new BigDecimal("50000.00"), // More than available
                "INR",
                null
        );

        mockMvc.perform(post("/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted()); // Transaction is accepted but will fail

        // Wait a bit for processing
        Thread.sleep(500);

        // Verify transaction failed
        Transaction failedTx = transactionRepository.findAll().stream()
                .filter(tx -> tx.getFromAccount().equals(FROM_ACCOUNT))
                .findFirst()
                .orElseThrow();
        
        assertThat(failedTx.getStatus()).isEqualTo(Transaction.Status.FAILED);
        assertThat(failedTx.getFailureReason()).contains("Insufficient balance");
    }

    @Test
    void testReverseTransactionNotFound() throws Exception {
        mockMvc.perform(post("/transactions/{transactionId}/reverse", "NON-EXISTENT-REF"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testReverseTransactionInvalidState() throws Exception {
        // Create a transaction that will fail
        doThrow(new InsufficientBalanceException("Insufficient balance"))
                .when(accountServiceClient).lockFunds(anyString(), any(BigDecimal.class), anyString(), any(OffsetDateTime.class));

        TransactionController.TransferRequestBody request = new TransactionController.TransferRequestBody(
                FROM_ACCOUNT,
                TO_ACCOUNT,
                new BigDecimal("50000.00"), // Insufficient balance
                "INR",
                null
        );

        mockMvc.perform(post("/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        // Wait for processing
        Thread.sleep(500);

        Transaction failedTx = transactionRepository.findAll().stream()
                .filter(tx -> tx.getFromAccount().equals(FROM_ACCOUNT))
                .findFirst()
                .orElseThrow();
        
        assertThat(failedTx.getStatus()).isEqualTo(Transaction.Status.FAILED);

        // Try to reverse a failed transaction (should fail)
        mockMvc.perform(post("/transactions/{transactionId}/reverse", failedTx.getTransactionRef()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATE"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testTransferWithSameSourceAndDestination() throws Exception {
        TransactionController.TransferRequestBody request = new TransactionController.TransferRequestBody(
                FROM_ACCOUNT,
                FROM_ACCOUNT, // Same account
                new BigDecimal("1000.00"),
                "INR",
                null
        );

        // Validation should happen before accepting, so expect 400
        mockMvc.perform(post("/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void testTransferWithZeroAmount() throws Exception {
        TransactionController.TransferRequestBody request = new TransactionController.TransferRequestBody(
                FROM_ACCOUNT,
                TO_ACCOUNT,
                BigDecimal.ZERO,
                "INR",
                null
        );

        // Validation should happen before accepting, so expect 400
        mockMvc.perform(post("/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void testTransferWithNegativeAmount() throws Exception {
        TransactionController.TransferRequestBody request = new TransactionController.TransferRequestBody(
                FROM_ACCOUNT,
                TO_ACCOUNT,
                new BigDecimal("-100.00"),
                "INR",
                null
        );

        // Validation should happen before accepting, so expect 400
        mockMvc.perform(post("/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void testTransferValidationErrors() throws Exception {
        // Test with missing required fields
        String invalidJson = "{\"fromAccount\":\"\",\"toAccount\":\"ACC1002\",\"amount\":1000.00}";

        mockMvc.perform(post("/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").exists());
    }
}

