pipeline {
    agent any
    tools {
        nodejs 'nodejs'
        'hudson.plugins.sonar.SonarRunnerInstallation' 'SonarQubeScanner'
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
                    label 'kaniko-build-agent'
                    defaultContainer 'kaniko'
                    yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: kaniko
    image: gcr.io/kaniko-project/executor:latest
    command: ["/busybox/sh"]
    tty: true
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
            }

            steps {
                echo "🐳 [Docker Build] Building image for ${IMAGE_FULL} (no push yet)..."

                container('kaniko') {
                    sh """
                        # Kaniko build -> tar export
                        /kaniko/executor \
                        --dockerfile=Dockerfile \
                        --context=${WORKSPACE} \
                        --no-push \
                        --tarPath /workspace/image.tar

                        ls -lh /workspace
                        echo '✅ Build complete, image.tar prepared'
                    """

                    // tar 파일을 워크스페이스로 복사해서 다음 stage에서도 접근 가능하게
                    sh "cp /workspace/image.tar ${WORKSPACE}/image.tar"
                }

                // Jenkins 아티팩트로도 저장해두면, 이후 stage가 다른 agent여도 받을 수 있음
                archiveArtifacts artifacts: 'image.tar', fingerprint: true
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
    command: ["/busybox/sh"]
    tty: true
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

                // 아까 build 단계에서 archiveArtifacts 한 걸 복구
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
