pipeline {
    agent any
    options {
        skipDefaultCheckout(true)
    }
    environment {
            // === 기본 환경 변수 (나중에 실제 값으로 덮어쓸 수 있음) ===
            JOB_NAME        = "${env.JOB_NAME}" // jenkins가 넣어줌
            BRANCH_NAME     = "main"

            // 소스 저장소
            GIT_URL         = "https://gitea.accordi-on.kro.kr/Accordi-on/${JOB_NAME}.git"
            GIT_CREDENTIALS = "gitea-token"

    }
    stages {
        stage('Git Clone') {
            steps {
                echo "🌐 [Git Clone] Cloning repository from ${GIT_URL}..."
            }
        }
        stage('Checkout Branch') {
            steps {
                echo "🌿 [Checkout] Checking out branch ${BRANCH_NAME}..."
            }
        }
        
        stage('Build Test') {
            steps {
                echo '🧪 [Build Test] Running unit/lint tests...'
            }
        }

        stage('Dependency-Check Analysis') {
            steps {
                echo '🔍 [Dependency-Check] Analyzing dependency vulnerabilities...'
            }
        }

        stage('Sonarqube and Quality gate') {
            steps {
                echo '📊 [SonarQube] Running code analysis and sending results...'
            }
        }

        stage('Quality Gate Check') {
            steps {
                echo '🚦 [Quality Gate] Waiting for SonarQube quality gate status...'
            }
        }

        stage('Docker image build') {
            steps {
                echo "🐳 [Docker Build] Building Docker image for ${APP_NAME}:${IMAGE_TAG}..."
            }
        }

        stage('Docker image push to Harbor') {
            steps {
                echo "📤 [Image Push] Pushing image to Harbor registry ${HARBOR_REGISTRY}/${HARBOR_PROJECT}/${APP_NAME}:${IMAGE_TAG}..."
            }
        }

        stage('Anchore analyse') {
            steps {
                echo '🛡 [Anchore] Running container image security scan...'
            }
        }

        stage('Modify Helm Repo') {
            steps {
                echo '📝 [Helm Repo] Updating Helm chart values (image.tag, etc.)...'
            }
        }

        stage('Argo Deploy') {
            steps {
                echo "🚀 [Argo Deploy] Syncing ArgoCD app ${ARGOCD_APP} for deployment..."
            }
        }
    }

    post {
        success {
            echo "✅ [Post Actions] Pipeline for ${APP_NAME}:${IMAGE_TAG} completed successfully!"
        }
        failure {
            echo "❌ [Post Actions] Pipeline failed. Check logs for details."
        }
        always {
            echo "📦 [Cleanup] Finalizing pipeline (workspace cleanup, etc.)"
        }
    }
}
