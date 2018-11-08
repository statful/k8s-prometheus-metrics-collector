#!/bin/sh

JVM_OPTS="-XX:+PrintFlagsFinal"
HEAP_OPTS="-Xmx${JVM_MAX_HEAP_SIZE:-256m} -Xms${JVM_MIN_HEAP_SIZE:-128m} -XX:MetaspaceSize=${METASPACE_SIZE:-64m}"
GC_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication"
KUBE_OPTS="-Dkubernetes.api.host=${KUBE_API_HOST:-kubernetes.default.svc} -Dkubernetes.api.port=${KUBE_API_PORT:-443} -Dkubernetes.api.cert=${KUBE_API_CERT:-/var/run/secrets/kubernetes.io/serviceaccount/ca.crt} -Dkubernetes.api.token=${KUBE_API_TOKEN:-/var/run/secrets/kubernetes.io/serviceaccount/token}"

if [ -v ${STATFUL_ENVIRONMENT} ]; then
    STATFUL_OPTS="-Dstatful.token=${STATFUL_TOKEN} -Dstatful.dryrun=${STATFUL_DRYRUN:-false} -Dstatful.host=${STATFUL_HOST:-api.statful.com}"
else
    STATFUL_OPTS="-Dstatful.token=${STATFUL_TOKEN} -Dstatful.dryrun=${STATFUL_DRYRUN:-false} -Dstatful.host=${STATFUL_HOST:-api.statful.com} -Dstatful.environment=${STATFUL_ENVIRONMENT}"
fi

if [ -v ${COLLECTOR_IGNORE} ]; then
    COLLECTOR_OPTS="-Dcollector.period=${COLLECTOR_PERIOD:-60000} -Ddevelopment.logging.enabled=${DEV_LOGGING:-false}"
else
    COLLECTOR_OPTS="-Dcollector.period=${COLLECTOR_PERIOD:-60000} -Ddevelopment.logging.enabled=${DEV_LOGGING:-false} -Dcollector.ignore=${COLLECTOR_IGNORE}"
fi

exec java ${HEAP_OPTS} ${JVM_OPTS} ${GC_OPTS} ${STATFUL_OPTS} ${KUBE_OPTS} ${COLLECTOR_OPTS} -jar /opt/${APPLICATION_NAME}/${APPLICATION_NAME}.jar