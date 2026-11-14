package io.roadmap.scheduler;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;

@Service
public class TasksService {

    WebClient webClient;
    private static final Logger log = LoggerFactory.getLogger(TasksService.class.getName());


    public TasksService() {
        this.webClient = WebClient.builder().build();
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
                .bodyToFlux(UserSummaryForReport.class);

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
                        .queryParam("page")
                        .build())
                .retrieve()
                .bodyToMono(Long.class);

        return response;
    }
}
