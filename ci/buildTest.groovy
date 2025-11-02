def run(app){
    def NODEJS_HOME = tool 'nodejs'
    echo "🧪 [Build Test] Running unit/lint tests for ${app}..."
    dir(app) {
        sh """
            export PATH=${NODEJS_HOME}/bin:\$PATH
            npm ci
            npm test
        """
    }
}

return this