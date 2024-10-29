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

set -e

if [ -z "${1}" ]; then
  echo "Usage: setup-test.sh <module-path>"
  exit 1
fi

module_path="${1}"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd "${script_dir}/../.." && pwd)"

cd "${project_root}"

if [ ! -d "${module_path}" ]; then
  echo "Module path ${module_path} does not exist"
  exit 1
fi

# Source base functions
source "${script_dir}/overridable-functions.sh"

# Source per-module overrides if they exist
override_file="${project_root}/${module_path}/src/test/resources/overrides/overridable-functions.sh"
if [ -f "${override_file}" ]; then
  echo "Loading overrides from ${override_file}"
  source "${override_file}"
fi

application=$(applicationName "${module_path}")
ns=$(namespace "${application}")
deployment_yaml=$(getDeploymentYaml)
container_cmd=$(containerCommand)

echo "=== Setting up test for ${application} ==="

# Dump resource stats on CI
if [ "${CLOUD_TESTS_CI}" = "1" ]; then
  echo "--- Resource stats ---"
  df -h / | tail -1 | awk '{print "Disk: "$3" used / "$4" avail ("$5" used)"}'
  free -h 2>/dev/null | awk '/^Mem:/{print "Memory: "$3" used / "$7" avail / "$2" total"}' || true
  echo "Container disk usage:"
  ${container_cmd} system df 2>/dev/null || true
  echo "---"
fi

# CI cleanup: recreate registry to free space
if [ "${CLOUD_TESTS_CI}" = "1" ]; then
  echo "Running CI registry cleanup..."
  "${script_dir}/start-registry.sh" "${module_path}"
fi

# Verify registry is reachable from minikube (IP may drift after container/VM restarts)
minikube_network=$(${container_cmd} inspect minikube --format='{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}' 2>/dev/null)
if [ -n "${minikube_network}" ]; then
  registry_ip=$(${container_cmd} inspect local-registry \
    --format="{{.NetworkSettings.Networks.${minikube_network}.IPAddress}}" 2>/dev/null)
  if [ -n "${registry_ip}" ] && [ "${registry_ip}" != "<no value>" ]; then
    configured_ip=$(minikube ssh "cat /etc/containerd/certs.d/localhost:5000/hosts.toml 2>/dev/null" \
      | grep -oE '[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+' | head -1)
    if [ "${configured_ip}" != "${registry_ip}" ]; then
      echo "Registry IP changed (${configured_ip:-none} -> ${registry_ip}), reconfiguring containerd..."
      minikube ssh "sudo mkdir -p /etc/containerd/certs.d/localhost:5000"
      minikube ssh "printf '[host.\"http://${registry_ip}:5000\"]\n  capabilities = [\"pull\", \"resolve\"]\n' \
        | sudo tee /etc/containerd/certs.d/localhost:5000/hosts.toml"
      minikube ssh "sudo systemctl restart containerd"
      sleep 3
    else
      echo "Registry reachable from minikube at ${registry_ip}"
    fi
  fi
fi

# Switch namespace if needed
if [ -n "${ns}" ]; then
  old_namespace="$(kubectl config view --minify --output 'jsonpath={..namespace}'; echo)"
  echo "Creating and switching to namespace '${ns}'"
  kubectl create namespace "${ns}" --dry-run=client -o yaml | kubectl apply -f -
  kubectl config set-context --current --namespace="${ns}"
fi

# Tag and push Docker image to local registry
cd "${project_root}/${module_path}"

# Install prerequisites
echo "Installing prerequisites..."
installPrerequisites "${application}"
echo "Tagging and pushing image..."
# Extract the image name from the deployment YAML (the WildFly Maven Plugin
# names images after the Maven artifactId, which may differ from the app name)
container_image=$(grep 'image:' "${deployment_yaml}" | head -1 | sed 's/.*image: *//' | sed 's/ *$//')
${container_cmd} tag "${container_image#localhost:5000/}" "${container_image}"
${container_cmd} push "${container_image}"

# Apply deployment YAML
echo "Applying deployment YAML..."
cd "${project_root}/${module_path}"
preprocessYaml < "${deployment_yaml}" | kubectl apply -f -

# Wait for readiness
waitForReadiness "${application}"

# Run post-deploy commands
runPostDeployCommands

# Write state file for teardown
state_file="${project_root}/${module_path}/target/.cloud-test-state"
mkdir -p "$(dirname "${state_file}")"
cat > "${state_file}" << EOF
APP_NAME=${application}
NAMESPACE=${ns}
OLD_NAMESPACE=${old_namespace:-default}
MODULE_PATH=${module_path}
CONTAINER_IMAGE=${container_image}
EOF
# Values are simple identifiers (no spaces/special chars) so KEY=VALUE format is safe

echo "=== Setup complete for ${application} ==="
