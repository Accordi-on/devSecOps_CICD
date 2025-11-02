pipeline {
    agent { kubernetes { inheritFrom 'jenkins-agent-k8s' } }
    environment {
            BRANCH_NAME     = "main"
            GIT_URL         = "http://gitea.service.accordi-on.com/Accordi-on/${env.JOB_NAME}.git"
            HARBOR_REGISTRY = "harbor.service.accordi-on.com"
            ARGOCD_URL      = "http://argocd.service.accordi-on.com"
            GIT_CREDENTIALS = credentials("gitea-token")
            SONARQUBE_SERVER = 'SonarQube'
            APP_NAME        = "${env.JOB_NAME}"
            ARGOCD_CREDENTIALS = credentials('argocd-token')
            ARGOCD_APP      = "${env.JOB_NAME}"
    }
    stages {
        stage('Image Tag') {
            steps {
                script {
                    //name slice
                    def parts = env.JOB_NAME.split('_')
                    env.PROJECT_NAME = parts[0].toLowerCase()
                    env.SERVICE_NAME = parts.size() > 1 ? parts[1].toLowerCase() : ''
                    echo "PROJECT_NAME = ${env.PROJECT_NAME}"
                    echo "SERVICE_NAME = ${env.SERVICE_NAME}"

                    //make image tag
                    def num = env.BUILD_NUMBER as Integer
                    def major = (num / 100).intValue()
                    def minor = ((num % 100) / 10).intValue()
                    def patch = num % 10

                    env.IMAGE_TAG = "v${major}.${minor}.${patch}"
                    echo "📦 IMAGE_TAG = ${env.IMAGE_TAG}"
                }
            }
        }

        stage('Build Test') {
            steps {
                script {
                    load("ci/buildTest.groovy").run("${env.APP_NAME}")
                }
            }
        }

        stage('Dependency-Check') {
            steps {
                script {
                    load('ci/dependencyCheck.groovy').dependencyCheck()
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                script {
                    load('ci/sonarQube.groovy').sonarQubeAnalysis()
                }
            }
        }

        stage('SonarQube Quality Gate') {
            steps {
                script {
                    load('ci/sonarQube.groovy').qualityGateCheck() 
                }
            }
        }

        stage('Docker image build') {
            steps {
                script {
                    load('ci/dockerImage.groovy').build()
                }
            }
        }

        stage('Docker image push') {
            steps {
                script {
                    load('ci/dockerImage.groovy').push()
                }
            }
        }

        stage('Image Analysis') {
            steps {
                script {
                    load('ci/imageAnalysis.groovy').analysis()
                }
            }
        }

        // stage('Modify Helm Repo') {
        //     steps {
        //         script {
        //             load('ci/modifyHelmRepo.groovy').run()
        //         }
        //     }
        // }

        // stage('Argo Deploy') {
        //     steps {
        //         script {
        //             load('ci/argoDeploy.groovy').run()
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