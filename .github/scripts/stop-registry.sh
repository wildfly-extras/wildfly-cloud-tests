#!/bin/sh

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
if [ "${1}" = "" ]; then
  echo "Not configured for this folder"
fi

if [ ! -d "${1}/src/test/java" ]; then
  # There are no tests so there is no point in starting and stopping the registry
  echo "Skipping, no tests"
  exit 0
fi

echo "Deleting image from docker: $2"
docker image rm "${2}"

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

echo "Disabling the minikube registry"
minikube addons disable registry

echo "Stopping the port-forward"
if echo "$OSTYPE" | grep -q  "^darwin"; then
  # Mainly here so I can debug on my Mac. The main use for this is CI on Linux
  echo "Mac detected, stopping 'portfwd' container"
  docker stop --name portfwd
  exit 0
fi

# Assume Linux
ps aux | grep kubectl | grep port-forward | awk '{print "kill -9 " $2}' | sh

