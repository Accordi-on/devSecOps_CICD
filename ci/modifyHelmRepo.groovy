def gitPush() {
    dir("helm") {
        withCredentials([usernamePassword(credentialsId: 'gitea-token', usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASS')]) {
        sh """
            set -e

            echo '🧭 [Git] Checkout main...'
            git fetch origin
            git checkout main
            git pull origin main

            echo '🔀 [Git] Merge dev -> main ...'
            git merge origin/${env.BRANCH_NAME} --no-edit || true

            echo '📝 [Helm Repo] Updating Helm chart values...'
            sed -i "s|^  repository: .*|  repository: ${HARBOR_REGISTRY}/${env.PROJECT_NAME}/${env.SERVICE_NAME}|" values.yaml
            sed -i "s|^  tag: .*|  tag: ${IMAGE_TAG}|" values.yaml

            echo '📝 [Git] Preparing commit...'
            git config user.name "jenkins-bot"
            git config user.email "jenkins-bot@accordi-on.kro.kr"

            git add values.yaml

            git commit -m "chore(ci): merge ${env.BRANCH_NAME} into main & update image to ${HARBOR_REGISTRY}/${env.PROJECT_NAME}/${env.SERVICE_NAME}:${IMAGE_TAG}" \
            || echo "ℹ️ no changes to commit"

            echo "🚀 [Git] Pushing main to remote..."
            git push http://${GIT_USER}:${GIT_PASS}@gitea.service.accordi-on.com/Accordi-on/${env.APP_NAME}.git main
            git checkout ${env.BRANCH_NAME}
        """
        }
    }
}

return this