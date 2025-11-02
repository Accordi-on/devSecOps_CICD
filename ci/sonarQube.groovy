def sonarQubeAnalysis() {
    def SCANNER_HOME = tool 'SonarQubeScanner'
    echo '📊 [SonarQube] Running code analysis and sending results...'
    withSonarQubeEnv('sonarqube') {
        sh """
        "${SCANNER_HOME}/bin/sonar-scanner" \
            -Dsonar.projectKey=${APP_NAME} \
            -Dsonar.projectName=${APP_NAME} \
            -Dsonar.sources=. \
            -Dsonar.host.url=$SONAR_HOST_URL \
            -Dsonar.login=$SONAR_AUTH_TOKEN \
            -Dsonar.exclusions=helm/**,charts/**,**/templates/**,**/values.yaml,${APP_NAME}/dependency-check-report/** \
        """
    }
}

def qualityGateCheck(){
                echo '🚦 [Quality Gate] Waiting for SonarQube quality gate status...'
                timeout(time: 3, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
    }
}

return this