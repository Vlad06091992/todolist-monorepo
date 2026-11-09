package io.roadmap.todolistmonorepo.kafka;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

@RequiredArgsConstructor
@Service
public class KafkaService {

    private final KafkaSender sender;
    private static final Logger log = LoggerFactory.getLogger(KafkaService.class.getName());

    public <T> Mono<Void> sendMessage(String topic, T message) {
        return sender.send(makeRecord(topic, message))
                .doOnNext(r -> log.info("отправка сообщения о регистрации пользователя {}", r.toString()))
                .doOnError(e -> log.error("Ошибка при отправке в топик {}", e))
                .then();
    }

    public void close() {
        sender.close();
    }

    private <T> Mono<SenderRecord<Integer, T, Integer>> makeRecord(String topic, T payload) {
        return Mono.just(
                SenderRecord.create(
                        new ProducerRecord<>(topic, payload),
                        null
                )
        );
    }
}