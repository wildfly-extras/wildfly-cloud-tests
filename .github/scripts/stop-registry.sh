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

echo "Deleting test image from docker: $2"
docker image rm "${2}" 2>/dev/null || true

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

# Registry cleanup (restart to wipe storage) is now handled by start-registry.sh
# before the next test starts. This ensures the registry is ready when needed.
echo "Test cleanup complete (registry will be cleaned before next test)"
