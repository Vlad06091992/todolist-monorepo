package io.roadmap.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.header.Headers;
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
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.receiver.ReceiverRecord;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class SummariesService {
    private static final Logger log = LoggerFactory.getLogger(SummariesService.class.getName());
    private final KafkaService kafkaService;

    @Autowired
    private final ReceiverOptions receiverOptions;

    @Value("${app.kafka.topic.reports-summaries-requested}")
    private String summariesRequestedTopic;

    @Value("${app.kafka.topic.reports-summaries-completed}")
    private String summariesCompletedTopic;

    @Value("${app.kafka.topic.notifications-email-requested}")
    private String notificationsEmailRequested;

    //TODO очистку временного хранилища емайлов
    private Map<String, String> emails = new HashMap<>();

    public Mono<Void> send(Flux<SummaryResponse> messages) {
        return messages
                .collectList()
                .flatMap(summariesRequestList -> {
                    summariesRequestList.forEach(el -> emails.put(el.login, el.email));
                    //todo вынести топик в переменные env
                    return kafkaService.sendMessage(summariesRequestedTopic, summariesRequestList);
                })
                .then();

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
            String id = new String(correlationHeader.value(), StandardCharsets.UTF_8);

            ReceiverOffset offset = record.receiverOffset();
            ObjectMapper mapper = new ObjectMapper();
            try {
                List<ReportDTO> tasks = mapper.readValue(
                        record.value(),
                        new TypeReference<>() {
                        });

                List<ReportDTO> tasksWithEmails = tasks.stream().map(el -> el.withEmail(emails.get(el.login()))).toList();
                log.info("tasks for reports {}", tasksWithEmails);
                //TODO обработка ошибок, нужен ли then и тд
//                tasksWithEmails.forEach(el -> kafkaService.sendMessage(notificationsEmailRequested, el).then().subscribe());
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            log.info("receiver message from summarization {}", record.value());
            offset.acknowledge();
        });
    }
}
