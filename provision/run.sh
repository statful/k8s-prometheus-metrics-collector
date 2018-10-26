#!/bin/sh

JVM_OPTS="-XX:+PrintFlagsFinal"
HEAP_OPTS="-Xmx${JVM_MAX_HEAP_SIZE:-256m} -Xms${JVM_MIN_HEAP_SIZE:-128m} -XX:MetaspaceSize=${METASPACE_SIZE:-64m}"
GC_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+PrintGCDateStamps -XX:+PrintGCDetails -XX:+UseStringDeduplication -XX:+PrintStringDeduplicationStatistics"
STATFUL_OPTS="-Dstatful.token=${STATFUL_TOKEN} -Dstatful.dryrun=${STATFUL_DRYRUN:-false} -Dstatful.host=${STATFUL_HOST:-api.statful.com} -Dstatful.environment=${STATFUL_ENVIRONMENT:-production}"
KUBE_OPTS="-Dkubernetes.api.host=${KUBE_API_HOST:-kubernetes.default.svc} -Dkubernetes.api.port=${KUBE_API_PORT:-443} -Dkubernetes.api.cert=${KUBE_API_CERT:-/var/run/secrets/kubernetes.io/serviceaccount/ca.crt}"
COLLECTOR_OPTS="-Dcollector.period=${COLLECTOR_PERIOD:-60000} -Ddevelopment.logging.enabled=${DEV_LOGGING:-false}"

exec java ${HEAP_OPTS} ${JVM_OPTS} ${GC_OPTS} ${STATFUL_OPTS} ${KUBE_OPTS} ${COLLECTOR_OPTS} -jar /opt/${APPLICATION_NAME}/${APPLICATION_NAME}.jar