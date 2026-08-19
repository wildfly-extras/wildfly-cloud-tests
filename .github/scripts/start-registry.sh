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
  echo "Skipping, no tests"
  exit 0
fi

curr_dir=$(pwd)
cd "${1}/src/test/java"
git grep -i KUBERNETES
found_kubernetes=$?
if [ $found_kubernetes -ne 0 ]; then
  echo "Skipping, no Kubernetes tests"
  cd "${curr_dir}"
  exit 0
fi
cd "${curr_dir}"

if command -v docker &>/dev/null; then
  CONTAINER_CMD=docker
elif command -v podman &>/dev/null; then
  CONTAINER_CMD=podman
else
  echo "ERROR: neither docker nor podman found" >&2
  exit 1
fi

echo "Cleaning local container registry..."

# Wipe registry storage and restart. Restarting keeps the same
# IP/network assignments so containerd inside minikube does not need
# to be reconfigured.
${CONTAINER_CMD} exec local-registry rm -rf /var/lib/registry/docker
${CONTAINER_CMD} restart local-registry

# Wait for registry to be ready
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

# Verify it's empty
CATALOG=$(curl -s http://localhost:5000/v2/_catalog)
echo "Registry catalog after cleanup: ${CATALOG}"

echo "Registry cleanup complete"
