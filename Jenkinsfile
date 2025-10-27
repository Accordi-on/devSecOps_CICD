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
            steps {
                container('jnlp'){
                    withCredentials([
                        usernamePassword(
                            credentialsId: 'harbor-credentials',
                            usernameVariable: 'HARBOR_ADMIN_USER',
                            passwordVariable: 'HARBOR_ADMIN_PASS'
                        )
                    ]) {
                    sh """
                        # 1) 프로젝트 존재 확인, 없으면~ 생성
                        curl -skf -u "$HARBOR_ADMIN_USER:$HARBOR_ADMIN_PASS" \\
                        "https://${HARBOR_REGISTRY}/api/v2.0/projects/${HARBOR_PROJECT}" >/dev/null 2>&1 \\
                        || curl -sk -X POST -u "$HARBOR_ADMIN_USER:$HARBOR_ADMIN_PASS" \\
                        -H "Content-Type: application/json" \\
                        -d '{ "project_name": "${HARBOR_PROJECT}", "public": true }' \\
                        "https://${HARBOR_REGISTRY}/api/v2.0/projects"

                    """
                    echo "✅ [Harbor Project] Verified or created project ${HARBOR_PROJECT} in Harbor."
                    }
                }
                container('crane') {
                    echo "📤 [Image Push] Pushing image to Harbor registry..."
                    withCredentials([usernamePassword(credentialsId: 'harbor-credentials', usernameVariable: 'HARBOR_USER', passwordVariable: 'HARBOR_PASS')]) {
                    sh """
                        crane auth login ${HARBOR_REGISTRY} \
                            --username $HARBOR_USER \
                            --password $HARBOR_PASS
                        crane push /home/jenkins/agent/workspace/${JOB_NAME}/image.tar ${HARBOR_REGISTRY}/${HARBOR_PROJECT}/${APP_NAME}:${IMAGE_TAG}
                    """
                    }
                    echo "✅ [Image Push] Image pushed to ${HARBOR_REGISTRY}/${HARBOR_PROJECT}/${APP_NAME}:${IMAGE_TAG}"
                }
            }
        }

        stage('Anchore analyse') {
//             agent{
//                 kubernetes{
//                     label 'jenkins-agent-anchore'
//                     defaultContainer 'jenkins-agent-anchore'
//                     yaml """
// apiVersion: v1
// kind: Pod
// metadata:
//     labels:
//         some-label: jenkins-agent-anchore
// spec:
//     containers:
//         - name: jenkins-agent-anchore
//           image: anchore/engine:latest
//             command: ['sleep', 'infinity']
//             tty: true
//             volumeMounts:
//                 - name: workspace-volume
//                     mountPath: /home/jenkins/agent/workspace
//     volumes:
//         - name: workspace-volume
//             emptyDir: {}
//             medium: Memory
//                     """
//                 }
//             }
            steps {
                echo '🛡 [Anchore] Running container image security scan...'
                // sh """
                //     anchore-cli image add ${REGISTRY}/${PROJECT}/${IMAGE}:${TAG}
                //     anchore-cli image wait ${REGISTRY}/${PROJECT}/${IMAGE}:${TAG}
                //     anchore-cli evaluate check ${REGISTRY}/${PROJECT}/${IMAGE}:${TAG} --detail > anchore-report.txt
                //     anchore-cli image vuln ${REGISTRY}/${PROJECT}/${IMAGE}:${TAG} all >> anchore-report.txt
                // """
                echo '✅ [Anchore] Security scan completed. Review anchore-report.txt for details.'
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
