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

echo ">>>> Start of build-docker-image.sh script <<<<"
echo "Directory: ${1}"
echo "Base image: ${2}"
echo "WildFly version: ${3}"

SCRIPT_DIR="$(dirname "${BASH_SOURCE[0]}")"
BASE_DIR="${SCRIPT_DIR}/../.."

cd "$BASE_DIR"


if [ ! -d "${1}/src/test/java" ]; then
  # There are no tests so there is no point in starting and stopping the registry
  echo "Skipping, no tests"
  exit 0
fi
echo ""
# ${2} is the value of the wildfly.cloud.test.base.image.name property in the poms
# It looks something like wildfly-cloud-test-image/<PART_WE_WANT>:latest
# sed command taken from 3.3. in https://www.baeldung.com/linux/bash-split-string-on-separator
IMAGE_BASE_NAME=$(echo "${2}" | sed 's/.*\///' | sed 's/:.*//')

echo "Base image name is ${IMAGE_BASE_NAME}, searching for directory containing it under images/ ..."

# We now have the base name of the image, which is the same as the artifactId of the module
# building the image. Try to find the artifact in the poms under images/
FOUND_POM=$(git grep --name-only "<artifactId>${IMAGE_BASE_NAME}</artifactId>" -- images)
if [ -z "${FOUND_POM}" ]; then
  echo "Could not find <artifactId>${IMAGE_BASE_NAME}</artifactId> anywhere!"
  >&2 echo "Exiting due to above error"
  exit 1
fi
echo "Found <artifactId>${IMAGE_BASE_NAME}</artifactId> in ${FOUND_POM}!"

echo "Building ${FOUND_POM} to create the ${2} image..."
mvn install -B -Pimages -pl "${FOUND_POM}" -Dversion.wildfly="${3}"

echo "${2} image has been built!"
echo ""
cd "$1"
echo ">>>> End of build-docker-image.sh script <<<<"