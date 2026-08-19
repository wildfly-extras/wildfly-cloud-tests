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

function applicationName() {
  echo "ds-postgresql"
}

function installPrerequisites() {
  local app="${1}"
  echo "Installing PostgreSQL..."
  kubectl apply -f src/test/resources/overrides/kubernetes.yml
  echo "Waiting for PostgreSQL deployment to be available..."
  kubectl wait --for=condition=Available deployment/postgres --timeout=120s
  echo "Waiting for PostgreSQL to accept connections..."
  local pod
  pod=$(kubectl get pods -l app=postgres -o jsonpath='{.items[0].metadata.name}')
  local attempts=0
  until kubectl exec "${pod}" -- pg_isready -U postgresadmin -d postgresdb 2>/dev/null; do
    attempts=$((attempts + 1))
    if [ "$attempts" -ge 30 ]; then
      echo "ERROR: PostgreSQL did not become ready after 60s"
      exit 1
    fi
    sleep 2
  done
}

function cleanPrerequisites() {
  local app="${1}"
  kubectl delete -f src/test/resources/overrides/kubernetes.yml --ignore-not-found=true
}
