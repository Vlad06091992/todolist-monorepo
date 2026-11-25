package io.roadmap.tasktracker.scheduler.kafka;

import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

@Configuration
public class KafkaConfig {

    @Bean
    public KafkaSender<Integer, Object> kafkaSender(KafkaProperties kafkaProperties) {
        SenderOptions<Integer, Object> senderOptions =
                SenderOptions.create(kafkaProperties.buildProducerProperties());

        return KafkaSender.create(senderOptions);
    }

    @Bean
    public ReceiverOptions<String, Object> receiverOptions(KafkaProperties kafkaProperties) {
        ReceiverOptions<String, Object> receiverOptions =
                ReceiverOptions.create(kafkaProperties.buildConsumerProperties());

        return receiverOptions;
    }
}
