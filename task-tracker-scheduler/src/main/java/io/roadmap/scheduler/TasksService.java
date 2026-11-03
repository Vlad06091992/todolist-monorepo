package io.roadmap.scheduler;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
public class TasksService {

    WebClient webClient;
    private static final Logger log = LoggerFactory.getLogger(TasksService.class.getName());


    public TasksService() {
        this.webClient = WebClient.builder().build();
    }

    public Flux<TaskResponse> sendRequest(String userId, String date) {
        log.info("userId: {}", userId);
        log.info("date: {}", date);

        Flux<TaskResponse> response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("http")
                        .host("localhost")
                        .port(8080)
                        .path("/tasks/get-finished-tasks-after-date")   // без "?"
                        .queryParam("userId", userId)
                        .queryParam("date", date)
                        .build())
                .retrieve()
                .bodyToFlux(TaskResponse.class);

        return response;
    }
}
