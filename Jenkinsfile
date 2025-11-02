pipeline {
    agent { kubernetes { inheritFrom 'jenkins-agent-k8s' } }
    environment {
            JOB_NAME        = "${env.JOB_NAME}"
            BRANCH_NAME     = "main"
            GIT_URL         = "http://gitea.service.accordi-on.com/Accordi-on/${env.JOB_NAME}.git"
            HARBOR_REGISTRY = "http://harbor.service.accordi-on.com"
            ARGOCD_URL      = "http://argocd.service.accordi-on.com"
            GIT_CREDENTIALS = credentials("gitea-token")
            SONARQUBE_SERVER = 'SonarQube'
            APP_NAME        = "${env.JOB_NAME}"
            ARGOCD_CREDENTIALS = credentials('argocd-token')
            HARBOR_PROJECT  = "${env.JOB_NAME}"
            ARGOCD_APP      = "${env.JOB_NAME}"
    }
    stages {
        stage('Checkout') {
            steps {
                script {
                    def num = env.BUILD_NUMBER as Integer
                    def major = (num / 100).intValue()
                    def minor = ((num % 100) / 10).intValue()
                    def patch = num % 10

                    env.IMAGE_TAG = "v${major}.${minor}.${patch}"
                    echo "📦 IMAGE_TAG = ${env.IMAGE_TAG}"
                }
                sh '''
                    git checkout ${BRANCH_NAME}
                '''
            }
        }

        stage('Build Test') {
            steps {
                script {
                    load("ci/buildTest.groovy").run("${env.APP_NAME}")
                }
            }
        }

        // stage('Dependency-Check') {
        //     steps {
        //         script {
        //             load('${APP_NAME}/ci/dependencyCheck.groovy').run()
        //         }
        //     }
        // }

        // stage('SonarQube Analysis') {
        //     steps {
        //         script {
        //             load('${APP_NAME}/ci/sonarQubeAnalysis.groovy').run()
        //         }
        //     }
        // }

        // stage('SonarQube Quality Gate') {
        //     steps {
        //         script {
        //             load('${APP_NAME}/ci/sonarQubeQualityGate.groovy').run()
        //         }
        //     }
        // }

        // stage('Docker image build') {
        //     steps {
        //         script {
        //             load('${APP_NAME}/ci/dockerImageBuild.groovy').run()
        //         }
        //     }
        // }

        // stage('Docker image push') {
        //     steps {
        //         script {
        //             load('${APP_NAME}/ci/dockerImagePush.groovy').run()
        //         }
        //     }
        // }

        // stage('Image Analysis') {
        //     steps {
        //         script {
        //             load('${APP_NAME}/ci/imageAnalysis.groovy').run()
        //         }
        //     }
        // }

        // stage('Modify Helm Repo') {
        //     steps {
        //         script {
        //             load('${APP_NAME}/ci/modifyHelmRepo.groovy').run()
        //         }
        //     }
        // }

        // stage('Argo Deploy') {
        //     steps {
        //         script {
        //             load('${APP_NAME}/ci/argoDeploy.groovy').run()
        //         }
        //     }
        // }

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