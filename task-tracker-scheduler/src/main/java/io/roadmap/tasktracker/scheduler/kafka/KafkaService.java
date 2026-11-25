package io.roadmap.tasktracker.scheduler.kafka;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor
@Service
public class KafkaService {

    private final KafkaSender<Integer, Object> sender;
    public static final String CORRELATION_ID_HEADER = "CORRELATION_ID";
    private static final Logger log = LoggerFactory.getLogger(KafkaService.class.getName());


    public <T> Mono<Void> sendMessage(String topic, T message, String correlationId) {
        return sender.send(buildRecord(topic,message, correlationId))
                .doOnNext(r -> log.info("Kafka message delivered: {}", r))
                .doOnError(e -> log.error("Ошибка при отправке в топик {}", topic, e))
                .then();
    }

    private <T> Mono<SenderRecord<Integer, T, Integer>> buildRecord(String topic, T payload, String correlationId){
        ProducerRecord<Integer, T> producerRecord = new ProducerRecord<>(topic, payload);
        // здесь кладем заголовок
        producerRecord.headers().add(
                CORRELATION_ID_HEADER,
                correlationId.getBytes(StandardCharsets.UTF_8)
        );

        return   Mono.just(
                SenderRecord.create(
                        producerRecord,
                        null
                )
        );
    }
}