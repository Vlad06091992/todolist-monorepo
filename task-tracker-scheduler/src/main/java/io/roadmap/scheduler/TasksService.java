package io.roadmap.scheduler;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class TasksService {

    WebClient webClient;

    public TasksService() {
        this.webClient = WebClient.builder().build();
    }

    public void sendRequest() {
        webClient.get()
                .uri("http://localhost:8080/tasks/statistics")
                .retrieve()
                .bodyToMono(Object.class)
                .subscribe(r -> {
                    System.out.println(r.toString());
                });
    }
}
