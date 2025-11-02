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
        stage('Image Tag') {
            steps {
                script {
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
            environment {
                SCANNER_HOME = tool 'SonarQubeScanner'
            }
            steps {
                echo '📊 [SonarQube] Running code analysis and sending results...'
                withSonarQubeEnv('SonarQube') {
                    sh '''
                    "${SCANNER_HOME}/bin/sonar-scanner" \
                        -Dsonar.projectKey=${APP_NAME} \
                        -Dsonar.projectName=${APP_NAME} \
                        -Dsonar.sources=. \
                        -Dsonar.host.url=$SONAR_HOST_URL \
                        -Dsonar.login=$SONAR_AUTH_TOKEN \
                        -Dsonar.exclusions=helm/**,charts/**,**/templates/**,**/values.yaml,${APP_NAME}/dependency-check-report/** 
                    '''
                }
            }
        }
        stage('SonarQube Quality Gate') {
            steps {
                echo '🚦 [Quality Gate] Waiting for SonarQube quality gate status...'
                timeout(time: 3, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }
        // stage('Docker image build') {
        //     steps {
        //         script {
        //             load('ci/dockerImageBuild.groovy').run()
        //         }
        //     }
        // }

        // stage('Docker image push') {
        //     steps {
        //         script {
        //             load('ci/dockerImagePush.groovy').run()
        //         }
        //     }
        // }

        // stage('Image Analysis') {
        //     steps {
        //         script {
        //             load('ci/imageAnalysis.groovy').run()
        //         }
        //     }
        // }

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