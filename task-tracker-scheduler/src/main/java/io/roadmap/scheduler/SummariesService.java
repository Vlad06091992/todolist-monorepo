package io.roadmap.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.receiver.ReceiverRecord;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


@Service
@RequiredArgsConstructor
public class SummariesService {
    private static final Logger log = LoggerFactory.getLogger(SummariesService.class.getName());
    private final KafkaService kafkaService;

    private final Map<String, Sinks.One<String>> pending =
            new ConcurrentHashMap<>();


    //todo нужен ли @Autowired?
    @Autowired
    private final ReceiverOptions receiverOptions;

    //todo нужен ли @Autowired?
    @Autowired
    private final TasksService tasksService;

    @Value("${app.kafka.topic.reports-summaries-requested}")
    private String summariesRequestedTopic;

    @Value("${app.kafka.topic.reports-summaries-completed}")
    private String summariesCompletedTopic;

    @Value("${app.kafka.topic.notifications-email-requested}")
    private String notificationsEmailRequested;

    //TODO очистку временного хранилища емайлов
    private Map<String, String> emails = new HashMap<>();

    public Mono<String> send(String date) {
        UUID uuid = UUID.randomUUID();
        String correlationId = uuid.toString();
        return tasksService
                .sendRequest(date)
                .collectList()
                .map(list -> {
                    list.forEach(el -> emails.put(el.login, el.email));
                    return list;
                })
                .flatMap((list) -> Mono.defer(() -> kafkaService.sendMessage(summariesRequestedTopic, list, correlationId)).then())

                .then(Mono.fromCallable(() -> {

                    Sinks.One<String> response = Sinks.one();
                    pending.put(correlationId, response);
                    return response.asMono();
                }))
                .flatMap((r) -> {
                    System.out.println("Получен ответ для RPC: " + r);
                    return r;

//                            .timeout(Duration.ofSeconds(35))


                })
                .doFinally(signal -> pending.remove(correlationId));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void consume() {
        ReceiverOptions<Integer, String> options = receiverOptions.subscription(Collections.singleton(summariesCompletedTopic));
        Flux<ReceiverRecord<Integer, String>> kafkaFlux = KafkaReceiver.create(options).receive();

        kafkaFlux.subscribe(record -> {


            Header correlationHeader = record.headers().lastHeader("CORRELATION_ID");

            if (correlationHeader == null) {
                // заголовка нет — обработайте (лог, пропуск, ошибка)
                return;
            }
            // здесь читаем заголовок
            String correlationId = new String(correlationHeader.value(), StandardCharsets.UTF_8);


            Sinks.One<String> sink = pending.get(correlationId);

            if (sink != null) {
                System.out.println("NOT NULL");
                sink.tryEmitValue(record.value());
            }


            ReceiverOffset offset = record.receiverOffset();
            ObjectMapper mapper = new ObjectMapper();
            //код ниже будет выполнятся в продбюсекре, реализуя паттерн RPC, работа будет происходить с Mono, который
            // будет создаваться в продьюсере, а здесь будет происходить sink.tryEmitValue(строка 105) для задания значения
            try {
                List<ReportDTO> tasks = mapper.readValue(
                        record.value(),
                        new TypeReference<>() {
                        });

                List<ReportDTO> tasksWithEmails = tasks.stream().map(el -> el.withEmail(emails.get(el.login()))).toList();
                log.info("tasks for reports {}", tasksWithEmails);
                //TODO обработка ошибок, нужен ли then и тд

                // отправка в емэйл
//                tasksWithEmails.forEach(el -> kafkaService.sendMessage(notificationsEmailRequested, el).then().subscribe());
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            log.info("receiver message from summarization {}", record.value());
            offset.acknowledge();
        });
    }
}
