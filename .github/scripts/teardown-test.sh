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

if [ -z "${1}" ]; then
  echo "Usage: teardown-test.sh <module-path>"
  exit 1
fi

module_path="${1}"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd "${script_dir}/../.." && pwd)"

cd "${project_root}"

# Source base functions
source "${script_dir}/overridable-functions.sh"

# Source per-module overrides if they exist
override_file="${project_root}/${module_path}/src/test/resources/overrides/overridable-functions.sh"
if [ -f "${override_file}" ]; then
  source "${override_file}"
fi

# Read state file
state_file="${project_root}/${module_path}/target/.cloud-test-state"
if [ -f "${state_file}" ]; then
  source "${state_file}"
else
  echo "Warning: no state file found at ${state_file}"
  APP_NAME=$(applicationName "${module_path}")
  NAMESPACE=$(namespace "${APP_NAME}")
fi

echo "=== Tearing down test for ${APP_NAME} ==="

# Delete deployment
cd "${project_root}/${module_path}"
deployment_yaml=$(getDeploymentYaml)
if [ -f "${deployment_yaml}" ]; then
  preprocessYaml < "${deployment_yaml}" | kubectl delete --ignore-not-found=true -f -
fi

# Clean prerequisites
echo "Cleaning prerequisites..."
cleanPrerequisites "${APP_NAME}"

# Restore namespace if we changed it
if [ -n "${NAMESPACE}" ]; then
  echo "Switching back to namespace '${OLD_NAMESPACE:-default}'"
  kubectl config set-context --current --namespace="${OLD_NAMESPACE:-default}"
  echo "Deleting namespace '${NAMESPACE}'"
  kubectl delete namespace "${NAMESPACE}" --ignore-not-found=true
fi

# CI cleanup
if [ "${CLOUD_TESTS_CI}" = "1" ]; then
  echo "Running CI cleanup..."
  "${script_dir}/stop-registry.sh" "${project_root}/${module_path}" "${CONTAINER_IMAGE:-localhost:5000/${APP_NAME}:latest}"
  "${script_dir}/clean-docker.sh" "${project_root}/${module_path}"
fi

# Clean state file (but NOT target/ — Maven needs it for failsafe reports)
rm -f "${state_file}"

echo "=== Teardown complete for ${APP_NAME} ==="
