package io.roadmap.tasktracker.scheduler.report;

import io.roadmap.tasktracker.scheduler.client.BackendTaskClient;
import io.roadmap.tasktracker.scheduler.kafka.KafkaService;
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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


@Service
@RequiredArgsConstructor
public class ReportGenerationService {
    private static final Logger log = LoggerFactory.getLogger(ReportGenerationService.class.getName());
    private final KafkaService kafkaService;

    private final Map<String, Sinks.One<String>> pending =
            new ConcurrentHashMap<>();


    //todo нужен ли @Autowired?
    @Autowired
    private final ReceiverOptions<String, Object> receiverOptions;

    //todo нужен ли @Autowired?
    @Autowired
    private final BackendTaskClient backendTaskClient;

    @Value("${app.kafka.topic.reports-summaries-requested}")
    private String summariesRequestedTopic;

    @Value("${app.kafka.topic.reports-summaries-completed}")
    private String summariesCompletedTopic;

    @Value("${app.kafka.topic.notifications-email-requested}")
    private String emailReportRequestedTopic;

    private final ObjectMapper objectMapper = new ObjectMapper();

    //TODO очистку временного хранилища емайлов
    private Map<String, String> emailsByLogin = new HashMap<>();

    public Mono<Void> generateReportsForPage(String date, int page, int pageSize) {
        return backendTaskClient
                .fetchUsersWithFinishedTasks(date, page, pageSize)
                .collectList()
                .doOnNext(list -> {
                    list.forEach(userSummary -> emailsByLogin.put(userSummary.login, userSummary.email));
                })
                .map(users -> users.stream().map(user -> new UserSummary(user.id, user.login, user.email, user.tasks)).toList())
                .flatMap((list) -> Mono.defer(() -> {

                            UUID uuid = UUID.randomUUID();
                            String correlationId = uuid.toString();
                            log.info("Sending reports request to Kafka, correlationId: {}", correlationId);
                    return kafkaService.sendMessage(summariesRequestedTopic, list, correlationId).thenReturn(correlationId);
                }))
                .flatMap((correlationId) -> {
                    Sinks.One<String> response = Sinks.one();
                    pending.put(correlationId, response);
                    return response.asMono()
                            .doFinally((signal) -> {
                                pending.remove(correlationId);
                            });
                }).map((rawReport) -> {
                    List<EmailReportRequestedEvent> tasks = objectMapper.readValue(
                            rawReport,
                            new TypeReference<>() {
                            });

                    return tasks.stream().map(report -> report.withEmail(emailsByLogin.get(report.login()))).toList();

                }).flatMap((reports) -> Flux.fromIterable(reports).flatMap((report) -> kafkaService.sendMessage(
                        emailReportRequestedTopic, report, UUID.randomUUID().toString()).then()).then());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startReplyListener() {
        ReceiverOptions<String, Object> options = receiverOptions.subscription(Collections.singleton(summariesCompletedTopic));
        Flux<ReceiverRecord<String, Object>> kafkaFlux = KafkaReceiver.create(options).receive();

        kafkaFlux.subscribe(record -> {
            Header correlationHeader = record.headers().lastHeader(KafkaService.CORRELATION_ID_HEADER);
            String correlationId = new String(correlationHeader.value(), StandardCharsets.UTF_8);


            Sinks.One<String> sink = pending.get(correlationId);

            if (sink != null) {
                sink.tryEmitValue((String) record.value());
            }

            ReceiverOffset offset = record.receiverOffset();
            offset.acknowledge();
        });
    }
}
