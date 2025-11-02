def dependencyCheck(){
    echo "🔍 [Dependency-Check] Running security scan for ${env.APP_NAME}..."
    def dcHome = tool name: 'dependencycheck', type: 'dependency-check'
    def dcData = "/home/jenkins/agent/workspace/.dependency-check"

    withCredentials([string(credentialsId: 'dependency-check', variable: 'NVD_API_KEY')]) {
        dir(env.APP_NAME) {
            sh """
                set -e
                mkdir -p ${dcData}
                export PATH=${dcHome}/bin:\$PATH

                dependency-check.sh \
                  --data ${dcData} \
                  --nvdApiKey "\$NVD_API_KEY" \
                  --scan . \
                  --format HTML \
                  --out dependency-check-report \
                  --project ${env.APP_NAME} \
                  --failOnCVSS 7

                echo "✅ [Dependency-Check] Completed for ${env.APP_NAME}"
            """
        }
    }
}
return this
