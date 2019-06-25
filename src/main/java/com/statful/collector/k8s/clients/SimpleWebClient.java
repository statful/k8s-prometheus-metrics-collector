package com.statful.collector.k8s.clients;

import com.statful.collector.k8s.utils.Loggable;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.Single;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.core.eventbus.EventBus;
import io.vertx.reactivex.core.eventbus.Message;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;

import java.util.function.Function;

public class SimpleWebClient extends AbstractVerticle implements Loggable {
    private static final String DEV_MODE_KEY = "development.logging.enabled";
    private static final String GET_ENDPOINT = "getEndpoint";

    private WebClient client;
    private boolean isDevLoggingEnabled;

    @Override
    public void start(Future<Void> startFuture) {
        isDevLoggingEnabled = Boolean.valueOf(System.getProperty(DEV_MODE_KEY, Boolean.FALSE.toString()));

        initWebClient();
        registerConsumers();
    }

    private void initWebClient() {
        WebClientOptions options = buildWebClientOptions();

        client = WebClient.create(vertx, options);
    }

    private WebClientOptions buildWebClientOptions() {
        return new WebClientOptions()
                .setTrustAll(false)
                .setVerifyHost(true);
    }

    private void registerConsumers() {
        vertx.eventBus().consumer(GET_ENDPOINT, this::getEndpoint);
    }

    private void getEndpoint(Message<String> message) {
        request(message.body(), message, HttpResponse::bodyAsString);
    }

    private <T> void request(String url, Message<String> message, Function<HttpResponse<Buffer>, T> mapper) {
        if (isDevLoggingEnabled) {
            log().info("{0} - {1} executed", HttpMethod.GET, url);
        }

        client.getAbs(url)
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

        public Single<String> getEndpoint(String url) {
            return eventBus.<String>rxSend(SimpleWebClient.GET_ENDPOINT, url)
                    .map(Message::body);
        }
    }
}
