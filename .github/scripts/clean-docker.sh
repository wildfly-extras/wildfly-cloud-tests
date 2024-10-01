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

# Dump all images so we have more information
# echo "All images"
# docker image ls

echo "Deleting test image $(docker image ls | grep localhost:5000 | awk '{print $1":"$2}')"
docker image ls | grep localhost:5000 | awk '{print "docker image rm "$1":"$2}' | sh

echo "Deleting base image  $(docker image ls | grep "wildfly-cloud-test-image"  | awk '{print $1":"$2}')...."
docker image ls | grep "wildfly-cloud-test-image"  | awk '{print "docker image rm "$1":"$2}' | sh

docker system prune -f


