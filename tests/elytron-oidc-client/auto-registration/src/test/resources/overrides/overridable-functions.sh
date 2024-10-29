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

function installPrerequisites() {
  local app="${1}"
  echo "Installing Keycloak..."
  kubectl apply -f src/test/resources/overrides/keycloak.yml
  echo "Waiting for Keycloak to be ready..."
  kubectl wait --for=condition=Available deployment/keycloak-server-cloud-test --timeout=300s
}

function cleanPrerequisites() {
  local app="${1}"
  kubectl delete -f src/test/resources/overrides/keycloak.yml --ignore-not-found=true
}

function preprocessYaml() {
  CLUSTER_IP=$(minikube ip)
  echo "Substituting \$CLUSTER_IP\$ with ${CLUSTER_IP}" >&2
  sed "s/\\\$CLUSTER_IP\\\$/${CLUSTER_IP}/g"
}
