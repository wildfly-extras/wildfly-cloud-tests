#!/bin/bash
#
# JBoss, Home of Professional Open Source.
# Copyright 2024 Red Hat, Inc., and individual contributors
# as indicated by the @author tags.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

#
# Starts minikube and sets up a local Docker registry that minikube can pull from.
# Prerequisites: a container runtime (docker or podman) must already be running.
#

set -e

# --- Detect container runtime ---
if command -v docker &>/dev/null && docker info &>/dev/null 2>&1; then
  CONTAINER_CMD=docker
elif command -v podman &>/dev/null && podman info &>/dev/null 2>&1; then
  CONTAINER_CMD=podman
else
  echo "ERROR: No running container runtime found."
  echo "Start Docker or Podman before running this script."
  exit 1
fi

OS=$(uname -s)
echo "OS: ${OS}, Container runtime: ${CONTAINER_CMD}"

# --- Determine minikube driver ---
if [ "${CONTAINER_CMD}" = "docker" ]; then
  DRIVER=docker
else
  DRIVER=podman
fi

# --- Check for port 5000 conflicts (macOS AirPlay Receiver) ---
if [ "${OS}" = "Darwin" ]; then
  if lsof -i :5000 -sTCP:LISTEN >/dev/null 2>&1; then
    # Check if it's something other than our registry
    if ! ${CONTAINER_CMD} ps --format '{{.Names}}' 2>/dev/null | grep -q '^local-registry$'; then
      echo "WARNING: Port 5000 is already in use (possibly macOS AirPlay Receiver)."
      echo "Disable it in: System Settings > General > AirDrop & Handoff > AirPlay Receiver"
      echo "Then re-run this script."
      exit 1
    fi
  fi
fi

# --- Start minikube ---
echo ""
echo "Starting minikube with ${DRIVER} driver..."
minikube start \
  --driver="${DRIVER}" \
  --container-runtime=containerd \
  --memory=4gb \
  --cpus=2 \
  --insecure-registry=10.0.0.0/8

echo ""

# --- Start local registry if not already running ---
if ${CONTAINER_CMD} ps --format '{{.Names}}' 2>/dev/null | grep -q '^local-registry$'; then
  echo "Local registry container already running."
else
  echo "Starting local registry container..."
  ${CONTAINER_CMD} rm -f local-registry 2>/dev/null || true
  ${CONTAINER_CMD} run -d -p 5000:5000 --restart=always --name local-registry \
    -e REGISTRY_STORAGE_DELETE_ENABLED=true \
    registry:2
fi

# Wait for registry to accept connections
echo "Waiting for registry to be ready..."
attempts=0
until curl -f -s http://localhost:5000/v2/ >/dev/null 2>&1; do
  attempts=$((attempts + 1))
  if [ "${attempts}" -ge 30 ]; then
    echo "ERROR: Registry did not become ready within 30s"
    exit 1
  fi
  sleep 1
done
echo "Registry is ready at localhost:5000"

# --- Connect registry to minikube's network ---
MINIKUBE_NETWORK=$(${CONTAINER_CMD} inspect minikube --format='{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}' 2>/dev/null)
if [ -z "${MINIKUBE_NETWORK}" ]; then
  echo "ERROR: Could not determine minikube's network"
  exit 1
fi
echo "Minikube network: ${MINIKUBE_NETWORK}"

EXISTING_IP=$(${CONTAINER_CMD} inspect local-registry \
  --format="{{.NetworkSettings.Networks.${MINIKUBE_NETWORK}.IPAddress}}" 2>/dev/null || echo "")

if [ -n "${EXISTING_IP}" ] && [ "${EXISTING_IP}" != "<no value>" ]; then
  echo "Registry already connected to minikube network at ${EXISTING_IP}"
else
  echo "Connecting registry to minikube network..."
  ${CONTAINER_CMD} network connect "${MINIKUBE_NETWORK}" local-registry
fi

REGISTRY_IP=$(${CONTAINER_CMD} inspect local-registry \
  --format="{{.NetworkSettings.Networks.${MINIKUBE_NETWORK}.IPAddress}}")
echo "Registry IP on minikube network: ${REGISTRY_IP}"

# --- Configure containerd inside minikube to redirect localhost:5000 ---
echo "Configuring containerd inside minikube to use registry at ${REGISTRY_IP}:5000..."
minikube ssh "sudo mkdir -p /etc/containerd/certs.d/localhost:5000"
minikube ssh "printf '[host.\"http://${REGISTRY_IP}:5000\"]\n  capabilities = [\"pull\", \"resolve\"]\n' | sudo tee /etc/containerd/certs.d/localhost:5000/hosts.toml"

echo "Restarting containerd..."
minikube ssh "sudo systemctl restart containerd"
sleep 5

# --- Verify registry is reachable from minikube ---
echo "Verifying registry access from minikube..."
if minikube ssh "curl -f -s http://${REGISTRY_IP}:5000/v2/" >/dev/null 2>&1; then
  echo "Minikube can reach registry at ${REGISTRY_IP}:5000"
else
  echo "ERROR: Minikube cannot reach registry at ${REGISTRY_IP}:5000"
  exit 1
fi

echo ""
echo "=== Minikube and local registry are ready ==="
echo "Run tests with: mvn verify -Pkubernetes-tests"
