def deploy(){
        withCredentials([string(credentialsId: 'argocd-token', variable: 'ARGOCD_TOKEN')]) {
        def exists = sh(
              script: """
                set -e
                curl -sk -o /dev/null -w "%{http_code}" \
                  -H "Authorization: Bearer ${ARGOCD_TOKEN}" \
                  ${env.ARGOCD_URL}/api/v1/applications/${env.APP_NAME}
                """,
              returnStdout: true
        ).trim()

        echo "🔍 [ArgoCD] Application existence check returned HTTP status: ${exists}"

        if (exists == '403'){
            sh """
                echo "❌ exists application"
                set -e

                echo "🚀 [ArgoCD] Creating application: ${env.APP_NAME}"

                curl -sk -X POST \
                -H "Content-Type: application/json" \
                -H "Authorization: Bearer ${ARGOCD_TOKEN}" \
                -d '{
                    "metadata": {
                    "name": "'"${env.APP_NAME}"'",
                    "namespace": "argocd"
                    },
                    "spec": {
                    "project": "default",
                    "source": {
                        "repoURL": "http://gitea.service.accordi-on.com/Accordi-on/"${env.APP_NAME}".git",
                        "targetRevision": "main",
                        "path": "helm"
                    },
                    "destination": {
                        "server": "https://kubernetes.default.svc",
                        "namespace": "'"${env.PROJECT_NAME}"'"
                    },
                    "syncPolicy": {
                        "automated": {
                        "prune": true,
                        "selfHeal": true
                        },
                        "syncOptions": [
                        "CreateNamespace=true"
                        ]
                    }
                    }
                }' \
                ${env.ARGOCD_URL}/api/v1/applications
            """
        }
        sh """
            set -e
            echo "🚀 [ArgoCD] Deploying application: ${env.APP_NAME}"
            curl -sk -X POST \\
            -H "Authorization: Bearer ${ARGOCD_TOKEN}" \\
            ${env.ARGOCD_URL}/api/v1/applications/${env.APP_NAME}/sync
        """
    }

}

return this