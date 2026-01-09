CREATE TABLE accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_number VARCHAR(50) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    status ENUM('ACTIVE', 'SUSPENDED', 'CLOSED') NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_ref VARCHAR(100) NOT NULL UNIQUE,
    from_account VARCHAR(50) NOT NULL,
    to_account VARCHAR(50) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    status ENUM('INITIATED', 'PROCESSING', 'SUCCESS', 'FAILED') NOT NULL,
    failure_reason VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_from_account (from_account),
    INDEX idx_to_account (to_account),
    INDEX idx_status (status)
);


CREATE TABLE account_locks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_number VARCHAR(50) NOT NULL,
    locked_by VARCHAR(100) NOT NULL,
    lock_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    lock_expiry TIMESTAMP NOT NULL,
    
    UNIQUE KEY uk_account_lock (account_number)
);



INSERT INTO pip.accounts (account_number, customer_id, balance, currency, status, created_at, updated_at)
VALUES
('ACC1001', 101, 50000.00, 'INR', 'ACTIVE', now(), now()),
('ACC1002', 102, 35000.00, 'INR', 'ACTIVE', now(), now()),
('ACC1003', 103, 15000.00, 'INR', 'ACTIVE', now(), now()),
('ACC1004', 104, 20000.00, 'INR', 'ACTIVE', now(), now()),
('ACC1005', 105, 75000.00, 'INR', 'ACTIVE', now(), now());