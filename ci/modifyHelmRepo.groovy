def gitPush() {
    dir("${APP_NAME}/helm") {
        sh """
            set -e

            echo '🧭 [Git] Checkout main...'
            git fetch origin
            git checkout main
            git pull origin main

            echo '🔀 [Git] Merge dev -> main ...'
            git merge origin/dev --no-edit || true

            echo '📝 [Helm Repo] Updating Helm chart values...'
            sed -i "s|^  repository: .*|  repository: ${HARBOR_REGISTRY}/${env.PROJECT_NAME}/${env.SERVICE_NAME}|" values.yaml
            sed -i "s|^  tag: .*|  tag: ${IMAGE_TAG}|" values.yaml

            echo '📝 [Git] Preparing commit...'
            git config user.name "jenkins-bot"
            git config user.email "jenkins-bot@accordi-on.kro.kr"

            git add values.yaml

            git commit -m "chore(ci): merge dev into main & update image to ${HARBOR_REGISTRY}/${env.PROJECT_NAME}/${env.SERVICE_NAME}:${IMAGE_TAG}" \
            || echo "ℹ️ no changes to commit"

            echo "🚀 [Git] Pushing main to remote..."
            git push http://${GIT_CREDENTIALS_USR}:${GIT_CREDENTIALS_PSW}@gitea.accordi-on.kro.kr/Accordi-on/test.git main
        """
    }
}

return this