# Fund Transfer Microservices

A microservices-based fund transfer system built with Spring Boot, featuring distributed transaction management, resilience patterns, and comprehensive error handling.

## 🏗️ Architecture

The application is split into three independent microservices:

- **Account Service** (Port 8081) - Manages accounts, balances, and fund locking
- **Transaction Service** (Port 8082) - Handles fund transfers and transaction orchestration
- **Notification Service** (Port 8083) - Sends email notifications for completed transactions

### Technology Stack

- **Framework**: Spring Boot 3.3.1
- **Language**: Java 17
- **Database**: MySQL 8.0
- **Build Tool**: Maven 3.9+
- **Containerization**: Docker & Docker Compose
- **Resilience**: Resilience4j (Circuit Breaker, Retry)
- **Communication**: REST APIs (WebClient)
- **CI/CD**: Jenkins Pipeline

## ✨ Key Features

- ✅ **Microservices Architecture** - Independent, scalable services
- ✅ **Saga Pattern** - Distributed transaction management with compensation
- ✅ **Idempotency** - Safe retry of duplicate requests
- ✅ **Resilience Patterns** - Circuit breaker and retry for service calls
- ✅ **Comprehensive Error Handling** - Standardized error responses across services
- ✅ **Email Notifications** - Transaction completion notifications
- ✅ **Docker Support** - Full containerization with docker-compose
- ✅ **CI/CD Pipeline** - Automated build, test, and deployment
- ✅ **Health Checks** - Service health monitoring
- ✅ **Optimistic Locking** - Concurrent update protection

## 📋 Prerequisites

- **Java 17** or higher
- **Maven 3.9+**
- **Docker** and **Docker Compose** (for containerized deployment)
- **MySQL 8.0** (for local development without Docker)

## 🚀 Quick Start

### Option 1: Docker Compose (Recommended)

```bash
# Clone the repository
git clone <repository-url>
cd fund_transfer-micrservices

# Start all services
docker-compose up -d --build

# Check service status
docker-compose ps

# View logs
docker-compose logs -f
```

Services will be available at:
- Account Service: http://localhost:8081
- Transaction Service: http://localhost:8082
- Notification Service: http://localhost:8083
- MailHog UI: http://localhost:8025 (for viewing emails)

### Option 2: Local Development

1. **Start MySQL Database**:
   ```bash
   # Ensure MySQL is running on localhost:3306
   # Database: pip
   # Username: root
   # Password: Svmr12!@
   ```

2. **Start Account Service**:
   ```bash
   cd account-service
   mvn spring-boot:run
   ```

3. **Start Transaction Service** (in new terminal):
   ```bash
   cd transaction-service
   mvn spring-boot:run
   ```

4. **Start Notification Service** (in new terminal):
   ```bash
   cd notification-service
   mvn spring-boot:run
   ```

## 📚 API Documentation

### Account Service (Port 8081)

#### Get Account Balance
```bash
GET /accounts/{accountNumber}/balance
```

#### Lock Funds
```bash
PUT /accounts/{accountNumber}/lock
Content-Type: application/json

{
  "amount": 1000.00,
  "lockedBy": "TXN-001",
  "lockMinutes": 5
}
```

#### Credit Account
```bash
PUT /accounts/{accountNumber}/credit
Content-Type: application/json

{
  "amount": 1000.00
}
```

#### Debit Account
```bash
PUT /accounts/{accountNumber}/debit
Content-Type: application/json

{
  "amount": 1000.00
}
```

### Transaction Service (Port 8082)

#### Initiate Fund Transfer
```bash
POST /transactions/transfer
Content-Type: application/json

{
  "fromAccount": "ACC1001",
  "toAccount": "ACC1002",
  "amount": 1000.00,
  "currency": "INR",
  "idempotencyKey": "unique-key-123"
}
```

#### Get Transaction Status
```bash
GET /transactions/{transactionId}
```

#### Reverse Transaction
```bash
POST /transactions/{transactionId}/reverse
```

**Note**: Transaction must have status `SUCCESS` to be reversed.

### Notification Service (Port 8083)

#### Send Transaction Notification
```bash
POST /notifications/transaction-completed
Content-Type: application/json

{
  "toEmail": "user@example.com",
  "transactionRef": "TXN-001",
  "fromAccount": "ACC1001",
  "toAccount": "ACC1002",
  "amount": "1000.00"
}
```

## 🧪 Testing

### Run All Tests
```bash
mvn test
```

### Run Tests for Specific Service
```bash
# Account Service
mvn test -pl account-service

# Transaction Service
mvn test -pl transaction-service

# Notification Service
mvn test -pl notification-service
```

### Run Specific Test Class
```bash
mvn test -pl transaction-service -Dtest=TransactionServiceTest
```

## 🏗️ Project Structure

```
fund_transfer-micrservices/
├── account-service/          # Account management service
│   ├── src/
│   │   ├── main/java/com/example/account/
│   │   └── main/resources/
│   ├── Dockerfile
│   └── pom.xml
├── transaction-service/      # Transaction orchestration service
│   ├── src/
│   │   ├── main/java/com/example/transaction/
│   │   └── main/resources/
│   ├── Dockerfile
│   └── pom.xml
├── notification-service/     # Email notification service
│   ├── src/
│   │   ├── main/java/com/example/notification/
│   │   └── main/resources/
│   ├── Dockerfile
│   └── pom.xml
├── docker-compose.yml        # Docker Compose configuration
├── Jenkinsfile              # CI/CD pipeline
└── pom.xml                  # Parent POM
```

## 🔧 Configuration

### Environment Variables

#### Account Service
- `SPRING_DATASOURCE_URL` - Database connection URL
- `SPRING_DATASOURCE_USERNAME` - Database username
- `SPRING_DATASOURCE_PASSWORD` - Database password

#### Transaction Service
- `SPRING_DATASOURCE_URL` - Database connection URL
- `SPRING_DATASOURCE_USERNAME` - Database username
- `SPRING_DATASOURCE_PASSWORD` - Database password
- `WEBCLIENT_ACCOUNT_SERVICE_BASE_URL` - Account service URL
- `WEBCLIENT_NOTIFICATION_SERVICE_BASE_URL` - Notification service URL

#### Notification Service
- `SPRING_MAIL_HOST` - SMTP server host
- `SPRING_MAIL_PORT` - SMTP server port
- `SPRING_MAIL_USERNAME` - SMTP username (optional for MailHog)
- `SPRING_MAIL_PASSWORD` - SMTP password (optional for MailHog)

### Application Profiles

- **default** - Production configuration (uses environment variables)
- **local** - Local development (uses local MySQL)
- **test** - Testing (uses H2 in-memory database)

## 🔄 CI/CD Pipeline

The project includes a Jenkins CI/CD pipeline with the following stages:

1. **Build** - Compiles all microservices
2. **Unit Tests** - Runs all tests
3. **SonarQube Analysis** - Code quality analysis (optional)
4. **Build Docker Images** - Creates Docker images for all services
5. **Push to Registry** - Pushes images to Docker registry (optional)
6. **Deploy to Dev** - Deploys all services using docker-compose

See [CICD_SETUP.md](CICD_SETUP.md) for detailed setup instructions.

## 📖 Documentation

- [API Usage Guide](API_USAGE.md) - Detailed API examples
- [Demo Guide](DEMO_GUIDE.md) - Complete demo scenarios
- [Running Guide](RUNNING.md) - How to run services
- [Testing Guide](TESTING.md) - Testing instructions
- [CI/CD Setup](CICD_SETUP.md) - Jenkins pipeline setup
- [Error Handling](ERROR_HANDLING.md) - Error handling documentation
- [Microservices Summary](MICROSERVICES_SUMMARY.md) - Architecture overview

## 🎯 Demo Scenarios

The system supports the following demo scenarios:

1. **Successful Fund Transfer** - Complete transaction with balance updates
2. **Compensation for Failed Transfer** - Saga pattern rollback
3. **Idempotency** - Duplicate request handling
4. **Notification Delivery** - Email notifications
5. **Jenkins Pipeline** - CI/CD demonstration

See [DEMO_GUIDE.md](DEMO_GUIDE.md) for step-by-step instructions.

## 🐛 Troubleshooting

### Services Not Starting

```bash
# Check service status
docker-compose ps

# View logs
docker-compose logs account-service
docker-compose logs transaction-service
docker-compose logs notification-service

# Restart services
docker-compose restart
```

### Database Connection Issues

- Verify MySQL is running: `docker-compose ps mysql`
- Check connection string in environment variables
- Ensure database `pip` exists

### Port Conflicts

If ports are already in use, update `docker-compose.yml` or stop conflicting services.

### IDE Errors

If you see "Cannot resolve symbol" errors in your IDE:
- Reload Maven projects
- Invalidate IDE caches
- See [IDE_REFRESH_GUIDE.md](IDE_REFRESH_GUIDE.md) for details

## 🔐 Security Notes

- Never commit passwords or sensitive data to Git
- Use environment variables for configuration
- In production, use secrets management (Docker secrets, Kubernetes secrets, etc.)
- Enable HTTPS for production deployments

## 📝 License

This project is for educational/demonstration purposes.

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests: `mvn test`
5. Submit a pull request

## 📞 Support

For issues or questions:
- Check the documentation files in the repository
- Review service logs: `docker-compose logs`
- Check [Troubleshooting](#-troubleshooting) section

## 🎓 Key Concepts

### Saga Pattern
The transaction service uses a Saga pattern (choreography) for distributed transactions:
1. Lock funds from source account
2. Credit destination account
3. Release lock from source account
4. If any step fails, compensation is triggered

### Idempotency
Transactions support idempotency keys to prevent duplicate processing:
- Same `idempotencyKey` returns the same transaction
- Safe to retry failed requests

### Resilience
- **Circuit Breaker**: Prevents cascading failures
- **Retry**: Automatic retry for transient failures
- **Timeout**: Prevents hanging requests

## 🚀 Next Steps

1. **Set up CI/CD**: Configure Jenkins pipeline (see [CICD_SETUP.md](CICD_SETUP.md))
2. **Configure Email**: Set up SMTP for notifications (see [MAIL_CONFIGURATION.md](MAIL_CONFIGURATION.md))
3. **Run Demo**: Follow [DEMO_GUIDE.md](DEMO_GUIDE.md) for complete demo
4. **Customize**: Adapt for your specific requirements

---

**Built with ❤️ using Spring Boot Microservices**
