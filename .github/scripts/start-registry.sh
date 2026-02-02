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

if [ ! -d "${1}/src/test/java" ]; then
  # There are no tests so there is no point in starting and stopping the registry
  echo "Skipping, no tests"
  exit 0
fi

curr_dir=$(pwd)
cd "${1}/src/test/java"
git grep KUBERNETES
found_kubernetes=$?
if [ $found_kubernetes -ne 0 ]; then
  echo "Skipping, no Kubernetes tests"
  cd "${curr_dir}"
  exit 0
fi
cd "${curr_dir}"

echo "Cleaning local Docker registry by restarting container..."

# Restart the registry container to completely wipe all stored images
# This provides the same memory/disk savings as disabling/enabling the minikube addon
docker restart local-registry

# Wait for registry to be ready
echo "Waiting for registry to be ready..."
timeout 30 bash -c 'until curl -f http://localhost:5000/v2/ >/dev/null 2>&1; do sleep 1; done' || {
  echo "ERROR: Registry failed to become ready after restart"
  exit 1
}

# After restart, registry may have a new IP - reconfigure containerd
MINIKUBE_NETWORK=$(docker inspect minikube --format='{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}' 2>/dev/null || echo "")
if [ -n "$MINIKUBE_NETWORK" ]; then
  REGISTRY_IP=$(docker inspect local-registry --format="{{.NetworkSettings.Networks.${MINIKUBE_NETWORK}.IPAddress}}" 2>/dev/null || echo "")
  if [ -n "$REGISTRY_IP" ]; then
    echo "Registry IP on minikube network: $REGISTRY_IP"
    echo "Reconfiguring minikube containerd to use registry at $REGISTRY_IP:5000..."
    minikube ssh "sudo mkdir -p /etc/containerd/certs.d/localhost:5000"
    minikube ssh "printf '[host.\"http://${REGISTRY_IP}:5000\"]\n  capabilities = [\"pull\", \"resolve\"]\n' | sudo tee /etc/containerd/certs.d/localhost:5000/hosts.toml"
    minikube ssh "sudo systemctl restart containerd"
    sleep 5
    echo "✓ Containerd reconfigured"
  fi
fi

echo "✓ Registry is clean and ready"

# Verify it's empty
echo "Registry catalog (should be empty):"
curl -s http://localhost:5000/v2/_catalog
echo ""
