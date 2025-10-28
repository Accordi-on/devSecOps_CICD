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
            GIT_CREDENTIALS = credentials("gitea-token")
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
                        -Dsonar.exclusions=helm/**,charts/**,**/templates/**,**/values.yaml
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

        stage('Docker image push') {
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

        stage('Image Analysis') {
            environment{
                HARBOR_CREDENTIALS = credentials('harbor-credentials')
            }
            steps {
                container('trivy') {
                    echo '🛡 [Anchore] Running container image security scan...'
                    sh '''
                        set -euo pipefail 

                        IMAGE="${HARBOR_REGISTRY}/${HARBOR_PROJECT}/${APP_NAME}:${IMAGE_TAG}"
                        REPORT="trivy-report.json"

                        echo "🔐 Scanning image (private registry) with Trivy: $IMAGE"

                        # Trivy로 이미지 스캔 (HIGH/CRITICAL만 보고서에 수집)
                        # --exit-code 0 으로 두고, 나중에 우리가 직접 보고서 분석해서 fail 여부 결정
                        trivy image \
                        --username "$HARBOR_CREDENTIALS_USR" \
                        --password "$HARBOR_CREDENTIALS_PSW" \
                        --format json \
                        --output "$REPORT" \
                        --exit-code 0 \
                        --severity HIGH,CRITICAL \
                        --timeout 5m \
                        "$IMAGE" 

                        echo "📄 Trivy report (first 200 lines):"
                        if [ -f "$REPORT" ]; then
                        head -n 200 "$REPORT" || true
                        else
                        echo "⚠️ No report generated."
                        echo "❌ Treating this as a failure for safety."
                        exit 1
                        fi

                        echo "🔎 Checking for HIGH or CRITICAL vulnerabilities in $IMAGE ..."
                        if grep -q '"Severity":"CRITICAL"' "$REPORT" || grep -q '"Severity":"HIGH"' "$REPORT"; then
                        echo "❌ HIGH/CRITICAL vulnerabilities found in $IMAGE"
                        STATUS="fail"
                        else
                        echo "✅ No HIGH/CRITICAL vulnerabilities in $IMAGE"
                        STATUS="pass"
                        fi

                        # 결과 요약 저장 (Jenkins artifact로 남길 수 있게)
                        echo "IMAGE=${IMAGE}"            >  trivy-summary.txt
                        echo "STATUS=${STATUS}"         >> trivy-summary.txt
                        echo "REPORT_FILE=${REPORT}"    >> trivy-summary.txt

                        cat trivy-summary.txt

                        # 최종 품질 게이트: fail이면 빌드 중단
                        if [ "$STATUS" = "fail" ]; then
                        exit 1
                        fi

                    '''
                    
                }
            }
        }

        stage('Modify Helm Repo') {
            steps {
                echo '📝 [Helm Repo] Updating Helm chart values (image.tag, etc.)...'
                dir("${APP_NAME}/helm") {
                        sh '''
                            set -e

                            echo '📝 [Helm Repo] Updating Helm chart values...'

                            # 우리가 배포 기준으로 삼는 브랜치로 이동 (예: main)
                            git checkout ${BRANCH_NAME}

                            # 최신 원격 반영 (안 하면 push에서 뒤쳐졌다고 막힐 수 있음)
                            git fetch origin
                            git pull origin ${BRANCH_NAME}

                            # values.yaml 이미지 정보 업데이트
                            sed -i "s|^  repository: .*|  repository: ${HARBOR_REGISTRY}/${HARBOR_PROJECT}/${APP_NAME}|" values.yaml
                            sed -i "s|^  tag: .*|  tag: ${IMAGE_TAG}|" values.yaml

                            echo '📝 [Git] Preparing commit...'
                            git config user.name "jenkins-bot"
                            git config user.email "jenkins-bot@accordi-on.kro.kr"

                            git add values.yaml

                            # 변경이 없으면 커밋 실패(exit 1)하니까 방어
                            git commit -m "chore(ci): update image to ${HARBOR_REGISTRY}/${HARBOR_PROJECT}/${APP_NAME}:${IMAGE_TAG}" || echo "no changes to commit"

                            echo '🌿 [Git] Preparing prod branch...'

                            # prod 브랜치를 현재 ${BRANCH_NAME} 커밋으로 fast-forward 시킴
                            git branch -f prod ${BRANCH_NAME}
                            git checkout prod

                            echo "🚀 [Git] Pushing prod branch to remote..."
                            git push https://${GIT_CREDENTIALS_USR}:${GIT_CREDENTIALS_PSW}@gitea.accordi-on.kro.kr/Accordi-on/test.git prod --force

                            echo "✅ [Helm Repo] values.yaml updated, committed, and pushed to prod."
                        '''
                }

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
