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

Collects a kubernetes' cluster metrics, translates the prometheus style metrics and sends them to statful, with the namespace `kubernetes. Currently
supports [cAdvisor](https://github.com/google/cadvisor) and node metrics.

## Configuration

| Variable                      | Container env var     | Description                                        | Default                                              |
| ---------------------------   | -------------------   | -------------------------------------------------- | ---------------------------------------------------- |
| `collector.period`            | `COLLECTOR_PERIOD`    | Collection interval in milliseconds                | 60000                                                |
| `collector.ignore`            | `COLLECTOR_IGNORE`    | Regex to ignore metrics with names it matches      |                                                      |
| `kubernetes.api.host`         | `KUBE_API_HOST`       |                                                    | kubernetes.default.svc.cluster.local                 |
| `kubernetes.api.port`         | `KUBE_API_PORT`       |                                                    | 443                                                  |
| `kubernetes.api.cert`         | `KUBE_API_CERT`       | Only used for port 443                             | /var/run/secrets/kubernetes.io/serviceaccount/ca.crt |
| `kubernetes.api.token`        | `KUBE_API_TOKEN`      | Only used for port 443                             | /var/run/secrets/kubernetes.io/serviceaccount/token  |
| `staful.token`                | `STATFUL_TOKEN`       | `required` Authentication token to send to Statful |                                                      |
| `statful.host`                | `STATFUL_HOST`        |                                                    | api.statful.com                                      |
| `statful.dryrun`              | `STATFUL_DRYRUN`      | Debug log metrics when flushing the buffer         | false                                                |
| `statful.environment`         | `STATFUL_ENVIRONMENT` | Set environment tag                                |                                                      |
| `development.logging.enabled` | `DEV_LOGGING`         | Enables more extensive logging                     | false                                                |
|                               | `JVM_MAX_HEAP_SIZE`   |                                                    | 256m                                                 |
|                               | `JVM_MIN_HEAP_SIZE`   |                                                    | 128m                                                 |
|                               | `METASPACE_SIZE`      |                                                    | 64m                                                  |

## Installation

Install using the following command, after replacing the statful token placeholder in the yaml file:

```
kubectl create -f https://raw.githubusercontent.com/statful/k8s-metrics-collector/deploy/1.0.4.yaml
```

## Container

Official containers can be found [here](https://hub.docker.com/r/statful/k8s-metrics-collector/).

## Authors

[Mindera - Software Craft](https://github.com/Mindera)

## License

Kubernetes Metrics Collector is available under the MIT license. See the [LICENSE](https://raw.githubusercontent.com/statful/k8s-metrics-collector/master/LICENSE) file for more information.