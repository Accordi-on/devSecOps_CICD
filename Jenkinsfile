pipeline {
    agent { kubernetes { inheritFrom 'jenkins-agent-k8s' } }
    options { skipDefaultCheckout(true) }
    environment {
            JOB_NAME        = "${env.JOB_NAME}"
            BRANCH_NAME     = "dev"
            GIT_URL         = "${env.GIT_URL}"
            HARBOR_REGISTRY = "${env.HARBOR_URL}"
            ARGOCD_URL      = "${env.ARGOCD_URL}"
            GIT_CREDENTIALS = credentials("gitea-token")
            SONARQUBE_SERVER = 'SonarQube'
            APP_NAME        = "${env.JOB_NAME}"
            ARGOCD_CREDENTIALS = credentials('argocd-token')
            HARBOR_PROJECT  = "${env.JOB_NAME}"
            ARGOCD_APP      = "${env.JOB_NAME}"
    }
    stages {
        stage('Git Clone') {
            steps {
                script{
                    def num = env.BUILD_NUMBER as Integer
                    def major = (num / 100).intValue()
                    def minor = ((num % 100) / 10).intValue()
                    def patch = num % 10

                    env.IMAGE_TAG= "v${major}.${minor}.${patch}"
                    echo "📦 IMAGE_TAG = ${env.IMAGE_TAG}"
                }
                echo "🌐 [Git Clone] Cloning repository from. ${env.GIT_URL}..."
                sh """
                    rm -rf ${APP_NAME} || true 
                    git clone ${GIT_URL} ${APP_NAME}
                    echo "✅ [Git Clone] Repository cloned successfully."
                """
            }
        }stage('Checkout Branch') {
            steps { 
                echo "🌿 [Checkout] Checking out branch ${BRANCH_NAME}..."
                dir("${APP_NAME}") {
                    sh """
                        git checkout ${BRANCH_NAME}
                        git fetch origin ${BRANCH_NAME}
                    """
                }
            }
        }stage('Build Test'){
            steps {
                script { load('ci/buildTest.groovy').run("${APP_NAME}") }
            }
        }stage('Dependency-Check'){
            steps {
                script { load('ci/dependencyCheck.groovy').run() }
            }
        }
        stage('SonarQube Analysis'){
            steps {
                script { load('ci/sonarQubeAnalysis.groovy').run() }
            }
        }stage('SonarQube Quality Gate'){
            steps {
                script { load('ci/sonarQubeQualityGate.groovy').run() }
            }
        }stage('Docker image build'){
            steps {
                script { load('ci/dockerImageBuild.groovy').run() }
            }
        }stage('Docker image push'){
            steps {
                script { load('ci/dockerImagePush.groovy').run() }
            }
        }stage('Image Analysis'){
            steps {
                script { load('ci/imageAnalysis.groovy').run() }
            }
        }stage('Modify Helm Repo'){
            steps {
                script { load('ci/modifyHelmRepo.groovy').run() }
            }
        }stage('Argo Deploy'){
            steps {
                script { load('ci/argoDeploy.groovy').run() }
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
