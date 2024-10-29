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

function namespace() {
  echo "kafka"
}

function installPrerequisites() {
  local app="${1}"
  echo "Installing Strimzi operator..."
  kubectl apply -f 'https://strimzi.io/install/latest?namespace=kafka' --force
  echo "Waiting for Strimzi operator to be ready..."
  kubectl wait --for=condition=Available deployment/strimzi-cluster-operator --timeout=300s

  echo "Creating Kafka node pool..."
  kubectl apply -f src/test/resources/overrides/strimzi-node-pool.yml
  echo "Creating Kafka cluster..."
  kubectl apply -f src/test/resources/overrides/strimzi-cluster.yml
  echo "Waiting for entity operator deployment to be created..."
  local attempts=0
  until kubectl get deployment my-cluster-entity-operator 2>/dev/null; do
    attempts=$((attempts + 1))
    if [ "$attempts" -ge 60 ]; then
      echo "ERROR: entity operator deployment was not created after 300s"
      exit 1
    fi
    sleep 5
  done
  echo "Waiting for entity operator to be available..."
  kubectl wait --for=condition=Available deployment/my-cluster-entity-operator --timeout=300s

  echo "Creating Kafka topic..."
  kubectl apply -f src/test/resources/overrides/strimzi-topic.yml
  sleep 5
}

function cleanPrerequisites() {
  local app="${1}"
  kubectl delete -f src/test/resources/overrides/strimzi-topic.yml --ignore-not-found=true
  kubectl delete -f src/test/resources/overrides/strimzi-cluster.yml --ignore-not-found=true
  kubectl delete -f src/test/resources/overrides/strimzi-node-pool.yml --ignore-not-found=true
  kubectl delete -f 'https://strimzi.io/install/latest?namespace=kafka' --ignore-not-found=true
}

function waitForReadiness() {
  local app="${1}"
  echo "Waiting for ${app} deployment..."
  kubectl wait --for=condition=Available deployment/"${app}" --timeout=300s
}
