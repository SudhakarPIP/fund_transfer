pipeline {
    agent any

    environment {
        REGISTRY = "your-docker-registry.example.com"
        IMAGE_NAME = "fund-transfer"
    }

    stages {
        stage('Build') {
            steps {
                sh 'mvn -B clean compile'
            }
        }

        stage('Unit Tests') {
            steps {
                sh 'mvn -B test'
            }
        }

        stage('SonarQube Analysis') {
            steps {
                echo 'Run SonarQube analysis here (configure with your Sonar server)'
                // sh 'mvn sonar:sonar -Dsonar.projectKey=fund-transfer'
            }
        }

        stage('Build Docker Images') {
            steps {
                sh 'mvn -B package -DskipTests'
                sh "docker build -t ${REGISTRY}/${IMAGE_NAME}:latest ."
            }
        }

        stage('Push to Registry') {
            steps {
                sh "docker push ${REGISTRY}/${IMAGE_NAME}:latest"
            }
        }

        stage('Deploy to Dev') {
            steps {
                sh 'docker-compose down || true'
                sh 'docker-compose up -d'
            }
        }
    }
}


