package io.roadmap.scheduler;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Service
public class TasksService {

    WebClient webClient;
    private static final Logger log = LoggerFactory.getLogger(TasksService.class.getName());


    public TasksService() {
        this.webClient = WebClient.builder().build();
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

    public Flux<UserSummaryForReport> getDataForReports(String date, int page, int pageSize) {
        log.info("date: {}", date);
        Flux<UserSummaryForReport> response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("http")
                        .host("localhost")
                        .port(8080)
                        .path("/tasks/get-finished-tasks-after-date")
                        .queryParam("date", date)
                        .queryParam("page", page)
                        .queryParam("pageSize", pageSize)
                        .build())
                .retrieve()
                .bodyToFlux(UserSummaryForReport.class)
                .timeout(Duration.ofSeconds(10))    // таймаут каждой попытки
                .retryWhen(Retry.backoff(2, Duration.ofMillis(200))
                        .filter(this::isRetryable)) // 3 попытки всего, только retryable
                .timeout(Duration.ofSeconds(35))    // общий потолок (опционально)
                .doOnError((e)->{
                    log.error("getDataForReports failed, date={}, cause={}", date, e.toString());
                });;

        return response;
    }

    public Mono<Long> getCount(String date) {
        log.info("date: {}", date);
        Mono<Long> response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("http")
                        .host("localhost")
                        .port(8080)
                        .path("/tasks/count")
                        .queryParam("date", date)
                        .build())
                .retrieve()
                .bodyToMono(Long.class)
                .timeout(Duration.ofSeconds(10))    // таймаут каждой попытки
                .retryWhen(Retry.backoff(2, Duration.ofMillis(200))
                        .filter(this::isRetryable)) // 3 попытки всего, только retryable
                .timeout(Duration.ofSeconds(35))    // общий потолок (опционально)
                .doOnError((e)->{
                    log.error("getCount failed, date={}, cause={}", date, e.toString());
                });

        return response;
    }
}
