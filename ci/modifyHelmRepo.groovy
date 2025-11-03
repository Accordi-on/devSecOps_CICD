def gitPush() {
    dir("helm") {
            withCredentials([usernamePassword(credentialsId: 'gitea-token', usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASS')]) {
                sh '''#!/bin/bash
            set -e

            echo '🧭 [Git] Checkout main...'
            git fetch origin --prune
            git checkout -B main origin/main

            echo '📝 [Git] Set identity...'
            git config user.name "jenkins-bot"
            git config user.email "jenkins-bot@accordi-on.kro.kr"

            echo '🔀 [Git] Merge dev -> main ...'
            git fetch origin ${BRANCH_NAME:-dev}:${BRANCH_NAME:-dev}
            git merge ${BRANCH_NAME:-dev} --no-edit

            echo '📝 [Helm Repo] Updating Helm chart values...'
            sed -i "s|^  repository: .*|  repository: ${HARBOR_REGISTRY}/${env.PROJECT_NAME}/${env.SERVICE_NAME}|" values.yaml
            sed -i "s|^  tag: .*|  tag: ${IMAGE_TAG}|" values.yaml

            echo '📝 [Git] Commit...'
            git add values.yaml
            git commit -m "chore(ci): merge ${BRANCH_NAME:-dev} into main & update image to ${HARBOR_REGISTRY}//${env.PROJECT_NAME}/${env.SERVICE_NAME}:${IMAGE_TAG}"

            echo '🚀 [Git] Push main...'
            git push http://"${GIT_USER}":"${GIT_PASS}"@gitea.service.accordi-on.com/Accordi-on/${env.PROJECT_NAME}.git main
            '''
            }

    }
}

return this