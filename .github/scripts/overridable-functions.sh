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
# Default implementations of overridable functions for cloud tests.
# Per-test overrides live in <module-path>/src/test/resources/overrides/overridable-functions.sh
#

function applicationName() {
  # Default: use the last directory component of the module path
  basename "${1}"
}

function namespace() {
  # Default: empty (use current kubectl context namespace)
  echo ""
}

function installPrerequisites() {
  echo "No prerequisites required for ${1}"
}

function cleanPrerequisites() {
  echo "No prerequisites to clean for ${1}"
}

function getDeploymentYaml() {
  echo "src/main/kubernetes/wildfly-deployment.yml"
}

function runPostDeployCommands() {
  echo "No post-deploy commands"
}

function waitForReadiness() {
  local app="${1}"
  echo "Waiting for deployment ${app} to be available..."
  kubectl wait --for=condition=Available deployment/"${app}" --timeout=300s
}

function preprocessYaml() {
  # Default: passthrough (no substitution)
  cat
}

function containerCommand() {
  if command -v docker &> /dev/null; then
    echo "docker"
  elif command -v podman &> /dev/null; then
    echo "podman"
  else
    echo "ERROR: neither docker nor podman found" >&2
    exit 1
  fi
}
