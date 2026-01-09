# Quick Start: CI/CD Pipeline

## Quick Setup (5 minutes)

### 1. Prerequisites Check

```bash
# Verify Docker is running
docker ps

# Verify Maven is available
mvn -version

# Verify Java 17
java -version
```

### 2. Create Jenkins Pipeline Job

1. Open Jenkins UI: http://localhost:8080
2. Click **New Item**
3. Enter name: `fund-transfer-microservices`
4. Select **Pipeline**
5. Click **OK**

### 3. Configure Pipeline

1. **Pipeline Definition**: 
   - Select **Pipeline script from SCM**
   
2. **SCM Configuration**:
   - **SCM**: Git
   - **Repository URL**: Your Git repository URL
   - **Credentials**: Add if repository is private
   - **Branch**: `*/main` (or your default branch)
   - **Script Path**: `Jenkinsfile`

3. Click **Save**

### 4. Run Pipeline

1. Click **Build Now**
2. Monitor progress in console output

### 5. Verify Deployment

After successful deployment, verify services:

```bash
# Check running containers
docker-compose ps

# Check service health
curl http://localhost:8081/actuator/health  # Account Service
curl http://localhost:8082/actuator/health  # Transaction Service
curl http://localhost:8083/actuator/health  # Notification Service
```

## Pipeline Stages Overview

| Stage | Description | Duration |
|-------|-------------|----------|
| **Build** | Compiles all microservices | ~2-3 min |
| **Unit Tests** | Runs all tests | ~1-2 min |
| **SonarQube Analysis** | Code quality check (optional) | ~1-2 min |
| **Build Docker Images** | Creates Docker images | ~3-5 min |
| **Push to Registry** | Pushes to registry (optional) | ~1-2 min |
| **Deploy to Dev** | Starts all services | ~2-3 min |

**Total Time**: ~10-15 minutes

## Common Commands

### Manual Pipeline Execution

```bash
# Build all services
mvn clean package -DskipTests

# Run tests
mvn test

# Build Docker images
docker-compose build

# Deploy services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop services
docker-compose down
```

### Troubleshooting

```bash
# Check Jenkins agent Docker access
docker ps

# Check service logs
docker-compose logs account-service
docker-compose logs transaction-service
docker-compose logs notification-service

# Restart a service
docker-compose restart account-service

# View service status
docker-compose ps
```

## Environment Variables

Set these in Jenkins → Manage Jenkins → Configure System → Global properties:

| Variable | Description | Default |
|----------|-------------|---------|
| `DOCKER_REGISTRY` | Docker registry URL | `your-docker-registry.example.com` |
| `DOCKER_REGISTRY_CREDENTIALS` | Credentials ID | `docker-registry-credentials` |
| `SONARQUBE_ENABLED` | Enable SonarQube | `false` (optional) |

## Skip Optional Stages

### Skip SonarQube

The SonarQube stage is optional. If not configured, it will be skipped automatically.

To explicitly disable:
- Set environment variable: `SONARQUBE_ENABLED=false`

### Skip Registry Push

The registry push stage only runs on `main`, `master`, or `develop` branches.

To skip:
- Use a different branch (e.g., `feature/*`)
- Or modify Jenkinsfile to remove the `when` condition

## Next Steps

1. **Configure SonarQube** (optional) - See `CICD_SETUP.md`
2. **Set up Docker Registry** (optional) - See `CICD_SETUP.md`
3. **Add Integration Tests** - Extend pipeline with integration test stage
4. **Deploy to Staging/Prod** - Add additional deployment stages

## Support

- **Full Documentation**: See `CICD_SETUP.md`
- **Requirements**: See `jenkins-requirements.txt`
- **Issues**: Check Jenkins build console for detailed error messages

