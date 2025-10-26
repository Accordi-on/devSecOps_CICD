pipeline {
    agent any
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
            agent {
                kubernetes {
                    label 'kaniko-agent'
                    yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: kaniko
      image: gcr.io/kaniko-project/executor:latest
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
    - name: jnlp
      image: jenkins/inbound-agent:latest
      volumeMounts:
        - name: system-ca
          mountPath: /etc/ssl/certs
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
                                        sh """
                    /kaniko/executor --context=dir:///workspace/${APP_NAME}/ --dockerfile=/workspace/${APP_NAME}/Dockerfile --destination=${REGISTRY}/${PROJECT}/${IMAGE}:${TAG} --destination=${REGISTRY}/${PROJECT}/${IMAGE}:latest --tarPath=/workspace/image.tar
                                        """
                    echo "✅ [Docker Build] Image build complete."
                    stash name: 'image.tar', includes: 'image.tar'
                }
            }
        }


        stage('Docker image push to Harbor') {
            agent {
                kubernetes {
                    label 'crane-push-agent'
                    defaultContainer 'crane'
                    yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: crane
    image: gcr.io/go-containerregistry/crane:debug
    volumeMounts:
    - name: work
      mountPath: /workspace
  volumes:
  - name: work
    emptyDir: {}
"""
                }
            }
            environment {
                IMAGE_FULL = "${HARBOR_REGISTRY}/${HARBOR_PROJECT}/${APP_NAME}:${IMAGE_TAG}"
                REGISTRY   = "${HARBOR_REGISTRY}"
            }
            steps {
                echo "📤 [Image Push] Pushing image.tar to ${IMAGE_FULL} ..."

                unstashOrUnarchive('image.tar')
                container('crane') {
                    withCredentials([
                        usernamePassword(
                            credentialsId: 'harbor-credentials',
                            usernameVariable: 'HARBOR_USERNAME',
                            passwordVariable: 'HARBOR_PASSWORD'
                        )
                    ]) {
                        sh """
                            ls -lh .

                            echo '🔐 Logging in to Harbor registry...'

                            # crane auth 는 env변수를 받거나 --auth 기본 옵션 사용 가능
                            # 여기서는 간단히 crane push 에 직접 전달
                            echo '🚚 Pushing...'
                            crane push image.tar ${IMAGE_FULL} --insecure --tls-verify=false --username "\${HARBOR_USERNAME}" --password "\${HARBOR_PASSWORD}"

                            # latest 태그도 밀고 싶으면 한 번 더
                            crane push image.tar ${HARBOR_REGISTRY}/${HARBOR_PROJECT}/${APP_NAME}:latest --insecure --tls-verify=false --username "\${HARBOR_USERNAME}" --password "\${HARBOR_PASSWORD}"

                            echo '✅ Push complete: ${IMAGE_FULL}'
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
