Kubernetes Metrics Collector
==============

[![Build Status](https://travis-ci.org/statful/k8s-metrics-collector.svg?branch=master)](https://travis-ci.org/statful/k8s-metrics-collector)

Collector of cluster-wide metrics for [kubernetes](https://kubernetes.io/).

## Table of Contents

- [Features](#features)
- [Configuration](#configuration)
- [Installation](#installation)
- [Container](#container)
- [Authors](#authors)
- [License](#license)

## Compatibility

This collector was tested with Kubernetes version `1.10.9`, should work with all >`1.10`.

## Features

Collects a kubernetes' cluster metrics, translates the prometheus style metrics and sends them to statful, with the namespace `kubernetes`. Currently
supports node metrics, [cAdvisor](https://github.com/google/cadvisor) as well as [metrics-server](https://github.com/kubernetes-incubator/metrics-server) for cpu and memory metrics.

## Configuration

You can configure the collector using system variables, json file and with a kubernetes config map. Each method overrides the other respectively (sys < json < config map). You can look at the folder examples for examples of all the options.
You have to set the configuration properties using the container environment variables (or system properties if not using docker).

| Variable                           | Container env var                  | Description                                                                | Default                                              |
| ---------------------------------- | ---------------------------------- | -------------------------------------------------------------------------- | ---------------------------------------------------- |
| `collector.config.path`            | `COLLECTOR_CONFIG_PATH`            | Path to the json file containing the configurations.                       | conf/config.json                                     |
| `collector.configmap.namespace`    | `COLLECTOR_CONFIGMAP_NAMESPACE`    | Namespace of the k8s config map (overrides the json configs)               | default                                              |
| `collector.configmap.name`         | `COLLECTOR_CONFIGMAP_NAME`         | Name of the k8s config map (overrides the json configs)                    | k8s-metrics-collector                                |
| `collector.secret.namespace`       | `COLLECTOR_SECRET_NAMESPACE`       | Namespace of the k8s secret (overrides the json and config map)            | default                                              |
| `collector.secret.name`            | `COLLECTOR_SECRET_NAME`            | Name of the k8s secret (overrides the json and config map)                 | statful-token                                        |
| `collector.ignore.metric.regex`    | `COLLECTOR_IGNORE_METRIC_REGEX`    | Regex used to ignore metric names                                          |                                                      |
| `collector.ignore.metric`          | `COLLECTOR_IGNORE_METRIC`          | Json array of metric names to be ignored (can be used alongside the regex) |                                                      |
| `collector.ignore.tags.regex`      | `COLLECTOR_IGNORE_TAGS_REGEX`      | Regex used to ignore tag names                                             |                                                      |
| `collector.ignore.tags`            | `COLLECTOR_IGNORE_TAGS`            | Json array of metric tags to be ignored (can be used alongside the regex)  |                                                      |
| `collector.replacement.tag`        | `COLLECTOR_REPLACEMENT_TAG`        | Json containing regex and replacement values for tag names                 |                                                      |
| `collector.period`                 | `COLLECTOR_PERIOD`                 | Collection interval in milliseconds                                        | 60000                                                |
| `collector.cadvisor.disabled`      | `COLLECTOR_CADVISOR_DISABLED`      | Option to disable the collection of cAdvisor metrics                       | false                                                |
| `collector.nodes.disabled`         | `COLLECTOR_NODES_DISABLED`         | Option to disable the collection of node metrics                           | false                                                |
| `collector.metricsserver.disabled` | `COLLECTOR_METRICSSERVER_DISABLED` | Option to disable the collection of metrics-server metrics                 | false                                                |
| `kubernetes.api.host`              | `KUBE_API_HOST`                    |                                                                            | kubernetes.default.svc.cluster.local                 |
| `kubernetes.api.port`              | `KUBE_API_PORT`                    |                                                                            | 443                                                  |
| `kubernetes.api.cert`              | `KUBE_API_CERT`                    | Only used for port 443                                                     | /var/run/secrets/kubernetes.io/serviceaccount/ca.crt |
| `kubernetes.api.token`             | `KUBE_API_TOKEN`                   | Only used for port 443                                                     | /var/run/secrets/kubernetes.io/serviceaccount/token  |
| `staful.token`                     | `STATFUL_TOKEN`                    | `required` Authentication token to send to Statful                         |                                                      |
| `statful.host`                     | `STATFUL_HOST`                     |                                                                            | api.statful.com                                      |
| `statful.dryrun`                   | `STATFUL_DRYRUN`                   | Debug log metrics when flushing the buffer                                 | false                                                |
| `statful.environment`              | `STATFUL_ENVIRONMENT`              | Set environment tag                                                        |                                                      |
| `statful.timer.agg`                | `STATFUL_TIMER_AGG`                | Comma separated list of aggregations for timer metrics                     | AVG,P90,COUNT                                        |
| `statful.counter.agg`              | `STATFUL_COUNTER_AGG`              | Comma separated list of aggregations for counter metrics                   | COUNT,SUM                                            |
| `statful.gauge.agg`                | `STATFUL_GAUGE_AGG`                | Comma separated list of aggregations for gauge metrics                     | LAST,MAX,AVG                                         |
| `development.logging.enabled`      | `DEV_LOGGING`                      | Enables more extensive logging                                             | false                                                |
|                                    | `JVM_MAX_HEAP_SIZE`                |                                                                            | 256m                                                 |
|                                    | `JVM_MIN_HEAP_SIZE`                |                                                                            | 128m                                                 |
|                                    | `METASPACE_SIZE`                   |                                                                            | 64m                                                  |

## Installation

Install using the following command, after replacing the statful token placeholder in the yaml file:

```
kubectl create -f https://raw.githubusercontent.com/statful/k8s-metrics-collector/master/deploy/1.0.4.yaml
```

## Container

Official containers can be found [here](https://hub.docker.com/r/statful/k8s-metrics-collector/).

## Authors

[Mindera - Software Craft](https://github.com/Mindera)

## License

Kubernetes Metrics Collector is available under the MIT license. See the [LICENSE](https://raw.githubusercontent.com/statful/k8s-metrics-collector/master/LICENSE) file for more information.