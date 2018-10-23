#!/bin/sh

## SOURCE CONFIGS
source ${BASH_SOURCE%/*}/source.sh

## SETUP TARGET DIR
mkdir -p .target

## SPEC
cat << EOF > .target/spec.yaml
---
apiVersion: extensions/v1beta1
kind: Deployment
metadata:
  labels:
    run: ${APPLICATION_NAME}
  name: ${APPLICATION_NAME}
  namespace: default
spec:
  replicas: 1
  selector:
    matchLabels:
      run: ${APPLICATION_NAME}
  strategy:
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 1
    type: RollingUpdate
  template:
    metadata:
      labels:
        run: ${APPLICATION_NAME}
    spec:
      containers:
      - image: ${DOCKER_HUB_USER}/${APPLICATION_NAME}:${VERSION:-latest}
        imagePullPolicy: Always
        name: ${APPLICATION_NAME}
        resources:
          limits:
            cpu: ${CPU_LIMIT:-0.1}
            memory: ${MEM_LIMIT:-512Mi}
          requests:
            cpu: ${CPU_REQUEST:-0.1}
            memory: ${MEM_REQUEST:-320Mi}
        env:
        - name: STATFUL_TOKEN
          value: ${STATFUL_TOKEN}
        - name: STATFUL_HOST
          value: ${STATFUL_HOST:-api-supervision.statful.com}
        - name: STATFUL_ENVIRONMENT
          value: ${ENVIRONMENT}
EOF

## APPLY CONFIGS
kubectl apply -f .target/spec.yaml
