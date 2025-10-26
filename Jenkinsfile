pipeline {
    agent{
        kubernetes{
        defaultContainer 'builder'
            yaml """
apiVersion: v1
kind: Pod
spec:
  volumes:
  - name: work
    emptyDir: {}

  containers:
  - name: builder
    image: alpine:3.20
    command:
    - /bin/sh
    args:
    - -c
    - |
      echo "builder ready"
      # keep pod alive so Jenkins can exec into it
      sleep 36000
    volumeMounts:
    - name: work
      mountPath: /workspace
    tty: true

  - name: kaniko
    image: gcr.io/kaniko-project/executor:debug
    command:
    - /busybox/sh
    args:
    - -c
    - |
      # Jenkins 'sh' step expects /bin/sh to exist.
      mkdir -p /bin
      ln -sf /busybox/sh /bin/sh
      echo "kaniko ready"
      sleep 36000
    volumeMounts:
    - name: work
      mountPath: /workspace
    tty: true

  - name: crane
    image: gcr.io/go-containerregistry/crane:debug
    command:
    - /bin/sh
    args:
    - -c
    - |
      echo "crane ready"
      sleep 36000
    volumeMounts:
    - name: work
      mountPath: /workspace
    tty: true
"""
        }
    }
    tools {
        nodejs 'nodejs'
    }

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
            IMAGE_FULL      = "${HARBOR_REGISTRY}/${HARBOR_PROJECT}/${APP_NAME}:${IMAGE_TAG}"

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
            steps {
                container('kaniko') {
                    sh '''
                        echo "🏗 building with kaniko..."
                        /kaniko/executor \
                        --dockerfile=Dockerfile \
                        --context=${WORKSPACE} \
                        --destination=${REGISTRY}/${PROJECT}/${IMAGE}:${TAG} \
                        --cache=true
                    '''
                }
            }
        }


        stage('Docker image push to Harbor') {
            steps {
                container('crane') {
                    withCredentials([
                        usernamePassword(
                            credentialsId: 'harbor-credentials',
                            usernameVariable: 'HARBOR_USERNAME',
                            passwordVariable: 'HARBOR_PASSWORD'
                        )
                    ]) {
                        sh """
                            echo '🔐 crane login'
                            crane auth login ${HARBOR_REGISTRY} \
                              -u "${HARBOR_USERNAME}" \
                              -p "${HARBOR_PASSWORD}" \
                              --insecure --tls-verify=false

                            echo '📤 pushing ${IMAGE_FULL}'
                            crane push /workspace/image.tar ${IMAGE_FULL} \
                              --insecure --tls-verify=false

                            echo '📤 also pushing :latest'
                            crane push /workspace/image.tar ${HARBOR_REGISTRY}/${HARBOR_PROJECT}/${APP_NAME}:latest \
                              --insecure --tls-verify=false

                            echo '✅ pushed to Harbor'
                        """
                    }
                }
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
