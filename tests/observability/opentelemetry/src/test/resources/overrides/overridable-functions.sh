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
  echo "opentelemetry"
}

function installPrerequisites() {
  local app="${1}"
  echo "Installing OpenTelemetry collector..."
  kubectl apply -f src/test/resources/overrides/collector-config.yml
  kubectl apply -f src/test/resources/overrides/opentelemetry-collector.yml
  kubectl apply -f src/test/resources/overrides/service.yml
  echo "Waiting for collector to be ready..."
  kubectl wait --for=condition=Available deployment/opentelemetrycollector --timeout=120s
}

function cleanPrerequisites() {
  local app="${1}"
  kubectl delete -f src/test/resources/overrides/service.yml --ignore-not-found=true
  kubectl delete -f src/test/resources/overrides/opentelemetry-collector.yml --ignore-not-found=true
  kubectl delete -f src/test/resources/overrides/collector-config.yml --ignore-not-found=true
}
