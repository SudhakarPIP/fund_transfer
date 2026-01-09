pipeline {
    agent any

    environment {
        // Docker Registry Configuration
        REGISTRY = "${env.DOCKER_REGISTRY ?: 'your-docker-registry.example.com'}"
        REGISTRY_CREDENTIALS = "${env.DOCKER_REGISTRY_CREDENTIALS ?: 'docker-registry-credentials'}"
        
        // Image Names
        ACCOUNT_SERVICE_IMAGE = "account-service"
        TRANSACTION_SERVICE_IMAGE = "transaction-service"
        NOTIFICATION_SERVICE_IMAGE = "notification-service"
        
        // Version Tag
        IMAGE_TAG = "${env.BUILD_NUMBER ?: 'latest'}"
        
        // SonarQube Configuration
        SONAR_PROJECT_KEY = "fund-transfer-microservices"
        SONAR_PROJECT_NAME = "Fund Transfer Microservices"
    }

    tools {
        maven 'Maven-3.9'
        jdk 'JDK-17'
    }

    stages {
        stage('Build') {
            steps {
                echo 'Building all microservices...'
                sh '''
                    mvn -B clean compile -DskipTests
                    echo "Build completed successfully"
                '''
            }
        }

        stage('Unit Tests') {
            steps {
                echo 'Running unit tests for all services...'
                sh '''
                    mvn -B test
                    echo "All tests completed"
                '''
            }
            post {
                always {
                    // Publish test results
                    junit '**/target/surefire-reports/*.xml'
                    // Archive test reports
                    archiveArtifacts artifacts: '**/target/surefire-reports/**', allowEmptyArchive: true
                }
            }
        }

        stage('SonarQube Analysis') {
            when {
                anyOf {
                    environment name: 'SONARQUBE_ENABLED', value: 'true'
                    // Uncomment to always run SonarQube
                    // expression { true }
                }
            }
            steps {
                echo 'Running SonarQube analysis...'
                script {
                    try {
                        // Configure SonarQube scanner
                        withSonarQubeEnv('SonarQube') {
                            sh '''
                                mvn -B sonar:sonar \
                                    -Dsonar.projectKey=${SONAR_PROJECT_KEY} \
                                    -Dsonar.projectName="${SONAR_PROJECT_NAME}" \
                                    -Dsonar.sources=account-service/src,transaction-service/src,notification-service/src \
                                    -Dsonar.tests=account-service/src/test,transaction-service/src/test,notification-service/src/test \
                                    -Dsonar.java.binaries=account-service/target/classes,transaction-service/target/classes,notification-service/target/classes \
                                    -Dsonar.modules=account-service,transaction-service,notification-service \
                                    -Dsonar.module.account-service.sonar.projectName=Account Service \
                                    -Dsonar.module.transaction-service.sonar.projectName=Transaction Service \
                                    -Dsonar.module.notification-service.sonar.projectName=Notification Service
                            '''
                        }
                    } catch (Exception e) {
                        echo "SonarQube analysis failed or not configured: ${e.getMessage()}"
                        echo "Continuing with pipeline..."
                        // Uncomment to fail the build if SonarQube fails
                        // currentBuild.result = 'UNSTABLE'
                    }
                }
            }
        }

        stage('Build Docker Images') {
            steps {
                echo 'Building Docker images for all services...'
                script {
                    // Package all services first
                    sh 'mvn -B package -DskipTests'
                    
                    // Build Account Service image
                    echo 'Building account-service Docker image...'
                    sh """
                        docker build -t ${REGISTRY}/${ACCOUNT_SERVICE_IMAGE}:${IMAGE_TAG} \
                                     -t ${REGISTRY}/${ACCOUNT_SERVICE_IMAGE}:latest \
                                     ./account-service
                    """
                    
                    // Build Transaction Service image
                    echo 'Building transaction-service Docker image...'
                    sh """
                        docker build -t ${REGISTRY}/${TRANSACTION_SERVICE_IMAGE}:${IMAGE_TAG} \
                                     -t ${REGISTRY}/${TRANSACTION_SERVICE_IMAGE}:latest \
                                     ./transaction-service
                    """
                    
                    // Build Notification Service image
                    echo 'Building notification-service Docker image...'
                    sh """
                        docker build -t ${REGISTRY}/${NOTIFICATION_SERVICE_IMAGE}:${IMAGE_TAG} \
                                     -t ${REGISTRY}/${NOTIFICATION_SERVICE_IMAGE}:latest \
                                     ./notification-service
                    """
                    
                    echo 'All Docker images built successfully'
                }
            }
        }

        stage('Push to Registry') {
            when {
                anyOf {
                    branch 'main'
                    branch 'master'
                    branch 'develop'
                }
            }
            steps {
                echo 'Pushing Docker images to registry...'
                script {
                    withCredentials([usernamePassword(credentialsId: "${REGISTRY_CREDENTIALS}", 
                                                      usernameVariable: 'DOCKER_USER', 
                                                      passwordVariable: 'DOCKER_PASS')]) {
                        sh '''
                            echo "${DOCKER_PASS}" | docker login ${REGISTRY} -u "${DOCKER_USER}" --password-stdin
                        '''
                        
                        // Push Account Service image
                        echo 'Pushing account-service image...'
                        sh """
                            docker push ${REGISTRY}/${ACCOUNT_SERVICE_IMAGE}:${IMAGE_TAG}
                            docker push ${REGISTRY}/${ACCOUNT_SERVICE_IMAGE}:latest
                        """
                        
                        // Push Transaction Service image
                        echo 'Pushing transaction-service image...'
                        sh """
                            docker push ${REGISTRY}/${TRANSACTION_SERVICE_IMAGE}:${IMAGE_TAG}
                            docker push ${REGISTRY}/${TRANSACTION_SERVICE_IMAGE}:latest
                        """
                        
                        // Push Notification Service image
                        echo 'Pushing notification-service image...'
                        sh """
                            docker push ${REGISTRY}/${NOTIFICATION_SERVICE_IMAGE}:${IMAGE_TAG}
                            docker push ${REGISTRY}/${NOTIFICATION_SERVICE_IMAGE}:latest
                        """
                        
                        sh 'docker logout ${REGISTRY}'
                    }
                }
            }
        }

        stage('Deploy to Dev') {
            steps {
                echo 'Deploying all services to Dev environment...'
                script {
                    // Stop existing containers
                    sh 'docker-compose down || true'
                    
                    // Update docker-compose.yml to use registry images if needed
                    // For local deployment, we'll use the locally built images
                    // If you want to use registry images, uncomment and modify:
                    // sh '''
                    //     sed -i "s|build:|image: ${REGISTRY}/${ACCOUNT_SERVICE_IMAGE}:${IMAGE_TAG}|g" docker-compose.yml
                    //     sed -i "s|context: ./account-service||g" docker-compose.yml
                    //     sed -i "s|dockerfile: Dockerfile||g" docker-compose.yml
                    // '''
                    
                    // Start all services
                    sh '''
                        docker-compose up -d --build
                        echo "Waiting for services to be healthy..."
                        sleep 30
                    '''
                    
                    // Verify services are running
                    sh '''
                        echo "Checking service health..."
                        docker-compose ps
                        
                        # Wait for services to be ready
                        max_attempts=30
                        attempt=0
                        
                        while [ $attempt -lt $max_attempts ]; do
                            if docker-compose ps | grep -q "Up (healthy)"; then
                                echo "Services are healthy!"
                                break
                            fi
                            attempt=$((attempt + 1))
                            echo "Waiting for services to be healthy... (attempt $attempt/$max_attempts)"
                            sleep 5
                        done
                    '''
                }
            }
            post {
                success {
                    echo 'Deployment to Dev environment completed successfully!'
                    echo """
                        Services are available at:
                        - Account Service: http://localhost:8081
                        - Transaction Service: http://localhost:8082
                        - Notification Service: http://localhost:8083
                    """
                }
                failure {
                    echo 'Deployment to Dev environment failed!'
                    sh 'docker-compose logs --tail=100'
                }
            }
        }
    }

    post {
        always {
            // Clean up workspace
            cleanWs()
        }
        success {
            echo 'Pipeline completed successfully!'
        }
        failure {
            echo 'Pipeline failed!'
        }
    }
}
