#!/bin/bash
# Kubernetes Diagnostic Script for AMQP Reactive Messaging Tests
# Captures comprehensive diagnostic information during test execution

set -e

NAMESPACE="${1:-default}"
OUTPUT_DIR="k8s-diagnostics-$(date +%Y%m%d-%H%M%S)"

echo "🔍 Starting Kubernetes diagnostics for namespace: $NAMESPACE"
echo "📁 Output directory: $OUTPUT_DIR"

mkdir -p "$OUTPUT_DIR"

# Function to capture with timestamp
capture() {
    local name=$1
    local cmd=$2
    echo "📝 Capturing: $name"
    echo "=== $name - $(date) ===" >> "$OUTPUT_DIR/$name.log"
    eval "$cmd" >> "$OUTPUT_DIR/$name.log" 2>&1 || echo "Failed to capture $name" >> "$OUTPUT_DIR/$name.log"
    echo "" >> "$OUTPUT_DIR/$name.log"
}

# Capture overall cluster state
capture "cluster-info" "kubectl cluster-info"
capture "nodes" "kubectl get nodes -o wide"

# Capture namespace resources
capture "all-resources" "kubectl get all -n $NAMESPACE -o wide"
capture "pods-yaml" "kubectl get pods -n $NAMESPACE -o yaml"
capture "services-yaml" "kubectl get services -n $NAMESPACE -o yaml"
capture "deployments-yaml" "kubectl get deployments -n $NAMESPACE -o yaml"

# Capture events (critical for debugging)
capture "events" "kubectl get events -n $NAMESPACE --sort-by='.lastTimestamp'"

# Capture pod details
echo "🔍 Capturing pod-specific details..."
for pod in $(kubectl get pods -n $NAMESPACE -o jsonpath='{.items[*].metadata.name}'); do
    echo "  📦 Processing pod: $pod"

    # Pod description
    capture "pod-$pod-describe" "kubectl describe pod $pod -n $NAMESPACE"

    # Pod logs (current)
    capture "pod-$pod-logs" "kubectl logs $pod -n $NAMESPACE --all-containers=true"

    # Pod logs (previous, if exists)
    kubectl logs $pod -n $NAMESPACE --previous --all-containers=true > "$OUTPUT_DIR/pod-$pod-logs-previous.log" 2>&1 || true

    # Container status
    capture "pod-$pod-status" "kubectl get pod $pod -n $NAMESPACE -o jsonpath='{.status}' | jq ."
done

# Capture Artemis-specific information
echo "🔍 Checking for Artemis deployment..."
if kubectl get deployment artemis -n $NAMESPACE &>/dev/null; then
    capture "artemis-deployment" "kubectl get deployment artemis -n $NAMESPACE -o yaml"

    ARTEMIS_POD=$(kubectl get pods -n $NAMESPACE -l app=artemis -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
    if [ -n "$ARTEMIS_POD" ]; then
        echo "  📦 Found Artemis pod: $ARTEMIS_POD"
        capture "artemis-logs" "kubectl logs $ARTEMIS_POD -n $NAMESPACE"
        capture "artemis-describe" "kubectl describe pod $ARTEMIS_POD -n $NAMESPACE"

        # Check Artemis port connectivity
        capture "artemis-endpoints" "kubectl get endpoints artemis -n $NAMESPACE -o yaml"
    fi
fi

# Capture ConfigMaps and Secrets (metadata only, not sensitive data)
capture "configmaps" "kubectl get configmaps -n $NAMESPACE"
capture "secrets" "kubectl get secrets -n $NAMESPACE"

# Capture persistent volume info if any
capture "pvcs" "kubectl get pvc -n $NAMESPACE -o wide"
capture "pvs" "kubectl get pv -o wide"

# Network policies
capture "network-policies" "kubectl get networkpolicies -n $NAMESPACE -o yaml"

# Resource usage
capture "top-nodes" "kubectl top nodes" || echo "Metrics server not available"
capture "top-pods" "kubectl top pods -n $NAMESPACE" || echo "Metrics server not available"

# Generate summary
cat > "$OUTPUT_DIR/SUMMARY.md" <<EOF
# Kubernetes Diagnostics Summary
**Captured**: $(date)
**Namespace**: $NAMESPACE

## Quick Check Commands

\`\`\`bash
# Watch pods in real-time
kubectl get pods -n $NAMESPACE -w

# Follow Artemis logs
kubectl logs -f deployment/artemis -n $NAMESPACE

# Follow application logs (update pod name)
kubectl logs -f <app-pod-name> -n $NAMESPACE

# Check service connectivity
kubectl get svc -n $NAMESPACE
kubectl get endpoints -n $NAMESPACE

# Check events
kubectl get events -n $NAMESPACE --sort-by='.lastTimestamp' --watch
\`\`\`

## Files Captured
$(ls -1 "$OUTPUT_DIR" | grep -v SUMMARY.md)
EOF

echo ""
echo "✅ Diagnostics captured successfully!"
echo "📁 Output directory: $OUTPUT_DIR"
echo "📄 Summary: $OUTPUT_DIR/SUMMARY.md"
echo ""
echo "💡 Quick commands for real-time monitoring:"
echo "   kubectl get pods -n $NAMESPACE -w"
echo "   kubectl logs -f deployment/artemis -n $NAMESPACE"
echo "   kubectl get events -n $NAMESPACE --watch"
