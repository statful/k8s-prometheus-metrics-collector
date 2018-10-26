package com.statful.collector.k8s.clients;

import com.statful.collector.k8s.utils.Loggable;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.Single;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.core.eventbus.EventBus;
import io.vertx.reactivex.core.eventbus.Message;
import io.vertx.reactivex.ext.web.client.HttpRequest;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;

import java.util.function.Function;

public class KubeApi extends AbstractVerticle implements Loggable {
    private static final String DEV_MODE_KEY = "development.logging.enabled";
    private static final String KUBERNETES_API_HOST_KEY = "kubernetes.api.host";
    private static final String KUBERNETES_API_PORT_KEY = "kubernetes.api.port";
    private static final String KUBERNETES_API_CERT_KEY = "kubernetes.api.cert";
    private static final String KUBERNETES_API_TOKEN_KEY = "kubernetes.api.token";

    private static final String DEFAULT_KUBE_API_HOST = "kubernetes.default.svc.cluster.local";
    private static final String DEFAULT_KUBE_API_PORT = "443";
    private static final String DEFAULT_KUBE_API_CERT_LOCATION = "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt";
    private static final String DEFAULT_KUBE_API_TOKEN_LOCATION = "/var/run/secrets/kubernetes.io/serviceaccount/token";

    private static final String BASE_NODE = "/api/v1/nodes/";
    private static final String METRICS = "/proxy/metrics";
    private static final String CADVISOR_METRICS = "/proxy/metrics/cadvisor";

    private static final String GET_NODES = "getNodes";
    private static final String GET_NODE_METRICS = "getNodeMetrics";
    private static final String GET_CADVISOR_NODE_METRICS = "getCAdvisorNodeMetrics";

    private static final int SSL_PORT = 443;

    private WebClient client;
    private boolean isDevLoggingEnabled;
    private Buffer token;

    @Override
    public void start(Future<Void> startFuture) {
        isDevLoggingEnabled = Boolean.valueOf(System.getProperty(DEV_MODE_KEY, Boolean.FALSE.toString()));
        final String host = System.getProperty(KUBERNETES_API_HOST_KEY, DEFAULT_KUBE_API_HOST);
        final int port = Integer.valueOf(System.getProperty(KUBERNETES_API_PORT_KEY, DEFAULT_KUBE_API_PORT));

        initWebClient(host, port);
        registerConsumers();

        if (port == SSL_PORT) {
            final String tokenLocation = System.getProperty(KUBERNETES_API_TOKEN_KEY, DEFAULT_KUBE_API_TOKEN_LOCATION);
            vertx.fileSystem().rxReadFile(tokenLocation)
                    .subscribe(file -> {
                        this.token = file;
                        startFuture.complete();
                    }, error -> {
                        log().error("Error reading token file {}", error, tokenLocation);
                        startFuture.fail(error);
                    });
        } else {
            startFuture.complete();
        }
    }

    private void initWebClient(String host, int port) {
        WebClientOptions options = buildWebClientOptions(host, port);

        client = WebClient.create(vertx, options);
    }

    private WebClientOptions buildWebClientOptions(String host, int port) {
        WebClientOptions options;
        if (port == 443) {
            final String certLocation = System.getProperty(KUBERNETES_API_CERT_KEY, DEFAULT_KUBE_API_CERT_LOCATION);

            options = new WebClientOptions()
                    .setDefaultHost(host)
                    .setDefaultPort(port)
                    .setSsl(true)
                    .setTrustAll(false)
                    .setVerifyHost(true)
                    .setPemTrustOptions(new PemTrustOptions()
                            .addCertPath(certLocation));
        } else {
            options = new WebClientOptions()
                    .setDefaultHost(host)
                    .setDefaultPort(port);
        }
        return options;
    }

    private void registerConsumers() {
        vertx.eventBus().consumer(GET_NODES, this::getNodes);
        vertx.eventBus().consumer(GET_NODE_METRICS, this::getNodeMetrics);
        vertx.eventBus().consumer(GET_CADVISOR_NODE_METRICS, this::getCAdvisorNodeMetrics);
    }

    private void getNodes(Message<String> message) {
        request(BASE_NODE, message, HttpResponse::bodyAsJsonObject);
    }

    private void getNodeMetrics(Message<String> message) {
        request(BASE_NODE + message.body() + METRICS, message, HttpResponse::bodyAsString);
    }

    private void getCAdvisorNodeMetrics(Message<String> message) {
        request(BASE_NODE + message.body() + CADVISOR_METRICS, message, HttpResponse::bodyAsString);
    }

    private <T> void request(String url, Message<String> message, Function<HttpResponse<Buffer>, T> mapper) {
        if (isDevLoggingEnabled) {
            log().info("{0} - {1} executed", HttpMethod.GET, url);
        }

        final HttpRequest<Buffer> request = client.get(url)
                .putHeader("Authorization", "Bearer " + token.toString());

        request
                .rxSend()
                .doOnSuccess(response -> logResponse(url, response))
                .map(response -> handleBody(response, mapper))
                .subscribe(message::reply, error -> log().error("{0} - {1} failed", error, HttpMethod.GET, url));
    }

    private <T> T handleBody(HttpResponse<Buffer> response, Function<HttpResponse<Buffer>, T> mapper) {
        if (response.statusCode() != HttpResponseStatus.OK.code()) {
            throw new HttpStatusException(response.statusCode());
        }

        return mapper.apply(response);
    }

    private void logResponse(String url, HttpResponse<Buffer> response) {
        if (response.statusCode() != HttpResponseStatus.OK.code()) {
            log().error("{0} - {1} executed response status code {2} {3} body {4}", HttpMethod.GET, url, response.statusCode(), response.statusMessage(), response.bodyAsString());
        } else if (isDevLoggingEnabled) {
            log().info("{0} - {1} executed response status code {2} {3}", HttpMethod.GET, url, response.statusCode(), response.statusMessage());
        }
    }

    public static class Client implements Loggable {
        private final EventBus eventBus;

        public Client(EventBus client) {
            this.eventBus = client;
        }

        public Single<JsonObject> getNodes() {
            return eventBus.<JsonObject>rxSend(KubeApi.GET_NODES, "")
                    .map(Message::body);
        }

        public Single<String> getNodeMetrics(String node) {
            return eventBus.<String>rxSend(KubeApi.GET_NODE_METRICS, node)
                    .map(Message::body);
        }

        public Single<String> getCAdvisorNodeMetrics(String node) {
            return eventBus.<String>rxSend(KubeApi.GET_CADVISOR_NODE_METRICS, node)
                    .map(Message::body);
        }
    }
}
