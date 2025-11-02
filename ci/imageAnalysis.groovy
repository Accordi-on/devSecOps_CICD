def analysis() {
    withCredentials([usernamePassword(credentialsId: 'harbor-token', usernameVariable: 'HARBOR_CREDENTIALS_USR', passwordVariable: 'HARBOR_CREDENTIALS_PSW')]) {
    container('trivy') {
                    echo '🛡 [Anchore] Running container image security scan...'
                    sh '''
                        set -euo pipefail 

                        IMAGE="${HARBOR_REGISTRY}/${env.PROJECT_NAME}/${env.SERVICE_NAME}:${IMAGE_TAG}"
                        REPORT="trivy-report.json"

                        echo "🔐 Scanning image (private registry) with Trivy: $IMAGE"
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

                        echo "IMAGE=${IMAGE}"            >  trivy-summary.txt
                        echo "STATUS=${STATUS}"         >> trivy-summary.txt
                        echo "REPORT_FILE=${REPORT}"    >> trivy-summary.txt

                        cat trivy-summary.txt

                        if [ "$STATUS" = "fail" ]; then
                        exit 1
                        fi

                    '''
                    
                }
    }

}