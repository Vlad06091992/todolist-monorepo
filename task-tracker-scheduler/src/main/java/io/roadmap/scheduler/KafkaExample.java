package io.roadmap.scheduler;

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


@Service
@RequiredArgsConstructor
public class KafkaExample {
    private static final Logger log = LoggerFactory.getLogger(KafkaExample.class.getName());
    private final KafkaService kafkaService;
    private final ReceiverOptions receiverOptions;
//    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("HH:mm:ss:SSS z dd MMM yyyy");;


    public void send() {
        kafkaService
//                .sendMessage("SCHEDULER_SENDING_TASKS", "send from scheduler")
                .sendMessage("test", "send from scheduler")
                .subscribe();
    }


   @EventListener(ApplicationReadyEvent.class)
    public void consume(){
        ReceiverOptions<Integer, String> options =  receiverOptions.subscription(Collections.singleton("SCHEDULER_SENDING_TASKS"))
                .addAssignListener(partitions -> log.debug("onPartitionsAssigned {}", partitions))
                .addRevokeListener(partitions -> log.debug("onPartitionsRevoked {}", partitions));

        Flux<ReceiverRecord<Integer, String>> kafkaFlux = KafkaReceiver.create(options).receive();



        kafkaFlux.subscribe(record -> {
            ReceiverOffset offset = record.receiverOffset();
            Instant timestamp = Instant.ofEpochMilli(record.timestamp());
//            System.out.printf("Received message: topic-partition=%s offset=%d timestamp=%s key=%d value=%s\n",
//                    offset.topicPartition(),
//                    offset.offset(),
////                    dateFormat.format(timestamp),
//                    record.key(),
//                    record.value());

            log.info("receiver message {}", record.value());

            offset.acknowledge();
        });

    }


}
