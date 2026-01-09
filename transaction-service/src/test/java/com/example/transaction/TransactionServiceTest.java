package com.example.transaction;

import com.example.transaction.client.AccountServiceClient;
import com.example.transaction.common.InsufficientBalanceException;
import com.example.transaction.common.MessageSourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountServiceClient accountServiceClient;

    @Mock
    private MessageSourceService messageSourceService;

    @InjectMocks
    private TransactionService transactionService;

    private static final String FROM_ACCOUNT = "ACC-FROM-001";
    private static final String TO_ACCOUNT = "ACC-TO-001";
    private static final String IDEMPOTENCY_KEY = "IDEMPOTENT-KEY-123";

    @BeforeEach
    void setUp() {
        // Setup default message source responses with lenient stubbing to avoid unnecessary stubbing errors
        lenient().when(messageSourceService.getMessage(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return key; // Return key as default message
        });
        // Mock varargs version explicitly
        lenient().when(messageSourceService.getMessage(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object[] args = invocation.getArgument(1);
            if (args != null && args.length > 0) {
                return key + ": " + args[0];
            }
            return key;
        });
        // Mock String defaultMessage version
        lenient().when(messageSourceService.getMessage(anyString(), anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String defaultMsg = invocation.getArgument(1);
            return defaultMsg != null ? defaultMsg : key;
        });
        // Mock Object[] args, String defaultMessage version
        lenient().when(messageSourceService.getMessage(anyString(), any(Object[].class), anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object[] args = invocation.getArgument(1);
            String defaultMsg = invocation.getArgument(2);
            if (args != null && args.length > 0) {
                return key + ": " + args[0];
            }
            return defaultMsg != null ? defaultMsg : key;
        });
    }

    @Test
    void testStartTransfer_Success() {
        // Arrange
        TransactionService.TransferRequest request = new TransactionService.TransferRequest(
                FROM_ACCOUNT,
                TO_ACCOUNT,
                new BigDecimal("1000.00"),
                "INR",
                null
        );

        Transaction transaction = new Transaction();
        transaction.setTransactionRef(UUID.randomUUID().toString());
        transaction.setFromAccount(FROM_ACCOUNT);
        transaction.setToAccount(TO_ACCOUNT);
        transaction.setAmount(new BigDecimal("1000.00"));
        transaction.setStatus(Transaction.Status.INITIATED);

        when(transactionRepository.save(any(Transaction.class))).thenReturn(transaction);
        doNothing().when(accountServiceClient).lockFunds(anyString(), any(BigDecimal.class), anyString(), any(OffsetDateTime.class));
        doNothing().when(accountServiceClient).credit(anyString(), any(BigDecimal.class));
        doNothing().when(accountServiceClient).releaseLock(anyString());

        // Act
        Transaction result = transactionService.startTransfer(request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getFromAccount()).isEqualTo(FROM_ACCOUNT);
        assertThat(result.getToAccount()).isEqualTo(TO_ACCOUNT);
        // INITIATED, PROCESSING, and SUCCESS = 3 saves
        verify(transactionRepository, times(3)).save(any(Transaction.class));
        verify(accountServiceClient).lockFunds(eq(FROM_ACCOUNT), any(BigDecimal.class), anyString(), any(OffsetDateTime.class));
        verify(accountServiceClient).credit(eq(TO_ACCOUNT), any(BigDecimal.class));
        verify(accountServiceClient).releaseLock(eq(FROM_ACCOUNT));
    }

    @Test
    void testStartTransfer_WithIdempotency_ReturnsExisting() {
        // Arrange
        TransactionService.TransferRequest request = new TransactionService.TransferRequest(
                FROM_ACCOUNT,
                TO_ACCOUNT,
                new BigDecimal("1000.00"),
                "INR",
                IDEMPOTENCY_KEY
        );

        Transaction existingTransaction = new Transaction();
        existingTransaction.setTransactionRef("EXISTING-REF");
        existingTransaction.setIdempotencyKey(IDEMPOTENCY_KEY);
        existingTransaction.setStatus(Transaction.Status.SUCCESS);

        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existingTransaction));

        // Act
        Transaction result = transactionService.startTransfer(request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getTransactionRef()).isEqualTo("EXISTING-REF");
        assertThat(result.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(accountServiceClient, never()).lockFunds(anyString(), any(BigDecimal.class), anyString(), any(OffsetDateTime.class));
    }

    @Test
    void testStartTransfer_WithIdempotency_NewTransaction() {
        // Arrange
        TransactionService.TransferRequest request = new TransactionService.TransferRequest(
                FROM_ACCOUNT,
                TO_ACCOUNT,
                new BigDecimal("1000.00"),
                "INR",
                IDEMPOTENCY_KEY
        );

        Transaction transaction = new Transaction();
        transaction.setTransactionRef(UUID.randomUUID().toString());
        transaction.setIdempotencyKey(IDEMPOTENCY_KEY);

        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(transaction);
        doNothing().when(accountServiceClient).lockFunds(anyString(), any(BigDecimal.class), anyString(), any(OffsetDateTime.class));
        doNothing().when(accountServiceClient).credit(anyString(), any(BigDecimal.class));
        doNothing().when(accountServiceClient).releaseLock(anyString());

        // Act
        Transaction result = transactionService.startTransfer(request);

        // Assert
        assertThat(result).isNotNull();
        verify(transactionRepository).findByIdempotencyKey(IDEMPOTENCY_KEY);
        // INITIATED, PROCESSING, and SUCCESS = 3 saves
        verify(transactionRepository, times(3)).save(any(Transaction.class));
    }

    @Test
    void testStartTransfer_InvalidSameAccounts() {
        // Arrange
        TransactionService.TransferRequest request = new TransactionService.TransferRequest(
                FROM_ACCOUNT,
                FROM_ACCOUNT, // Same account
                new BigDecimal("1000.00"),
                "INR",
                null
        );

        when(transactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(messageSourceService.getMessage("transaction.same.accounts"))
                .thenReturn("Source and destination accounts cannot be the same");

        // Act & Assert
        assertThatThrownBy(() -> transactionService.startTransfer(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Source and destination accounts cannot be the same");

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void testStartTransfer_InvalidZeroAmount() {
        // Arrange
        TransactionService.TransferRequest request = new TransactionService.TransferRequest(
                FROM_ACCOUNT,
                TO_ACCOUNT,
                BigDecimal.ZERO,
                "INR",
                null
        );

        when(transactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(messageSourceService.getMessage("transaction.invalid.amount"))
                .thenReturn("Transfer amount must be greater than zero");

        // Act & Assert
        assertThatThrownBy(() -> transactionService.startTransfer(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Transfer amount must be greater than zero");

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void testStartTransfer_InvalidNegativeAmount() {
        // Arrange
        TransactionService.TransferRequest request = new TransactionService.TransferRequest(
                FROM_ACCOUNT,
                TO_ACCOUNT,
                new BigDecimal("-100.00"),
                "INR",
                null
        );

        when(transactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(messageSourceService.getMessage("transaction.invalid.amount"))
                .thenReturn("Transfer amount must be greater than zero");

        // Act & Assert
        assertThatThrownBy(() -> transactionService.startTransfer(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Transfer amount must be greater than zero");

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void testStartTransfer_InsufficientBalance() {
        // Arrange
        TransactionService.TransferRequest request = new TransactionService.TransferRequest(
                FROM_ACCOUNT,
                TO_ACCOUNT,
                new BigDecimal("1000.00"),
                "INR",
                null
        );

        Transaction transaction = new Transaction();
        transaction.setTransactionRef(UUID.randomUUID().toString());
        transaction.setStatus(Transaction.Status.INITIATED);

        when(transactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(transaction);
        doThrow(new InsufficientBalanceException("Insufficient balance"))
                .when(accountServiceClient).lockFunds(anyString(), any(BigDecimal.class), anyString(), any(OffsetDateTime.class));

        // Act
        Transaction result = transactionService.startTransfer(request);

        // Assert
        assertThat(result).isNotNull();
        verify(transactionRepository, atLeastOnce()).save(any(Transaction.class));
        // Transaction should be marked as FAILED
    }

    @Test
    void testGetByRef_Found() {
        // Arrange
        String transactionRef = "TX-REF-123";
        Transaction transaction = new Transaction();
        transaction.setTransactionRef(transactionRef);

        when(transactionRepository.findByTransactionRef(transactionRef))
                .thenReturn(Optional.of(transaction));

        // Act
        Optional<Transaction> result = transactionService.getByRef(transactionRef);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getTransactionRef()).isEqualTo(transactionRef);
    }

    @Test
    void testGetByRef_NotFound() {
        // Arrange
        String transactionRef = "NON-EXISTENT";

        when(transactionRepository.findByTransactionRef(transactionRef))
                .thenReturn(Optional.empty());

        // Act
        Optional<Transaction> result = transactionService.getByRef(transactionRef);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void testReverse_Success() {
        // Arrange
        String transactionRef = "TX-REF-123";
        Transaction transaction = new Transaction();
        transaction.setTransactionRef(transactionRef);
        transaction.setFromAccount(FROM_ACCOUNT);
        transaction.setToAccount(TO_ACCOUNT);
        transaction.setAmount(new BigDecimal("1000.00"));
        transaction.setStatus(Transaction.Status.SUCCESS);

        when(transactionRepository.findByTransactionRef(transactionRef))
                .thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(transaction);
        doNothing().when(accountServiceClient).lockFunds(anyString(), any(BigDecimal.class), anyString(), any(OffsetDateTime.class));
        doNothing().when(accountServiceClient).credit(anyString(), any(BigDecimal.class));
        doNothing().when(accountServiceClient).unlockFunds(anyString(), any(BigDecimal.class));
        when(messageSourceService.getMessage("transaction.reversed.by.user"))
                .thenReturn("Reversed by user");

        // Act
        Transaction result = transactionService.reverse(transactionRef);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(Transaction.Status.FAILED);
        assertThat(result.getFailureReason()).isEqualTo("Reversed by user");
        verify(accountServiceClient).lockFunds(eq(TO_ACCOUNT), any(BigDecimal.class), anyString(), any(OffsetDateTime.class));
        verify(accountServiceClient).credit(eq(FROM_ACCOUNT), any(BigDecimal.class));
        verify(accountServiceClient).unlockFunds(eq(TO_ACCOUNT), any(BigDecimal.class));
    }

    @Test
    void testReverse_TransactionNotFound() {
        // Arrange
        String transactionRef = "NON-EXISTENT";

        when(transactionRepository.findByTransactionRef(transactionRef))
                .thenReturn(Optional.empty());
        when(messageSourceService.getMessage("transaction.not.found.reversal", transactionRef))
                .thenReturn("Transaction not found for reversal: " + transactionRef);

        // Act & Assert
        assertThatThrownBy(() -> transactionService.reverse(transactionRef))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Transaction not found");

        verify(accountServiceClient, never()).lockFunds(anyString(), any(BigDecimal.class), anyString(), any(OffsetDateTime.class));
    }

    @Test
    void testReverse_InvalidState_NotSuccessful() {
        // Arrange
        String transactionRef = "TX-REF-123";
        Transaction transaction = new Transaction();
        transaction.setTransactionRef(transactionRef);
        transaction.setStatus(Transaction.Status.FAILED);

        when(transactionRepository.findByTransactionRef(transactionRef))
                .thenReturn(Optional.of(transaction));
        when(messageSourceService.getMessage("transaction.reverse.invalid.status", "FAILED"))
                .thenReturn("Only successful transactions can be reversed. Current status: FAILED");

        // Act & Assert
        assertThatThrownBy(() -> transactionService.reverse(transactionRef))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only successful transactions can be reversed");

        verify(accountServiceClient, never()).lockFunds(anyString(), any(BigDecimal.class), anyString(), any(OffsetDateTime.class));
    }

    @Test
    void testReverse_InvalidState_Processing() {
        // Arrange
        String transactionRef = "TX-REF-123";
        Transaction transaction = new Transaction();
        transaction.setTransactionRef(transactionRef);
        transaction.setStatus(Transaction.Status.PROCESSING);

        when(transactionRepository.findByTransactionRef(transactionRef))
                .thenReturn(Optional.of(transaction));
        when(messageSourceService.getMessage("transaction.reverse.invalid.status", "PROCESSING"))
                .thenReturn("Only successful transactions can be reversed. Current status: PROCESSING");

        // Act & Assert
        assertThatThrownBy(() -> transactionService.reverse(transactionRef))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only successful transactions can be reversed");

        verify(accountServiceClient, never()).lockFunds(anyString(), any(BigDecimal.class), anyString(), any(OffsetDateTime.class));
    }
}

