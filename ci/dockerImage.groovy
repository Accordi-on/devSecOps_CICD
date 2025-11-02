def build() {
    container('kaniko') {
        echo "🐳 [Docker Build] Building Docker image for ${env.APP_NAME}:${env.IMAGE_TAG} ..."
        sh """
            /kaniko/executor \
                --context /home/jenkins/agent/workspace/${env.JOB_NAME} \
                --dockerfile /home/jenkins/agent/workspace/${env.JOB_NAME}/Dockerfile \
                --no-push \
                --destination ${env.HARBOR_REGISTRY}/${env.PROJECT_NAME}/${env.SERVICE_NAME}:${env.IMAGE_TAG} \
                --tarPath /home/jenkins/agent/workspace/${env.JOB_NAME}/image.tar
        """
        echo "✅ [Docker Build] Image build complete."
    }
}

def push() {
    withCredentials([usernamePassword(credentialsId: 'harbor-token', usernameVariable: 'HARBOR_CREDENTIALS_USR', passwordVariable: 'HARBOR_CREDENTIALS_PSW')]) {

        container('jnlp') {
            echo "🔍 [Harbor Project] Checking project ${env.PROJECT_NAME} existence..."
            sh """
                set -e
                curl -skf -u "\$HARBOR_CREDENTIALS_USR:\$HARBOR_CREDENTIALS_PSW" \
                    "http://${env.HARBOR_REGISTRY}/api/v2.0/projects/${env.PROJECT_NAME}" >/dev/null 2>&1 \
                || curl -sk -X POST -u "\$HARBOR_CREDENTIALS_USR:\$HARBOR_CREDENTIALS_PSW" \
                    -H "Content-Type: application/json" \
                    -d '{ "project_name": "${env.PROJECT_NAME}", "public": false }' \
                    "http://${env.HARBOR_REGISTRY}/api/v2.0/projects"
            """
            echo "✅ [Harbor Project] Verified or created project ${env.PROJECT_NAME}."
        }

        container('crane') {
            withEnv(["CRANE_INSECURE=1"]) {
            echo "📤 [Image Push] Pushing image to Harbor registry..."
            sh """
                    set -e
                    crane auth login ${env.HARBOR_REGISTRY} \
                        --username \$HARBOR_CREDENTIALS_USR \
                        --password \$HARBOR_CREDENTIALS_PSW \

                    crane push /home/jenkins/agent/workspace/${env.JOB_NAME}/image.tar \
                        ${env.HARBOR_REGISTRY}/${env.PROJECT_NAME}/${env.SERVICE_NAME}:${env.IMAGE_TAG} \
            """
            echo "✅ [Image Push] Image pushed to ${env.HARBOR_REGISTRY}/${env.PROJECT_NAME}/${env.SERVICE_NAME}:${env.IMAGE_TAG}"
            }
        }
    }
}

return this