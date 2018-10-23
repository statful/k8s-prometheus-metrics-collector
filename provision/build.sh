#!/bin/sh
set -eo pipefail

## SOURCE CONFIGS
source ${BASH_SOURCE%/*}/source.sh

## BUILD IMAGE
docker build -t ${APPLICATION_NAME}:${VERSION} .
docker build -t ${APPLICATION_NAME}:latest .
