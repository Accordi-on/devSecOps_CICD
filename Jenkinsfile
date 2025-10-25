pipeline {
    agent any

    environment {
        APP_NAME         = 'my-service'
        IMAGE_TAG        = "${env.BUILD_NUMBER}"
        HARBOR_REGISTRY  = 'harbor.accordi-on.kro.kr'
        HARBOR_PROJECT   = 'my-project'
        ARGOCD_APP       = 'my-service-app'
    }

    stages {


        stage('Git clone') {
            steps {
                echo '📥 [Git clone] Cloning source code from repository....'
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
