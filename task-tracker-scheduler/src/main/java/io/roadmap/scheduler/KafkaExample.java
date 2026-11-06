package io.roadmap.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.receiver.ReceiverRecord;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class KafkaExample {
    private static final Logger log = LoggerFactory.getLogger(KafkaExample.class.getName());
    private final KafkaService kafkaService;
    private final ReceiverOptions receiverOptions;
    //TODO очистку временного хранилища емайлов
    private Map<String,String> emails = new HashMap<>();
//    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("HH:mm:ss:SSS z dd MMM yyyy");;


//    public Mono<Void> send(Flux<SummaryResponse> messages) {
//        return messages
//                .flatMap(m ->{
//                    kafkaService.sendMessage("test", m);
//                    System.out.println("ОТПРАВЛЕНО!!!");
//                    System.out.println(m);
//                    return Mono.empty();
//                })// T = SummaryRespons
//                .then();
//    }

    public Mono<Void> send(Flux<SummaryResponse> messages) {

        return messages
                .collectList()
                .flatMap(m -> {

                    System.out.println(m);

                    m.stream().forEach(el -> {
                        System.out.println(el);
                        emails.put(el.login, el.email);
                    });
                    //todo вынести топик в переменные env
                    return kafkaService.sendMessage("task-tracker-scheduler.reports.requests", m);
                })
                .then();

//        return kafkaService
////                .sendMessage("SCHEDULER_SENDING_TASKS", "send from scheduler")
//                .sendMessage("test", messages)
//                .then();

    }

   @EventListener(ApplicationReadyEvent.class)
    public void consume(){
        ReceiverOptions<Integer, String> options =  receiverOptions.subscription(Collections.singleton("SCHEDULER_SENDING_TASKS"))
                .addAssignListener(partitions -> log.debug("onPartitionsAssigned {}", partitions))
                .addRevokeListener(partitions -> log.debug("onPartitionsRevoked {}", partitions));

        Flux<ReceiverRecord<Integer, String>> kafkaFlux = KafkaReceiver.create(options).receive();


        // сюда отчеты по пользователям

        kafkaFlux.subscribe(record -> {
            ReceiverOffset offset = record.receiverOffset();
            Instant timestamp = Instant.ofEpochMilli(record.timestamp());
//            System.out.printf("Received message: topic-partition=%s offset=%d timestamp=%s key=%d value=%s\n",
//                    offset.topicPartition(),
//                    offset.offset(),
////                    dateFormat.format(timestamp),
//                    record.key(),
//                    record.value());


            ObjectMapper mapper = new ObjectMapper();

            try {
                List<ReportDTO> tasks = mapper.readValue(
                        record.value(),
                        new TypeReference<List<ReportDTO>>() {}
                );

               List<ReportDTO> tasksWithEmails = tasks.stream().map(el -> el.withEmail(emails.get(el.login()))).toList();



                //тут уже есть емайл
                log.info("tasks for reports {}", tasksWithEmails);
                //TODO обработка ошибок, нужен ли then и тд
                tasksWithEmails.forEach(el -> kafkaService.sendMessage("EMAIL_SENDING_TASKS", el).then().subscribe());



            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

            log.info("receiver message from summarization {}", record.value());

            offset.acknowledge();
        });

    }


}
