package io.roadmap.tasktracker.backend.kafka;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;

@RequiredArgsConstructor
@Service
public class KafkaEventPublisher {

    private final KafkaSender<Integer, Object> sender;
    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class.getName());

    public <T> Mono<Void> sendMessage(String topic, T message) {
        return sender.send(createSenderRecord(topic, message))
                .doOnNext( result -> {
                    if ( result.exception() != null) {
                        log.error("Kafka send failed", result.exception());
                    } else {
                        RecordMetadata metadata = result.recordMetadata();

                        log.info(
                                "Kafka message delivered: topic={}, partition={}, offset={}",
                                metadata.topic(),
                                metadata.partition(),
                                metadata.offset()
                        );
                    }
                })
                .doOnError(e ->
                        log.error("Ошибка отправки в Kafka, topic={}", topic, e)
                )
                .then();
    }

    public void close() {
        sender.close();
    }

    private <T> Mono<SenderRecord<Integer, T, Integer>> createSenderRecord(String topic, T payload) {
        return Mono.just(
                SenderRecord.create(
                        new ProducerRecord<>(topic, payload),
                        null
                )
        );
    }
}