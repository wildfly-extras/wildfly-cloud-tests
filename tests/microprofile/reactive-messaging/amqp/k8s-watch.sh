#!/bin/bash
# Real-time Kubernetes monitoring for AMQP tests
# Run this in a separate terminal while executing tests

NAMESPACE="${1:-default}"

echo "👀 Real-time Kubernetes monitoring for namespace: $NAMESPACE"
echo "Press Ctrl+C to stop"
echo ""

# Function to run commands in a loop with clear separation
monitor() {
    while true; do
        clear
        echo "═══════════════════════════════════════════════════════════════"
        echo "🕐 $(date)"
        echo "═══════════════════════════════════════════════════════════════"
        echo ""

        echo "📦 PODS:"
        kubectl get pods -n $NAMESPACE -o wide
        echo ""

        echo "🔌 SERVICES & ENDPOINTS:"
        kubectl get svc,endpoints -n $NAMESPACE
        echo ""

        echo "⚠️  RECENT EVENTS (last 10):"
        kubectl get events -n $NAMESPACE --sort-by='.lastTimestamp' | tail -10
        echo ""

        echo "🔍 POD STATUS DETAILS:"
        kubectl get pods -n $NAMESPACE -o custom-columns=\
NAME:.metadata.name,\
STATUS:.status.phase,\
READY:.status.conditions[?\(@.type==\"Ready\"\)].status,\
RESTARTS:.status.containerStatuses[0].restartCount,\
IP:.status.podIP
        echo ""

        # Check if Artemis is running
        ARTEMIS_POD=$(kubectl get pods -n $NAMESPACE -l app=artemis -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
        if [ -n "$ARTEMIS_POD" ]; then
            echo "🎯 ARTEMIS STATUS: ✅ Running ($ARTEMIS_POD)"
            ARTEMIS_READY=$(kubectl get pod $ARTEMIS_POD -n $NAMESPACE -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}')
            echo "   Ready: $ARTEMIS_READY"
        else
            echo "🎯 ARTEMIS STATUS: ❌ Not found"
        fi
        echo ""

        echo "═══════════════════════════════════════════════════════════════"
        echo "Refreshing in 5 seconds... (Ctrl+C to stop)"
        sleep 5
    done
}

# Trap Ctrl+C for clean exit
trap 'echo ""; echo "👋 Monitoring stopped"; exit 0' INT

monitor
