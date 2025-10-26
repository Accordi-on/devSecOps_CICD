pipeline {
    agent any
    tools {
        nodejs "nodejs"
    }
    options {
        skipDefaultCheckout(true)
    }
    environment {
            JOB_NAME        = "${env.JOB_NAME}"
            BRANCH_NAME     = "main"
            GIT_URL         = "https://gitea.accordi-on.kro.kr/Accordi-on/${env.JOB_NAME}.git"
            GIT_CREDENTIALS = "gitea-token"
            APP_NAME        = "${env.JOB_NAME}"
            IMAGE_TAG       = "build-${env.BUILD_NUMBER}"
            HARBOR_REGISTRY = "harbor.accordi-on.kro.kr"
            HARBOR_PROJECT  = "demo-project"
            ARGOCD_APP      = "${env.JOB_NAME}"

    }
    stages {
        stage('Git Clone') {
            steps {
                echo "🌐 [Git Clone] Cloning repository from. ${env.GIT_URL}..."
                sh """
                    rm -rf ${APP_NAME} || true 
                    git clone ${GIT_URL} ${APP_NAME}
                """

            }
        }
        stage('Checkout Branch') {
            steps { 
                echo "🌿 [Checkout] Checking out branch ${env.BRANCH_NAME}..."
                dir("${APP_NAME}") {
                    sh """
                        git checkout ${BRANCH_NAME}
                        git fetch origin ${BRANCH_NAME}
                    """
                }
            }
        }

        stage('Build Test') {
            steps {
                echo '🧪 [Build Test] Running unit/lint tests...'
                dir("${APP_NAME}") {
                        sh '''
                            npm ci
                            npm test
                        '''
                }
            }
        }
        stage('Dependency-Check') {
            steps {
                dir("${APP_NAME}") {
                    dependencyCheck additionalArguments: ''' 
                        -o "./" 
                        -s "./"
                        -f "ALL" 
                        --prettyPrint''', odcInstallation: 'OWASP-DepCheck-10'
                    
                    // 💡 디버깅 1: 실행 후 파일 목록 확인
                    sh 'ls -al dependency-check-report.xml || echo "Report file not found!"'

                    // 💡 디버깅 2: Dependency-Check 실행 결과 코드 확인
                    // sh 'cat dependency-check.log' // 로그 파일이 생성되었다면 확인

                    dependencyCheckPublisher pattern: 'dependency-check-report.xml'
                }
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
