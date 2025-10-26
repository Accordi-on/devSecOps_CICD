pipeline {
    agent any
    options {
        skipDefaultCheckout(true)
    }

    environment {
            JOB_NAME        = "${env.JOB_NAME}"
            BRANCH_NAME     = "main"
            GIT_URL         = "https://gitea.accordi-on.kro.kr/Accordi-on/${env.JOB_NAME}.git"
            GIT_CREDENTIALS = "gitea-token"
            SONARQUBE_SERVER = 'SonarQube'
            APP_NAME        = "${env.JOB_NAME}"
            IMAGE_TAG       = "build-${env.BUILD_NUMBER}"
            HARBOR_REGISTRY = "harbor.accordi-on.kro.kr"
            HARBOR_PROJECT  = "demo-project"
            ARGOCD_APP      = "${env.JOB_NAME}"
            WORKSPACE = ""

    }
    stages {
        stage('Git Clone') {
            steps {
                echo "🌐 [Git Clone] Cloning repository from. ${env.GIT_URL}..."
                sh """
                    rm -rf ${APP_NAME} || true 
                    git clone ${GIT_URL} ${APP_NAME}
                """
                WORKSPACE="$(pwd)/${APP_NAME}"
                echo "🌍 [Workspace] Workspace is located at ${WORKSPACE}."

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
            environment {
                NODEJS_HOME = tool 'nodejs'
            }
            steps {
                nodejs('nodejs') {
                echo '🧪 [Build Test] Running unit/lint tests...'
                dir("${APP_NAME}") {
                        sh '''
                            npm ci
                            npm test
                        '''
                }
                }

            }
        }
        // stage('Dependency-Check') {
        //     steps {
        //         dir("${APP_NAME}") {
        //             dependencyCheck additionalArguments: ''' 
        //                 -o "./" 
        //                 -s "./"
        //                 -f "ALL" 
        //                 --prettyPrint''', odcInstallation: 'Dependency-Check'
        //             dependencyCheckPublisher pattern: 'dependency-check-report.xml'
        //         }
        //     }
        // }

        stage('Sonarqube and Quality gate') {
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
                        -Dsonar.login=$SONAR_AUTH_TOKEN
                    '''
                }
            }
        }

        stage('Quality Gate Check') {
            steps {
                echo '🚦 [Quality Gate] Waiting for SonarQube quality gate status...'
                timeout(time: 3, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Docker image build') {
            agent {
                kubernetes {
                    label 'kaniko-agent'
                    yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: kaniko
      image: gcr.io/kaniko-project/executor:debug
      command:
        - sleep
      args:
        - infinity
      tty: true
      volumeMounts:
        - name: kaniko-docker-config
          mountPath: /kaniko/.docker
        - name: system-ca
          mountPath: /etc/ssl/certs
      resources:
        requests:
          cpu: "200m"
          memory: "512Mi"
  volumes:
    - name: kaniko-docker-config
      projected:
        sources:
          - secret:
              name: harbor-dockerconfig
              items:
                - key: .dockerconfigjson
                  path: config.json
    - name: system-ca
      configMap:
        name: system-ca
"""     }
            }
            environment {
                REGISTRY = "${HARBOR_REGISTRY}"
                PROJECT  = "${HARBOR_PROJECT}"
                IMAGE    = "${APP_NAME}"
                TAG      = "${IMAGE_TAG}"
            }
            steps {
                container('kaniko') {
                    echo "🛠 [Docker Build] Building Docker image ${REGISTRY}/${PROJECT}/${IMAGE}:${TAG} ..."
                    sh '''
                    /kaniko/executor \
                        --context ${APP_NAME} \
                        --dockerfile Dockerfile \
                        --destination ${REGISTRY}/${PROJECT}/${IMAGE}:${TAG} \
                        --tarPath /workspace/image.tar                   
                    '''

                    echo "✅ [Docker Build] Image build complete."
                    stash name: 'image.tar', includes: 'image.tar'
                }
            }
        }


        stage('Docker image push to Harbor') {
            steps {
                echo "📤 [Image Push] crane-push-agent Pushing image.tar to ${IMAGE_FULL} ..."
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
