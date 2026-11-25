package io.roadmap.tasktracker.scheduler.client;

import io.roadmap.tasktracker.scheduler.report.UserSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Service
public class BackendTaskClient {

    private final WebClient webClient;
    private static final Logger log = LoggerFactory.getLogger(BackendTaskClient.class.getName());

    public BackendTaskClient(@Value("${app.backend.base-url:http://localhost:8080}") String baseUrl) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    private boolean isRetryable(Throwable t) {
        // 5xx от сервера — временный сбой, повторяем
        if (t instanceof WebClientResponseException w) {
            return w.getStatusCode().is5xxServerError();
        }
        // сетевые ошибки (connect refused, DNS, reset) — повторяем
        if (t instanceof WebClientRequestException) {
            return true;
        }
        // таймаут — повторяем
        if (t instanceof TimeoutException) {
            return true;
        }
        // всё остальное (400, 404, NPE, IllegalArgumentException) — не повторяем
        return false;
    }

    public Flux<UserSummary> fetchUsersWithFinishedTasks(String from, int page, int pageSize) {
        log.info("from: {}", from);
        Flux<UserSummary> result = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/tasks/reports/users-with-finished-tasks")
                        .queryParam("from", from)
                        .queryParam("page", page)
                        .queryParam("pageSize", pageSize)
                        .build())
                .retrieve()
                .bodyToFlux(UserSummary.class)
                .timeout(Duration.ofSeconds(10))    // таймаут каждой попытки
                .retryWhen(Retry.backoff(2, Duration.ofMillis(200))
                        .filter(this::isRetryable)) // 3 попытки всего, только retryable
                .timeout(Duration.ofSeconds(35))    // общий потолок (опционально)
                .doOnError((e)->{
                    log.error("fetchUsersWithFinishedTasks failed, from={}, cause={}", from, e.toString());
                });

        return result;
    }

    public Mono<Long> getCount(String from) {
        log.info("from: {}", from);
        Mono<Long> result = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/tasks/reports/users-with-finished-tasks/count")
                        .queryParam("from", from)
                        .build())
                .retrieve()
                .bodyToMono(Long.class)
                .timeout(Duration.ofSeconds(10))    // таймаут каждой попытки
                .retryWhen(Retry.backoff(2, Duration.ofMillis(200))
                        .filter(this::isRetryable)) // 3 попытки всего, только retryable
                .timeout(Duration.ofSeconds(35))    // общий потолок (опционально)
                .doOnError((e)->{
                    log.error("getCount failed, from={}, cause={}", from, e.toString());
                });

        return result;
    }
}
