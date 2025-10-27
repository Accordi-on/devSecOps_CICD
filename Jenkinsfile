pipeline {
    agent {
        kubernetes {
            label 'jenkins-agent-k8s'
            defaultContainer 'jnlp'
            yaml """
apiVersion: v1
kind: Pod
metadata:
  labels:
    some-label: jenkins-agent
spec:
  containers:
    - name: jnlp
      image: jenkins/inbound-agent:latest
      args: ['\$(JENKINS_SECRET)', '\$(JENKINS_NAME)']
      tty: true
      volumeMounts:
        - name: workspace-volume
          mountPath: /home/jenkins/agent/workspace
        - name: system-ca
          mountPath: /etc/ssl/certs/ca-certificates.crt
          subPath: ca-certificates.crt

    - name: kaniko
      image: gcr.io/kaniko-project/executor:debug
      command: ["sleep"]
      args: ["infinity"]
      tty: true
      volumeMounts:
        - name: workspace-volume
          mountPath: /home/jenkins/agent/workspace
        - name: system-ca
          mountPath: /etc/ssl/certs/ca-certificates.crt
          subPath: ca-certificates.crt
        - name: kaniko-docker-config
          mountPath: /kaniko/.docker
    - name: crane
      image: gcr.io/go-containerregistry/crane:debug
      command: ["sleep"]
      args: ["infinity"]
      tty: true
      volumeMounts:
        - name: workspace-volume
          mountPath: /home/jenkins/agent/workspace
        - name: system-ca
          mountPath: /etc/ssl/certs/ca-certificates.crt
          subPath: ca-certificates.crt
        - name: kaniko-docker-config
          mountPath: /kaniko/.docker
  volumes:
    - name: workspace-volume
      emptyDir: {}
    - name: system-ca
      configMap:
        name: system-ca
    - name: kaniko-docker-config
      projected:
        sources:
          - secret:
              name: harbor-dockerconfig
              items:
                - key: .dockerconfigjson
                  path: config.json
"""           
            }
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
            HARBOR_PROJECT  = "${env.JOB_NAME}"
            ARGOCD_APP      = "${env.JOB_NAME}"
    }
    stages {
        stage('Git Clone') {
            steps {
                echo "🌐 [Git Clone] Cloning repository from. ${env.GIT_URL}..."
                sh """
                    rm -rf ${APP_NAME} || true 
                    git clone ${GIT_URL} ${APP_NAME}
                    echo "✅ [Git Clone] Repository cloned successfully."
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
            steps {
                container('kaniko') {
                    echo "🐳 [Docker Build] Building Docker image for ${APP_NAME}:${IMAGE_TAG} ..."
                    sh """
                        /kaniko/executor \
                            --context /home/jenkins/agent/workspace/${JOB_NAME}/${APP_NAME} \
                            --dockerfile /home/jenkins/agent/workspace/${JOB_NAME}/${APP_NAME}/Dockerfile \
                            --no-push \
                            --destination ${HARBOR_REGISTRY}/${JOB_NAME}/${APP_NAME}:${IMAGE_TAG} \
                            --tarPath /home/jenkins/agent/workspace/${JOB_NAME}/image.tar
                    """
                    echo "✅ [Docker Build] Image build complete."
                }
            }
        }

        stage('Docker image push to Harbor') {
            environment{
                HARBOR_CREDENTIALS = credentials('harbor-credentials')
            }
            steps {
                container('jnlp'){
                    sh '''
                        # 1) 프로젝트 존재 확인, 없으면~ 생성
                        curl -skf -u "$HARBOR_CREDENTIALS_USR:$HARBOR_CREDENTIALS_PSW" \\
                        "https://${HARBOR_REGISTRY}/api/v2.0/projects/${HARBOR_PROJECT}" >/dev/null 2>&1 \\
                        || curl -sk -X POST -u "$HARBOR_CREDENTIALS_USR:$HARBOR_CREDENTIALS_PSW" \\
                        -H "Content-Type: application/json" \\
                        -d '{ "project_name": "${HARBOR_PROJECT}", "public": false }' \\
                        "https://${HARBOR_REGISTRY}/api/v2.0/projects"
                    '''
                    echo "✅ [Harbor Project] Verified or created project ${HARBOR_PROJECT} in Harbor."
                }
                container('crane') {
                    echo "📤 [Image Push] Pushing image to Harbor registry..."
                    sh '''
                        crane auth login ${HARBOR_REGISTRY} \
                            --username $HARBOR_CREDENTIALS_USR \
                            --password $HARBOR_CREDENTIALS_PSW
                        crane push /home/jenkins/agent/workspace/${JOB_NAME}/image.tar ${HARBOR_REGISTRY}/${HARBOR_PROJECT}/${APP_NAME}:${IMAGE_TAG}
                    '''
                    echo "✅ [Image Push] Image pushed to ${HARBOR_REGISTRY}/${HARBOR_PROJECT}/${APP_NAME}:${IMAGE_TAG}"
                }
            }
        }

        stage('Anchore analyse') {
            agent{
                kubernetes{
                    label 'trivy-agent'
                    defaultContainer 'trivy'
yaml """
apiVersion: v1
kind: Pod
metadata:
  labels:
    some-label: trivy-agent
spec:
    containers:
      - name: trivy
        image: aquasec/trivy:latest
        command: ["sleep"]
        args: ["infinity"]
        tty: true
        volumeMounts:
          - name: workspace-volume
            mountPath: /home/jenkins/agent/workspace
          - name: system-ca
            mountPath: /etc/ssl/certs/ca-certificates.crt
            subPath: ca-certificates.crt
    volumes:
      - name: workspace-volume
        emptyDir: {}
      - name: system-ca
        configMap:
          name: system-ca
"""
                }
            }
            steps {
                container('trivy') {
                    echo '🛡 [Anchore] Running container image security scan...'
                    withCredentials([usernamePassword(credentialsId: 'harbor-credentials',
                                                    usernameVariable: 'HARBOR_USER',
                                                    passwordVariable: 'HARBOR_PASS')]) {
                    sh '''
                        set -euo pipefail

                        IMAGE="${REGISTRY}/${PROJECT}/${IMAGE}:${TAG}"
                        REPORT="trivy-report.json"
                        # DB 미리 받기 (옵션) — 네트워크/캐시 상황에 따라 주석 처리 가능
                        echo "⚙️ Downloading/updating Trivy DB (this speeds up subsequent scans)..."
                        trivy --download-db-only || echo "⚠️ trivy DB download failed (continue anyway)"

                        echo "🔐 Scanning image (private registry) with Trivy: $IMAGE"
                        # --exit-code 1 : 지정한 심각도(HIGH,CRITICAL) 이상 발견 시 exit code 1로 종료 (빌드 실패)
                        # --severity : 검사할 심각도 레벨
                        # --username/--password : private registry 인증
                        # --format json : JSON 출력 (Jenkins artifact로 남김)
                        # --timeout : 네트워크/레지스트리 느릴때 대비 (원하면 조정)
                        trivy image \
                        --username "$HARBOR_USER" \
                        --password "$HARBOR_PASS" \
                        --format json \
                        --output "$REPORT" \
                        --exit-code 1 \
                        --severity HIGH,CRITICAL \
                        --timeout 5m \
                        "$IMAGE" || true

                        # trivy가 exit-code 1로 실패시에도 리포트를 남기고, 후속 로직에서 검사한다.
                        echo "📄 Trivy report:"
                        if [ -f "$REPORT" ]; then
                        jq '.' "$REPORT" || cat "$REPORT"
                        else
                        echo "⚠️ No report generated."
                        fi

                        # 간단한 품질게이트 (Fail 판정 시 파이프라인 실패)
                        if [ -f "$REPORT" ]; then
                        # trivy JSON 구조에서 HIGH/CRITICAL 취약점 개수를 추출 (safe parsing)
                        CRITICAL_COUNT=$(jq '[.Results[].Vulnerabilities[]? | select(.Severity=="CRITICAL")] | length' "$REPORT" || echo 0)
                        HIGH_COUNT=$(jq '[.Results[].Vulnerabilities[]? | select(.Severity=="HIGH")] | length' "$REPORT" || echo 0)
                        echo "🔎 Found HIGH: $HIGH_COUNT, CRITICAL: $CRITICAL_COUNT"

                        if [ "$((CRITICAL_COUNT + HIGH_COUNT))" -gt 0 ]; then
                            echo "❌ Trivy found HIGH/CRITICAL vulnerabilities. Failing the build."
                            # 아티팩트는 남기고 종료
                            exit 1
                        else
                            echo "✅ No HIGH/CRITICAL vulnerabilities found."
                        fi
                        else
                        echo "⚠️ Report missing — treating as failure to be safe."
                        exit 1
                        fi
                    '''
                    }
                    
                }
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
